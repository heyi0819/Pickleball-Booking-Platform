package com.pickleball.booking.settlement.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementAdjustmentRepository extends JpaRepository<SettlementAdjustmentEntity, UUID> {
    List<SettlementAdjustmentEntity> findBySessionSettlementIdOrderByCreatedAtAsc(UUID sessionSettlementId);
}
