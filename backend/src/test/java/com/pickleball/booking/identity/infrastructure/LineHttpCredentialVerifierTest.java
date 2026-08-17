package com.pickleball.booking.identity.infrastructure;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.pickleball.booking.identity.application.LineCredentialInvalidException;
import org.junit.jupiter.api.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.*;

class LineHttpCredentialVerifierTest {
    private static final WireMockServer line = new WireMockServer(wireMockConfig().dynamicPort());
    private LineHttpCredentialVerifier verifier;
    @BeforeAll static void start() { line.start(); }
    @AfterAll static void stop() { line.stop(); }
    @BeforeEach void setUp() { line.resetAll(); verifier = new LineHttpCredentialVerifier("channel", line.baseUrl() + "/oauth2/v2.1/verify", 100); }
    @Test void acceptsValidCredential() { stubJson(200, valid(1_900_000_000L)); assertThat(verifier.verify("token").identity().subject()).isEqualTo("line-subject"); }
    @Test void rejectsExpiredCredential() { stubJson(200, valid(1L)); assertThatThrownBy(() -> verifier.verify("token")).isInstanceOf(LineCredentialInvalidException.class); }
    @Test void rejectsWrongAudience() { stubJson(200, valid(1_900_000_000L).replace("channel", "other")); assertThatThrownBy(() -> verifier.verify("token")).isInstanceOf(LineCredentialInvalidException.class); }
    @Test void mapsLine4xxAnd5xxToInvalidCredential() { stubJson(400, "{}"); assertThatThrownBy(() -> verifier.verify("token")).isInstanceOf(LineCredentialInvalidException.class); line.resetAll(); stubJson(500, "{}"); assertThatThrownBy(() -> verifier.verify("token")).isInstanceOf(LineCredentialInvalidException.class); }
    @Test void rejectsMalformedResponseAndTimeout() { stubJson(200, "{bad json"); assertThatThrownBy(() -> verifier.verify("token")).isInstanceOf(LineCredentialInvalidException.class); line.resetAll(); line.stubFor(post(urlEqualTo("/oauth2/v2.1/verify")).willReturn(aResponse().withFixedDelay(300).withStatus(200).withBody(valid(1_900_000_000L)))); assertThatThrownBy(() -> verifier.verify("token")).isInstanceOf(LineCredentialInvalidException.class); }
    private void stubJson(int status, String body) { line.stubFor(post(urlEqualTo("/oauth2/v2.1/verify")).withRequestBody(containing("id_token=token")).withRequestBody(containing("client_id=channel")).willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body))); }
    private String valid(long exp) { return "{\"sub\":\"line-subject\",\"name\":\"Member\",\"email\":\"member@example.test\",\"iss\":\"https://access.line.me\",\"aud\":\"channel\",\"exp\":" + exp + "}"; }
}
