package com.pickleball.booking.identity.application;

import com.pickleball.booking.identity.domain.LineIdentity;

public interface LineCredentialVerifier {
    VerifiedLineCredential verify(String idToken);
    VerifiedLineCredential verify(String idToken, String nonce);
    record VerifiedLineCredential(LineIdentity identity, String issuer, String audience, long expiresAtEpochSeconds) {}
}
