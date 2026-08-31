package com.pickleball.booking.identity.application;

import com.pickleball.booking.identity.domain.*;
import com.pickleball.booking.identity.infrastructure.*;
import com.pickleball.booking.organization.domain.OrganizationStatus;
import jakarta.transaction.Transactional;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class IdentityService {
    private final LineCredentialVerifier lineVerifier; private final FirstLoginProvisioningPolicy provisioning; private final ExternalIdentityRepository identities; private final PlatformUserRepository users; private final RoleAssignmentRepository roles; private final PlatformTokenService tokens; private final OrganizationAccessPolicy organizationAccess;
    public IdentityService(LineCredentialVerifier lineVerifier, FirstLoginProvisioningPolicy provisioning, ExternalIdentityRepository identities, PlatformUserRepository users, RoleAssignmentRepository roles, PlatformTokenService tokens, OrganizationAccessPolicy organizationAccess) { this.lineVerifier = lineVerifier; this.provisioning = provisioning; this.identities = identities; this.users = users; this.roles = roles; this.tokens = tokens; this.organizationAccess = organizationAccess; }
    @Transactional
    public LoginResult login(String idToken) {
        var credential = lineVerifier.verify(idToken);
        var existing = identities.findByProviderAndProviderSubjectAndRevokedAtIsNull("LINE", credential.identity().subject());
        var user = existing.map(ExternalIdentityEntity::getUser).orElseGet(() -> provisionOrResolveRaceWinner(credential.identity()));
        if (user.getStatus() != UserStatus.ACTIVE) throw new AccessForbiddenException("User is not active");
        user.recordLogin(); existing.ifPresent(ExternalIdentityEntity::verifiedNow);
        var activeRoles = activeRoles(user.getId());
        var token = tokens.issue(user.getId());
        return new LoginResult(token.value(), token.expiresIn(), user.getId(), user.getDisplayName(), activeRoles.stream().map(RoleView::roleCode).distinct().toList());
    }
    private PlatformUserEntity provisionOrResolveRaceWinner(LineIdentity identity) {
        try { return provisioning.provision(identity); }
        catch (DataIntegrityViolationException race) {
            return identities.findByProviderAndProviderSubjectAndRevokedAtIsNull("LINE", identity.subject())
                    .map(ExternalIdentityEntity::getUser)
                    .orElseThrow(() -> race);
        }
    }
    @Transactional
    public MeView me(AuthenticatedPrincipal principal) { var user = requireActiveUser(principal.userId()); return toMe(user); }
    @Transactional
    public MeView updateProfile(AuthenticatedPrincipal principal, UserProfile profile) { var user = requireActiveUser(principal.userId()); user.updateProfile(profile.displayName().trim(), blankToNull(profile.email()), profile.locale()); return toMe(user); }
    @Transactional
    public List<RoleView> roles(AuthenticatedPrincipal principal) { requireActiveUser(principal.userId()); return activeRoles(principal.userId()); }
    @Transactional
    public boolean isAuthorizedForOrganization(AuthenticatedPrincipal principal, RoleCode requiredRole, UUID organizationId) {
        requireActiveUser(principal.userId());
        var assignments = roles.findByUserId(principal.userId()).stream()
                .filter(role -> role.getOrganization() == null || role.getOrganization().getStatus() == OrganizationStatus.ACTIVE)
                .map(role -> new RoleAssignment(principal.userId(), role.getRoleCode(), role.getStatus(), role.getOrganization() == null ? OrganizationScope.global() : new OrganizationScope(role.getOrganization().getId())))
                .toList();
        return organizationAccess.permits(assignments, requiredRole, organizationId);
    }
    public PlatformUserEntity requireActiveUser(UUID userId) { var user = users.findById(userId).orElseThrow(() -> new AccessForbiddenException("User not found")); if (user.getStatus() != UserStatus.ACTIVE) throw new AccessForbiddenException("User is not active"); return user; }
    private MeView toMe(PlatformUserEntity user) { var profile = new UserProfile(user.getDisplayName(), user.getEmail(), user.getLocale()); return new MeView(user.getId(), user.getDisplayName(), user.getEmail(), user.getLocale(), profile.isComplete(), activeRoles(user.getId())); }
    private List<RoleView> activeRoles(UUID userId) { return roles.findByUserId(userId).stream().filter(r -> r.getStatus() == RoleAssignmentStatus.ACTIVE).filter(r -> r.getOrganization() == null || r.getOrganization().getStatus() == OrganizationStatus.ACTIVE).map(r -> new RoleView(r.getRoleCode(), r.getOrganization() == null ? null : r.getOrganization().getId(), r.getOrganization() == null ? null : r.getOrganization().getCode(), r.getOrganization() == null ? null : r.getOrganization().getName())).toList(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public record LoginResult(String accessToken, long expiresIn, UUID userId, String displayName, List<RoleCode> roles) {}
    public record MeView(UUID id, String displayName, String email, String locale, boolean profileComplete, List<RoleView> roles) {}
    public record RoleView(RoleCode roleCode, UUID organizationId, String organizationCode, String organizationName) {}
}
