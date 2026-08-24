package com.pickleball.booking.offering.infrastructure;

import com.pickleball.booking.offering.domain.OfferingPriceSnapshotStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CourseOfferingPriceSnapshotJpaRepository extends JpaRepository<CourseOfferingPriceSnapshotEntity,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CourseOfferingPriceSnapshotEntity p where p.id=:id")
    Optional<CourseOfferingPriceSnapshotEntity> findLockedById(UUID id);

    Optional<CourseOfferingPriceSnapshotEntity> findByCourseOfferingIdAndStatus(UUID offeringId, OfferingPriceSnapshotStatus status);
    Optional<CourseOfferingPriceSnapshotEntity> findTopByCourseOfferingIdOrderByVersionNoDesc(UUID offeringId);
}
