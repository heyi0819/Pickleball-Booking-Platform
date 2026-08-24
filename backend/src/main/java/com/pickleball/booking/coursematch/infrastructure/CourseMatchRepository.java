package com.pickleball.booking.coursematch.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CourseMatchRepository extends JpaRepository<CourseMatchEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CourseMatchEntity m where m.id = :id")
    Optional<CourseMatchEntity> findLockedById(UUID id);
}
