package com.pickleball.booking.identity.application;
import java.util.UUID;
public interface PlatformTokenService { IssuedToken issue(UUID userId); UUID verifyAndGetUserId(String token); record IssuedToken(String value, long expiresIn) {} }
