package com.pickleball.booking.identity.application;

import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Explicit operator command; no HTTP endpoint and inactive unless the pilot-bootstrap profile is selected. */
@Component
@Profile("pilot-bootstrap")
public class PilotBootstrapCommand implements ApplicationRunner {
    private final PilotBootstrapService service;
    private final Environment environment;
    public PilotBootstrapCommand(PilotBootstrapService service, Environment environment) { this.service = service; this.environment = environment; }
    @Override public void run(ApplicationArguments arguments) {
        String confirmation = environment.getProperty("pilot.bootstrap.confirm");
        String userId = environment.getProperty("pilot.bootstrap.platform-admin-user-id");
        if (!"grant-initial-platform-admin".equals(confirmation) || userId == null || userId.isBlank()) {
            throw new IllegalStateException("pilot bootstrap requires an explicit confirmation and platform admin user id");
        }
        service.grantInitialPlatformAdmin(UUID.fromString(userId));
    }
}
