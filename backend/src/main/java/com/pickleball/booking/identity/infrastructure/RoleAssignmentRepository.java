package com.pickleball.booking.identity.infrastructure;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RoleAssignmentRepository extends JpaRepository<RoleAssignmentEntity, UUID> {
    List<RoleAssignmentEntity> findByUserId(java.util.UUID userId);
    Optional<RoleAssignmentEntity> findByUserIdAndOrganizationIdAndRoleCode(UUID userId, UUID organizationId, com.pickleball.booking.identity.domain.RoleCode roleCode);
    boolean existsByUserIdAndOrganizationIdAndRoleCodeAndStatus(UUID userId, UUID organizationId, com.pickleball.booking.identity.domain.RoleCode roleCode, com.pickleball.booking.identity.domain.RoleAssignmentStatus status);
}
