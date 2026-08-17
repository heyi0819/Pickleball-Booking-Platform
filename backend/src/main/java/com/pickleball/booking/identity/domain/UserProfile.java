package com.pickleball.booking.identity.domain;

public record UserProfile(String displayName, String phone, String email, String locale) {
    public UserProfile {
        if (displayName == null || displayName.isBlank() || displayName.length() > 100) throw new IllegalArgumentException("displayName is required");
        if (phone != null && phone.length() > 30) throw new IllegalArgumentException("phone is too long");
        if (email != null && (email.length() > 254 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) throw new IllegalArgumentException("email is invalid");
        if (locale == null || locale.isBlank() || locale.length() > 10) throw new IllegalArgumentException("locale is invalid");
    }

    public boolean isComplete() { return phone != null && !phone.isBlank() || email != null && !email.isBlank(); }
}
