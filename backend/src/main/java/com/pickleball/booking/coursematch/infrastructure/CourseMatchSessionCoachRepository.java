package com.pickleball.booking.coursematch.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseMatchSessionCoachRepository extends JpaRepository<CourseMatchSessionCoachEntity, UUID> {
    List<CourseMatchSessionCoachEntity> findByCourseMatchSessionIdInOrderByCourseMatchSessionIdAscAssignmentOrderAsc(Collection<UUID> sessionIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourseMatchSessionCoachEntity c where c.id = :id")
    Optional<CourseMatchSessionCoachEntity> findLockedById(@Param("id") UUID id);
}
