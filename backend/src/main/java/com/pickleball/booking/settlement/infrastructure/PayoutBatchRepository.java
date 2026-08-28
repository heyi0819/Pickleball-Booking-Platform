package com.pickleball.booking.settlement.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PayoutBatchRepository extends JpaRepository<PayoutBatchEntity, UUID> {
    List<PayoutBatchEntity> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from PayoutBatchEntity b where b.id = :id")
    Optional<PayoutBatchEntity> findLockedById(UUID id);
}
