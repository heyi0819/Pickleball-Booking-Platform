package com.pickleball.booking.coursematch.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseMatchSessionRepository extends JpaRepository<CourseMatchSessionEntity, UUID> {
    List<CourseMatchSessionEntity> findByCourseMatchIdOrderBySessionIndexAsc(UUID courseMatchId);
}
