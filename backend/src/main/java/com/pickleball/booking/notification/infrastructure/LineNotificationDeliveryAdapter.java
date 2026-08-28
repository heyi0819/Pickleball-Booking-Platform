package com.pickleball.booking.notification.infrastructure;

import com.pickleball.booking.notification.application.NotificationDeliveryException;
import com.pickleball.booking.notification.application.NotificationDeliveryPort;
import com.pickleball.booking.notification.application.NotificationTemplateRenderer;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class LineNotificationDeliveryAdapter implements NotificationDeliveryPort {
    private final LineRecipientResolver recipients;
    private final NotificationTemplateRenderer templates;
    private final String channelAccessToken;
    private final RestClient client;

    public LineNotificationDeliveryAdapter(
            LineRecipientResolver recipients,
            NotificationTemplateRenderer templates,
            @Value("${line.messaging.channel-access-token:}") String channelAccessToken,
            @Value("${line.messaging.push-url:https://api.line.me/v2/bot/message/push}") String pushUrl,
            @Value("${line.messaging.timeout-millis:3000}") int timeoutMillis) {
        this.recipients = recipients;
        this.templates = templates;
        this.channelAccessToken = channelAccessToken;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        this.client = RestClient.builder().baseUrl(pushUrl).requestFactory(factory).build();
    }

    @Override
    public void deliver(NotificationMessage message) {
        if (!"LINE".equals(message.channel())) {
            throw NotificationDeliveryException.permanent(
                    "NOTIFICATION_CHANNEL_UNSUPPORTED",
                    "LINE adapter cannot deliver channel " + message.channel());
        }
        if (channelAccessToken == null || channelAccessToken.isBlank()) {
            throw NotificationDeliveryException.permanent(
                    "LINE_CHANNEL_ACCESS_TOKEN_MISSING",
                    "LINE Messaging channel access token is not configured");
        }

        String recipient = recipients.resolve(message);
        String text = templates.render(message.templateCode(), message.payload());
        Map<String, Object> body = Map.of(
                "to", recipient,
                "messages", List.of(Map.of("type", "text", "text", text)));

        try {
            client.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + channelAccessToken)
                    .header("X-Line-Retry-Key", message.id().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict duplicateRetryKey) {
            // LINE returns 409 when an accepted retry key is reused. Treat it as idempotent success.
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            boolean retryable = status == 408 || status == 429 || status >= 500;
            String code = retryable ? "LINE_HTTP_RETRYABLE" : "LINE_HTTP_PERMANENT";
            if (retryable) {
                throw NotificationDeliveryException.retryable(
                        code, "LINE push returned retryable HTTP " + status, ex);
            }
            throw NotificationDeliveryException.permanent(
                    code, "LINE push returned non-retryable HTTP " + status);
        } catch (ResourceAccessException ex) {
            throw NotificationDeliveryException.retryable(
                    "LINE_HTTP_TIMEOUT",
                    "LINE push timed out or could not connect", ex);
        } catch (RestClientException ex) {
            throw NotificationDeliveryException.retryable(
                    "LINE_HTTP_UNAVAILABLE",
                    "LINE push request failed", ex);
        }
    }
}
