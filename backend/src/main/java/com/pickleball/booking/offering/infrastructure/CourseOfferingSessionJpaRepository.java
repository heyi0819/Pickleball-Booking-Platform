package com.pickleball.booking.offering.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseOfferingSessionJpaRepository extends JpaRepository<CourseOfferingSessionEntity,UUID> {
    List<CourseOfferingSessionEntity> findByCourseOfferingIdOrderBySequenceNoAsc(UUID courseOfferingId);
    void deleteByCourseOfferingId(UUID courseOfferingId);
}
