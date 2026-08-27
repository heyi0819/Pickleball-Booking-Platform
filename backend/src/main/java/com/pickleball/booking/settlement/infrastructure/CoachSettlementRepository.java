package com.pickleball.booking.settlement.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CoachSettlementRepository extends JpaRepository<CoachSettlementEntity, UUID> {
    List<CoachSettlementEntity> findBySessionSettlementId(UUID sessionSettlementId);
    List<CoachSettlementEntity> findByCoachProfileIdOrderByCreatedAtDesc(UUID coachProfileId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CoachSettlementEntity c where c.id = :id")
    Optional<CoachSettlementEntity> findLockedById(UUID id);
}
