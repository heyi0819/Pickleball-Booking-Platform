package com.pickleball.booking.coach.infrastructure;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface CoachProfileRepository extends JpaRepository<CoachProfileEntity, UUID> { Optional<CoachProfileEntity> findByOrganizationIdAndUserId(UUID organizationId, UUID userId); }
