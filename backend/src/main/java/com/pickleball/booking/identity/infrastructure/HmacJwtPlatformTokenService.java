package com.pickleball.booking.identity.infrastructure;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.pickleball.booking.identity.application.PlatformTokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Small, purpose-built HS256 issuer. It contains only user identity and timestamps. */
@Component
public class HmacJwtPlatformTokenService implements PlatformTokenService {
    private static final long EXPIRES_IN = 1800;
    private final byte[] secret; private final ObjectMapper json;
    public HmacJwtPlatformTokenService(@Value("${security.jwt.signing-secret}") String secret, ObjectMapper json) { if (secret == null || secret.length() < 32) throw new IllegalStateException("JWT signing secret must be at least 32 characters"); this.secret = secret.getBytes(StandardCharsets.UTF_8); this.json = json; }
    @Override public IssuedToken issue(UUID userId) {
        var now = Instant.now().getEpochSecond();
        try { return new IssuedToken(encode(Map.of("alg", "HS256", "typ", "JWT"), Map.of("sub", userId.toString(), "iat", now, "exp", now + EXPIRES_IN)), EXPIRES_IN); }
        catch (Exception exception) { throw new IllegalStateException("Cannot issue platform token", exception); }
    }
    @Override public UUID verifyAndGetUserId(String token) {
        try {
            var parts = token.split("\\."); if (parts.length != 3) throw new IllegalArgumentException();
            var expected = sign(parts[0] + "." + parts[1]); if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), parts[2].getBytes(StandardCharsets.US_ASCII))) throw new IllegalArgumentException();
            var claims = json.readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<Map<String, Object>>() {});
            var exp = ((Number) claims.get("exp")).longValue(); if (Instant.now().getEpochSecond() >= exp) throw new IllegalArgumentException();
            return UUID.fromString(String.valueOf(claims.get("sub")));
        } catch (Exception exception) { throw new IllegalArgumentException("Invalid platform token"); }
    }
    private String encode(Map<String, Object> header, Map<String, Object> claims) throws Exception { var unsigned = b64(json.writeValueAsBytes(header)) + "." + b64(json.writeValueAsBytes(claims)); return unsigned + "." + sign(unsigned); }
    private String sign(String input) throws Exception { var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256")); return b64(mac.doFinal(input.getBytes(StandardCharsets.US_ASCII))); }
    private String b64(byte[] input) { return Base64.getUrlEncoder().withoutPadding().encodeToString(input); }
}
