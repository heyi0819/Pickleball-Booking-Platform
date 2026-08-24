package com.pickleball.booking.coursematch.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CourseMatchRepository extends JpaRepository<CourseMatchEntity, UUID> {
    List<CourseMatchEntity> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CourseMatchEntity m where m.id = :id")
    Optional<CourseMatchEntity> findLockedById(UUID id);
}
