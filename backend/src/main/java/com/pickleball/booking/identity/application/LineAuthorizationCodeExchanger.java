package com.pickleball.booking.identity.application;

public interface LineAuthorizationCodeExchanger {
    String exchange(String authorizationCode, String codeVerifier);
}
