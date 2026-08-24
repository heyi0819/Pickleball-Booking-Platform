package com.pickleball.booking.offering.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CourseOfferingJpaRepository extends JpaRepository<CourseOfferingEntity,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CourseOfferingEntity o where o.id=:id")
    Optional<CourseOfferingEntity> findLockedById(UUID id);
}
