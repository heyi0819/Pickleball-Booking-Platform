package com.pickleball.booking.identity.application;

import com.pickleball.booking.identity.domain.LineIdentity;

public interface LineCredentialVerifier {
    VerifiedLineCredential verify(String idToken);
    record VerifiedLineCredential(LineIdentity identity, String issuer, String audience, long expiresAtEpochSeconds) {}
}
