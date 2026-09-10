package com.pickleball.booking.identity.infrastructure;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.pickleball.booking.identity.application.LineCredentialInvalidException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.*;

class LineHttpAuthorizationCodeExchangerTest {
    private static final String CANONICAL = "https://pickleball-stg-admin.pages.dev/auth/line/callback";
    private static final String PREVIEW = "https://26cb48d9.pickleball-stg-admin.pages.dev/auth/line/callback";
    private static final WireMockServer line = new WireMockServer(wireMockConfig().dynamicPort());
    private LineHttpAuthorizationCodeExchanger exchanger;

    @BeforeAll static void start() { line.start(); }
    @AfterAll static void stop() { line.stop(); }
    @BeforeEach void setUp() {
        line.resetAll();
        line.resetRequests();
        configureFor("localhost", line.port());
        exchanger = new LineHttpAuthorizationCodeExchanger("channel", "secret", CANONICAL, CANONICAL + ", " + PREVIEW, line.baseUrl() + "/oauth2/v2.1/token", 3000);
    }

    @Test void exchangesUsingTheSameExactAllowedRedirectUri() {
        stubTokenExchange();
        assertThat(exchanger.exchange("authorization-code", "verifier", CANONICAL)).isEqualTo("id-token");
        assertThat(line.getAllServeEvents()).singleElement().satisfies(event ->
                assertThat(event.getRequest().getBodyAsString()).contains("redirect_uri=" + encoded(CANONICAL)));
    }

    @Test void acceptsTheExactTemporaryPreviewRedirectUri() {
        stubTokenExchange();
        assertThat(exchanger.exchange("authorization-code", "verifier", PREVIEW)).isEqualTo("id-token");
    }

    @Test void fallsBackToTheConfiguredCanonicalRedirectUriForTheOldCanonicalClient() {
        stubTokenExchange();
        assertThat(exchanger.exchange("authorization-code", "verifier", null)).isEqualTo("id-token");
        assertThat(line.getAllServeEvents()).singleElement().satisfies(event ->
                assertThat(event.getRequest().getBodyAsString()).contains("redirect_uri=" + encoded(CANONICAL)));
    }

    @Test void deniesArbitraryRedirectUrisBeforeCallingLine() {
        assertThatThrownBy(() -> exchanger.exchange("authorization-code", "verifier", "https://random.pickleball-stg-admin.pages.dev/auth/line/callback")).isInstanceOf(LineCredentialInvalidException.class);
        assertThatThrownBy(() -> exchanger.exchange("authorization-code", "verifier", "https://unknown.example/auth/line/callback")).isInstanceOf(LineCredentialInvalidException.class);
        verify(0, postRequestedFor(urlEqualTo("/oauth2/v2.1/token")));
    }

    private void stubTokenExchange() {
        line.stubFor(post(urlEqualTo("/oauth2/v2.1/token"))
                .willReturn(okJson("{\"id_token\":\"id-token\"}")));
    }
    private String encoded(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
