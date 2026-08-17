package com.pickleball.booking.identity.application;

import com.pickleball.booking.identity.domain.LineIdentity;
import com.pickleball.booking.identity.domain.RoleCode;
import com.pickleball.booking.identity.infrastructure.*;
import com.pickleball.booking.organization.infrastructure.*;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** The only place where the Slice 1 default-STUDENT provisioning assumption is encoded. */
@Service
public class FirstLoginProvisioningPolicy {
    private final PlatformUserRepository users; private final ExternalIdentityRepository identities; private final OrganizationRepository organizations; private final RoleAssignmentRepository roles;
    private final String defaultOrganizationCode; private final String defaultOrganizationName;
    public FirstLoginProvisioningPolicy(PlatformUserRepository users, ExternalIdentityRepository identities, OrganizationRepository organizations, RoleAssignmentRepository roles,
                                       @Value("${identity.default-organization-code:MVP-DEFAULT}") String defaultOrganizationCode,
                                       @Value("${identity.default-organization-name:Pickleball MVP}") String defaultOrganizationName) {
        this.users = users; this.identities = identities; this.organizations = organizations; this.roles = roles; this.defaultOrganizationCode = defaultOrganizationCode; this.defaultOrganizationName = defaultOrganizationName;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PlatformUserEntity provision(LineIdentity identity) {
        var user = users.save(new PlatformUserEntity(java.util.UUID.randomUUID(), safeName(identity.displayName())));
        identities.saveAndFlush(new ExternalIdentityEntity(user, identity.subject(), Map.of("name", safeName(identity.displayName()), "picture", nullToEmpty(identity.pictureUrl()), "email", nullToEmpty(identity.email()))));
        var organization = organizations.findByCode(defaultOrganizationCode).orElseGet(() -> organizations.saveAndFlush(new OrganizationEntity(defaultOrganizationCode, defaultOrganizationName)));
        roles.saveAndFlush(new RoleAssignmentEntity(user, organization, RoleCode.STUDENT));
        return user;
    }
    private String safeName(String value) { return value == null || value.isBlank() ? "LINE User" : value.substring(0, Math.min(100, value.length())); }
    private String nullToEmpty(String value) { return value == null ? "" : value; }
}
