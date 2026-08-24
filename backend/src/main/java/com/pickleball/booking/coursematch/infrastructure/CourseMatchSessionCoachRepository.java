package com.pickleball.booking.coursematch.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseMatchSessionCoachRepository extends JpaRepository<CourseMatchSessionCoachEntity, UUID> {
    List<CourseMatchSessionCoachEntity> findByCourseMatchSessionIdInOrderByCourseMatchSessionIdAscAssignmentOrderAsc(Collection<UUID> sessionIds);
}
