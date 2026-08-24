package com.pickleball.booking.offering.infrastructure;

import com.pickleball.booking.offering.domain.OfferingRegistrationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OfferingRegistrationJpaRepository extends JpaRepository<OfferingRegistrationEntity,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OfferingRegistrationEntity r where r.id=:id")
    Optional<OfferingRegistrationEntity> findLockedById(UUID id);

    boolean existsByCourseOfferingIdAndUserIdAndStatus(UUID offeringId, UUID userId, OfferingRegistrationStatus status);
    long countByCourseOfferingIdAndStatus(UUID offeringId, OfferingRegistrationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OfferingRegistrationEntity r where r.courseOfferingId=:offeringId and r.status=:status order by r.registeredAt, r.id")
    List<OfferingRegistrationEntity> findLockedByOfferingIdAndStatus(UUID offeringId, OfferingRegistrationStatus status);
}
