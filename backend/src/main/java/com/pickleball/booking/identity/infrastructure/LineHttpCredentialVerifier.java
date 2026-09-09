package com.pickleball.booking.identity.infrastructure;

import com.pickleball.booking.identity.application.*;
import com.pickleball.booking.identity.domain.LineIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Component
public class LineHttpCredentialVerifier implements LineCredentialVerifier {
    private static final Logger log = LoggerFactory.getLogger(LineHttpCredentialVerifier.class);
    private final RestClient client; private final String channelId;
    public LineHttpCredentialVerifier(@Value("${line.login.channel-id:}") String channelId, @Value("${line.login.verify-url:https://api.line.me/oauth2/v2.1/verify}") String verifyUrl, @Value("${line.login.timeout-millis:10000}") int timeoutMillis) {
        this.channelId = channelId;
        var factory = new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(timeoutMillis); factory.setReadTimeout(timeoutMillis);
        this.client = RestClient.builder().baseUrl(verifyUrl).requestFactory(factory).build();
    }
    @Override public VerifiedLineCredential verify(String idToken) { return verify(idToken, null); }
    @Override public VerifiedLineCredential verify(String idToken, String nonce) {
        if (idToken == null || idToken.isBlank() || channelId.isBlank()) throw new LineCredentialInvalidException("LINE credential cannot be verified");
        try {
            var form = "id_token=" + java.net.URLEncoder.encode(idToken, java.nio.charset.StandardCharsets.UTF_8) + "&client_id=" + java.net.URLEncoder.encode(channelId, java.nio.charset.StandardCharsets.UTF_8);
            if (nonce != null && !nonce.isBlank()) form += "&nonce=" + java.net.URLEncoder.encode(nonce, java.nio.charset.StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked") var body = client.post().contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
            var exp = Long.parseLong(String.valueOf(body == null ? 0 : body.getOrDefault("exp", 0)));
            if (body == null || body.get("sub") == null || exp <= Instant.now().getEpochSecond() || !channelId.equals(String.valueOf(body.get("aud"))) || !"https://access.line.me".equals(body.get("iss")) || (nonce != null && !nonce.equals(String.valueOf(body.get("nonce"))))) throw new LineCredentialInvalidException("Invalid LINE credential");
            return new VerifiedLineCredential(new LineIdentity(String.valueOf(body.get("sub")), string(body, "name"), string(body, "email"), string(body, "picture")), string(body, "iss"), string(body, "aud"), exp);
        } catch (RestClientResponseException exception) {
            log.warn("LINE credential verification was rejected with HTTP status {} ({})", exception.getStatusCode(), lineErrorCategory(exception.getResponseBodyAsString()));
            throw new LineCredentialInvalidException("Invalid LINE credential");
        } catch (ResourceAccessException exception) {
            log.warn("LINE credential verification was unavailable ({})", exception.getClass().getSimpleName());
            throw new LineCredentialInvalidException("Unavailable LINE credential verification");
        } catch (RestClientException | NumberFormatException exception) { throw new LineCredentialInvalidException("Invalid or unavailable LINE credential"); }
    }
    static String lineErrorCategory(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return "empty-response";
        var normalized = responseBody.toLowerCase(Locale.ROOT);
        if (normalized.contains("id_token") || normalized.contains("id token") || normalized.contains("idtoken")) return "id-token";
        if (normalized.contains("client_id") || normalized.contains("client id") || normalized.contains("clientid")) return "client-id";
        if (normalized.contains("nonce")) return "nonce";
        var errorCode = java.util.regex.Pattern.compile("\\\"error\\\"\\s*:\\s*\\\"([a-z0-9_-]{1,64})\\\"").matcher(normalized);
        if (errorCode.find()) return "error-code-" + errorCode.group(1);
        return "other-response";
    }
    private String string(Map<?, ?> body, String key) { var value = body.get(key); return value == null ? null : String.valueOf(value); }
}
