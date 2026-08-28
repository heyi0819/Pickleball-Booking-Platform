package com.pickleball.booking.settlement.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SessionSettlementRepository extends JpaRepository<SessionSettlementEntity, UUID> {
    Optional<SessionSettlementEntity> findByCourseSessionId(UUID courseSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SessionSettlementEntity s where s.id = :id")
    Optional<SessionSettlementEntity> findLockedById(UUID id);
}
