package com.pickleball.booking.identity.infrastructure;

import com.pickleball.booking.identity.application.LineAuthorizationCodeExchanger;
import com.pickleball.booking.identity.application.LineCredentialInvalidException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class LineHttpAuthorizationCodeExchanger implements LineAuthorizationCodeExchanger {
    private final RestClient client; private final String channelId; private final String channelSecret; private final String redirectUri;
    public LineHttpAuthorizationCodeExchanger(@Value("${line.login.channel-id:}") String channelId, @Value("${line.login.channel-secret:}") String channelSecret, @Value("${line.login.admin-redirect-uri:}") String redirectUri, @Value("${line.login.token-url:https://api.line.me/oauth2/v2.1/token}") String tokenUrl, @Value("${line.login.timeout-millis:3000}") int timeout) {
        this.channelId = channelId; this.channelSecret = channelSecret; this.redirectUri = redirectUri; var factory = new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(timeout); factory.setReadTimeout(timeout); this.client = RestClient.builder().baseUrl(tokenUrl).requestFactory(factory).build();
    }
    @Override public String exchange(String code, String verifier) {
        if (code == null || verifier == null || channelId.isBlank() || channelSecret.isBlank() || redirectUri.isBlank()) throw new LineCredentialInvalidException("LINE web login is unavailable");
        try {
            var form = "grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(redirectUri) + "&client_id=" + enc(channelId) + "&client_secret=" + enc(channelSecret) + "&code_verifier=" + enc(verifier);
            @SuppressWarnings("unchecked") var body = client.post().contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
            var idToken = body == null ? null : body.get("id_token"); if (idToken == null || String.valueOf(idToken).isBlank()) throw new LineCredentialInvalidException("Invalid LINE authorization response"); return String.valueOf(idToken);
        } catch (RestClientException exception) { throw new LineCredentialInvalidException("LINE authorization failed"); }
    }
    private String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
