package com.pickleball.booking.coach.infrastructure;
import java.util.*; import org.springframework.data.jpa.repository.*; import jakarta.persistence.LockModeType;
public interface CoachApplicationRepository extends JpaRepository<CoachApplicationEntity, UUID> {
    List<CoachApplicationEntity> findByOrganizationIdOrderBySubmittedAtDesc(UUID organizationId);
    List<CoachApplicationEntity> findByCoachProfileIdOrderBySubmittedAtDesc(UUID profileId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from CoachApplicationEntity c where c.id=:id") Optional<CoachApplicationEntity> findLockedById(UUID id);
}
