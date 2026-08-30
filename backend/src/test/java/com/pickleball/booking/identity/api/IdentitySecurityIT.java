package com.pickleball.booking.identity.api;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.pickleball.booking.identity.domain.UserStatus;
import com.pickleball.booking.identity.infrastructure.PlatformUserRepository;
import com.pickleball.booking.identity.infrastructure.ExternalIdentityRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class IdentitySecurityIT {
    static final WireMockServer line = new WireMockServer(wireMockConfig().dynamicPort());
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");
    static { line.start(); }
    @Autowired PlatformUserRepository users;
    @Autowired ExternalIdentityRepository identities;
    @LocalServerPort int port;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl); registry.add("spring.datasource.username", postgres::getUsername); registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("security.jwt.signing-secret", () -> "test-only-signing-secret-with-at-least-thirty-two-characters");
        registry.add("line.login.channel-id", () -> "channel"); registry.add("line.login.verify-url", () -> line.baseUrl() + "/oauth2/v2.1/verify");
        registry.add("app.cors.allowed-origins", () -> "https://pickleball-stg-liff.pages.dev");
    }
    @BeforeEach void stubLine() { line.resetAll(); line.stubFor(post(urlEqualTo("/oauth2/v2.1/verify")).willReturn(okJson("{\"sub\":\"line-subject\",\"name\":\"Member\",\"iss\":\"https://access.line.me\",\"aud\":\"channel\",\"exp\":1900000000}"))); }
    @AfterAll static void stopLine() { line.stop(); }
    @Test void loginAndProtectedMeFollowSecurityBoundary() throws Exception {
        var body = request("POST", "/api/v1/auth/line/login", "{\"idToken\":\"valid\"}", null);
        assertThat(body.statusCode()).isEqualTo(200);
        var token = body.body().replaceAll(".*\\\"accessToken\\\":\\\"([^\\\"]+)\\\".*", "$1");
        assertThat(request("GET", "/api/v1/me", null, null).statusCode()).isEqualTo(401);
        assertThat(request("GET", "/api/v1/me", null, "malformed").statusCode()).isEqualTo(401);
        assertThat(request("GET", "/api/v1/me", null, token).statusCode()).isEqualTo(200);
        var userId = identities.findByProviderAndProviderSubjectAndRevokedAtIsNull("LINE", "line-subject").orElseThrow().getUser().getId();
        var user = users.findById(userId).orElseThrow(); user.changeStatus(UserStatus.SUSPENDED); users.saveAndFlush(user);
        assertThat(request("GET", "/api/v1/me", null, token).statusCode()).isEqualTo(401);
    }
    @Test void invalidOrExpiredLineCredentialIsUnauthorized() throws Exception {
        line.resetAll(); line.stubFor(post(urlEqualTo("/oauth2/v2.1/verify")).willReturn(aResponse().withStatus(400)));
        assertThat(request("POST", "/api/v1/auth/line/login", "{\"idToken\":\"invalid\"}", null).statusCode()).isEqualTo(401);
    }
    @Test void configuredStagingOriginCanPreflightButStillRequiresPlatformJwt() throws Exception {
        var preflight = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "https://pickleball-stg-liff.pages.dev")
                .header("Access-Control-Request-Method", "GET").build();
        var response = HttpClient.newHttpClient().send(preflight, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("access-control-allow-origin")).contains("https://pickleball-stg-liff.pages.dev");
        var protectedRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me"))
                .header("Origin", "https://pickleball-stg-liff.pages.dev").GET().build();
        var protectedResponse = HttpClient.newHttpClient().send(protectedRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(protectedResponse.statusCode()).isEqualTo(401);
        assertThat(protectedResponse.headers().firstValue("access-control-allow-origin")).contains("https://pickleball-stg-liff.pages.dev");
        assertThat(protectedResponse.headers().firstValue("access-control-allow-credentials")).isEmpty();
    }
    @Test void unknownOriginIsDeniedWithoutWildcardCorsRegression() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "https://unknown.example")
                .header("Access-Control-Request-Method", "GET").build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("access-control-allow-origin")).isEmpty();
    }
    @Test void concurrentFirstLoginsCreateOneIdentityAndOneDefaultRole() throws Exception {
        line.resetAll(); line.stubFor(post(urlEqualTo("/oauth2/v2.1/verify")).willReturn(okJson("{\"sub\":\"concurrent-line-subject\",\"name\":\"Concurrent member\",\"iss\":\"https://access.line.me\",\"aud\":\"channel\",\"exp\":1900000000}")));
        var identitiesBefore = identities.count(); var usersBefore = users.count();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first = pool.submit(() -> { start.await(); return request("POST", "/api/v1/auth/line/login", "{\"idToken\":\"same\"}", null); });
            var second = pool.submit(() -> { start.await(); return request("POST", "/api/v1/auth/line/login", "{\"idToken\":\"same\"}", null); });
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            assertThat(second.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }
        assertThat(identities.count()).isEqualTo(identitiesBefore + 1);
        assertThat(users.count()).isEqualTo(usersBefore + 1);
        assertThat(identities.findByProviderAndProviderSubjectAndRevokedAtIsNull("LINE", "concurrent-line-subject").orElseThrow().getUser().getId()).isNotNull();
    }
    private HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Accept", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body)); else builder.method(method, HttpRequest.BodyPublishers.noBody());
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
