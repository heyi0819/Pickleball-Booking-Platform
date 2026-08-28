package com.pickleball.booking.settlement.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutBatchItemRepository extends JpaRepository<PayoutBatchItemEntity, UUID> {
    List<PayoutBatchItemEntity> findByPayoutBatchIdOrderByCreatedAtAsc(UUID payoutBatchId);
    List<PayoutBatchItemEntity> findByCoachSettlementId(UUID coachSettlementId);
}
