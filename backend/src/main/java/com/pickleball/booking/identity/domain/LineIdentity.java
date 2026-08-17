package com.pickleball.booking.identity.domain;

public record LineIdentity(String subject, String displayName, String email, String pictureUrl) {
    public LineIdentity {
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("LINE subject is required");
    }
}
