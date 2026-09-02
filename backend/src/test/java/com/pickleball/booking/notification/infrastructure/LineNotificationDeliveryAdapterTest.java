package com.pickleball.booking.notification.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.pickleball.booking.notification.application.NotificationDeliveryException;
import com.pickleball.booking.notification.application.NotificationDeliveryPort.NotificationMessage;
import com.pickleball.booking.notification.application.NotificationTemplateRenderer;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LineNotificationDeliveryAdapterTest {
    private static final WireMockServer line = new WireMockServer(wireMockConfig().dynamicPort());
    private final LineRecipientResolver recipients = mock(LineRecipientResolver.class);
    private LineNotificationDeliveryAdapter adapter;

    @BeforeAll
    static void start() {
        line.start();
    }

    @AfterAll
    static void stop() {
        line.stop();
    }

    @BeforeEach
    void setUp() {
        line.resetAll();
        when(recipients.resolve(any())).thenReturn("U-line-recipient");
        adapter = new LineNotificationDeliveryAdapter(
                recipients, new NotificationTemplateRenderer(), "channel-token",
                line.baseUrl() + "/v2/bot/message/push", 1_000);
    }

    @Test
    void sendsPushWithBearerTokenAndRetryKey() {
        line.stubFor(post(urlEqualTo("/v2/bot/message/push")).willReturn(aResponse().withStatus(200)));
        NotificationMessage message = message();

        adapter.deliver(message);

        line.verify(postRequestedFor(urlEqualTo("/v2/bot/message/push"))
                .withHeader("Authorization", equalTo("Bearer channel-token"))
                .withHeader("X-Line-Retry-Key", equalTo(message.id().toString())));
    }

    @Test
    void treatsDuplicateRetryKeyAsSuccess() {
        line.stubFor(post(urlEqualTo("/v2/bot/message/push")).willReturn(aResponse().withStatus(409)));
        assertThatCode(() -> adapter.deliver(message())).doesNotThrowAnyException();
    }

    @Test
    void classifies4xxAsPermanentAnd5xxAsRetryable() {
        line.stubFor(post(urlEqualTo("/v2/bot/message/push")).willReturn(aResponse().withStatus(400)));
        assertThatThrownBy(() -> adapter.deliver(message()))
                .isInstanceOf(NotificationDeliveryException.class)
                .satisfies(ex -> assertThat(((NotificationDeliveryException) ex).retryable()).isFalse());

        line.resetAll();
        line.stubFor(post(urlEqualTo("/v2/bot/message/push")).willReturn(aResponse().withStatus(503)));
        assertThatThrownBy(() -> adapter.deliver(message()))
                .isInstanceOf(NotificationDeliveryException.class)
                .satisfies(ex -> assertThat(((NotificationDeliveryException) ex).retryable()).isTrue());
    }

    private NotificationMessage message() {
        return new NotificationMessage(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                "LINE", "COURSE_CONFIRMED", "Course", UUID.randomUUID(),
                Map.of("courseNo", "C-1", "firstSessionStart", "2026-08-30 10:00", "venueName", "A場"), 1);
    }
}
