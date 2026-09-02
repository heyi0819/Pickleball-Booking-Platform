package com.pickleball.booking.identity.infrastructure;

import com.pickleball.booking.identity.application.*;
import com.pickleball.booking.identity.domain.LineIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.Instant;
import java.util.Map;

@Component
public class LineHttpCredentialVerifier implements LineCredentialVerifier {
    private final RestClient client; private final String channelId;
    public LineHttpCredentialVerifier(@Value("${line.login.channel-id:}") String channelId, @Value("${line.login.verify-url:https://api.line.me/oauth2/v2.1/verify}") String verifyUrl, @Value("${line.login.timeout-millis:3000}") int timeoutMillis) {
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
        } catch (RestClientException | NumberFormatException exception) { throw new LineCredentialInvalidException("Invalid or unavailable LINE credential"); }
    }
    private String string(Map<?, ?> body, String key) { var value = body.get(key); return value == null ? null : String.valueOf(value); }
}
