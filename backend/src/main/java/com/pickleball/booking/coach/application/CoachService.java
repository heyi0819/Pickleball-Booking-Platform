package com.pickleball.booking.coach.application;

import com.pickleball.booking.coach.domain.*;
import com.pickleball.booking.coach.infrastructure.*;
import com.pickleball.booking.identity.application.*;
import com.pickleball.booking.identity.domain.*;
import com.pickleball.booking.identity.infrastructure.*;
import com.pickleball.booking.organization.infrastructure.*;
import com.pickleball.booking.shared.application.*;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CoachService {
    private final IdentityService identity; private final OrganizationScopeResolver scopes; private final CoachProfileRepository profiles; private final CoachApplicationRepository applications; private final AvailabilityProposalRepository availability; private final PlatformUserRepository users; private final OrganizationRepository organizations; private final RoleAssignmentRepository roles; private final AuditOutboxService audit;
    public CoachService(IdentityService identity, OrganizationScopeResolver scopes, CoachProfileRepository profiles, CoachApplicationRepository applications, AvailabilityProposalRepository availability, PlatformUserRepository users, OrganizationRepository organizations, RoleAssignmentRepository roles, AuditOutboxService audit) { this.identity=identity;this.scopes=scopes;this.profiles=profiles;this.applications=applications;this.availability=availability;this.users=users;this.organizations=organizations;this.roles=roles;this.audit=audit; }
    @Transactional public CoachApplicationEntity apply(AuthenticatedPrincipal actor, String note, String skillLevel, String bio) {
        identity.requireActiveUser(actor.userId()); UUID org=scopes.activeOrganizationFor(actor.userId(),RoleCode.STUDENT);
        var existing=profiles.findByOrganizationIdAndUserId(org,actor.userId());
        var profile=existing.orElseGet(() -> profiles.save(new CoachProfileEntity(org,actor.userId(),skillLevel,bio)));
        if(existing.isPresent()) { if(profile.getApprovalStatus()==CoachProfileApprovalStatus.APPROVED || profile.getApprovalStatus()==CoachProfileApprovalStatus.PENDING) throw new BusinessException("STATE_TRANSITION_INVALID","A coach application is already active"); profile.resubmit(); }
        var application=applications.save(new CoachApplicationEntity(org,profile.getId(),note)); audit.record(org,actor.userId(),"COACH_APPLICATION_SUBMITTED","CoachApplication",application.getId(),note); return application;
    }
    @Transactional public CoachApplicationEntity reviewApplication(AuthenticatedPrincipal actor, UUID id, boolean approve, String note) {
        var app=applications.findLockedById(id).orElseThrow(()->new BusinessException("RESOURCE_NOT_FOUND","Coach application was not found")); requireCommittee(actor,app.getOrganizationId()); var profile=profiles.findById(app.getCoachProfileId()).orElseThrow(()->new BusinessException("RESOURCE_NOT_FOUND","Coach profile was not found"));
        if(approve) { app.approve(actor.userId(),note); profile.approve(actor.userId()); ensureActiveCoachRole(profile,actor.userId()); audit.record(app.getOrganizationId(),actor.userId(),"COACH_APPLICATION_APPROVED","CoachApplication",app.getId(),note); audit.record(app.getOrganizationId(),actor.userId(),"COACH_ROLE_ACTIVATED","CoachProfile",profile.getId(),null); } else { app.reject(actor.userId(),note); profile.reject(); audit.record(app.getOrganizationId(),actor.userId(),"COACH_APPLICATION_REJECTED","CoachApplication",app.getId(),note); }
        return app;
    }
    @Transactional public AvailabilityProposalEntity createAvailability(AuthenticatedPrincipal actor, Instant start, Instant end, UUID venue) { var profile=requireApprovedCoach(actor); return availability.save(new AvailabilityProposalEntity(profile.getOrganizationId(),profile.getId(),start,end,venue)); }
    @Transactional public AvailabilityProposalEntity updateAvailability(AuthenticatedPrincipal actor, UUID id, Instant start, Instant end, UUID venue) { var proposal=ownProposal(actor,id);proposal.update(start,end,venue);return proposal; }
    @Transactional public AvailabilityProposalEntity submitAvailability(AuthenticatedPrincipal actor, UUID id) { var proposal=ownProposal(actor,id);proposal.submit();audit.record(proposal.getOrganizationId(),actor.userId(),"AVAILABILITY_SUBMITTED","AvailabilityProposal",proposal.getId(),null);return proposal; }
    @Transactional public AvailabilityProposalEntity reviewAvailability(AuthenticatedPrincipal actor, UUID id, boolean approve, String note) { var proposal=availability.findById(id).orElseThrow(()->new BusinessException("RESOURCE_NOT_FOUND","Availability proposal was not found"));requireCommittee(actor,proposal.getOrganizationId());proposal.review(approve,actor.userId(),note);audit.record(proposal.getOrganizationId(),actor.userId(),approve?"AVAILABILITY_APPROVED":"AVAILABILITY_REJECTED","AvailabilityProposal",proposal.getId(),note);return proposal; }
    @Transactional public AvailabilityProposalEntity closeAvailability(AuthenticatedPrincipal actor, UUID id) { var proposal=ownProposal(actor,id);proposal.close();return proposal; }
    @Transactional public List<CoachApplicationEntity> myApplications(AuthenticatedPrincipal actor) { identity.requireActiveUser(actor.userId()); UUID org=scopes.activeOrganizationFor(actor.userId(),RoleCode.STUDENT); return profiles.findByOrganizationIdAndUserId(org,actor.userId()).map(p->applications.findByCoachProfileIdOrderBySubmittedAtDesc(p.getId())).orElse(List.of()); }
    @Transactional public List<CoachApplicationEntity> applicationsForReview(AuthenticatedPrincipal actor, UUID org) { requireCommittee(actor,org);return applications.findByOrganizationIdOrderBySubmittedAtDesc(org); }
    @Transactional public List<AvailabilityProposalEntity> myAvailability(AuthenticatedPrincipal actor) { return availability.findByCoachProfileIdOrderByStartAtDesc(requireApprovedCoach(actor).getId()); }
    @Transactional public List<AvailabilityProposalEntity> approvedAvailability(AuthenticatedPrincipal actor) { identity.requireActiveUser(actor.userId()); UUID org=scopes.activeOrganizationFor(actor.userId(),RoleCode.STUDENT); return availability.findByOrganizationIdAndStatusAndStartAtAfterOrderByStartAtAsc(org,AvailabilityProposalStatus.APPROVED,Instant.now()); }
    @Transactional public List<AvailabilityProposalEntity> availabilityForReview(AuthenticatedPrincipal actor, UUID org) { requireCommittee(actor,org);return availability.findByOrganizationIdOrderByStartAtDesc(org); }
    private AvailabilityProposalEntity ownProposal(AuthenticatedPrincipal actor, UUID id) { var profile=requireApprovedCoach(actor);var proposal=availability.findById(id).orElseThrow(()->new BusinessException("RESOURCE_NOT_FOUND","Availability proposal was not found"));if(!proposal.getCoachProfileId().equals(profile.getId()))throw new BusinessException("ORG_SCOPE_DENIED","Availability proposal is outside your scope");return proposal; }
    private CoachProfileEntity requireApprovedCoach(AuthenticatedPrincipal actor) { identity.requireActiveUser(actor.userId()); UUID org=scopes.activeOrganizationFor(actor.userId(),RoleCode.COACH);if(!identity.isAuthorizedForOrganization(actor,RoleCode.COACH,org))throw new BusinessException("AUTH_FORBIDDEN","An active coach role is required");var p=profiles.findByOrganizationIdAndUserId(org,actor.userId()).orElseThrow(()->new BusinessException("COACH_NOT_APPROVED","Coach profile was not found"));if(p.getApprovalStatus()!=CoachProfileApprovalStatus.APPROVED)throw new BusinessException("COACH_NOT_APPROVED","An approved coach profile is required");return p; }
    private void requireCommittee(AuthenticatedPrincipal actor, UUID org) { identity.requireActiveUser(actor.userId());if(!identity.isAuthorizedForOrganization(actor,RoleCode.COMMITTEE,org))throw new BusinessException("AUTH_FORBIDDEN","Committee role is required for this organization"); }
    private void ensureActiveCoachRole(CoachProfileEntity profile, UUID grantedBy) { var current=roles.findByUserIdAndOrganizationIdAndRoleCode(profile.getUserId(),profile.getOrganizationId(),RoleCode.COACH);if(current.isPresent()) { current.get().changeStatus(RoleAssignmentStatus.ACTIVE); return; } roles.save(new RoleAssignmentEntity(users.getReferenceById(profile.getUserId()),organizations.getReferenceById(profile.getOrganizationId()),RoleCode.COACH)); }
}
