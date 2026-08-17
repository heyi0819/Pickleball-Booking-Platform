package com.pickleball.booking.identity.infrastructure;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RoleAssignmentRepository extends JpaRepository<RoleAssignmentEntity, UUID> { List<RoleAssignmentEntity> findByUserId(java.util.UUID userId); }
