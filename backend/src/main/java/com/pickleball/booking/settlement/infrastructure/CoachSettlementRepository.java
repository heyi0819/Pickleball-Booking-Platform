package com.pickleball.booking.settlement.infrastructure;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoachSettlementRepository extends JpaRepository<CoachSettlementEntity, UUID> {
    List<CoachSettlementEntity> findBySessionSettlementId(UUID sessionSettlementId);
    List<CoachSettlementEntity> findByCoachProfileIdOrderByCreatedAtDesc(UUID coachProfileId);

    @Query(value = """
            select cs.id as "coachSettlementId",
                   cs.organization_id as "organizationId",
                   ss.course_session_id as "courseSessionId",
                   cs.payable_amount as "payableAmount",
                   cs.paid_amount as "paidAmount",
                   cs.payout_status as "payoutStatus"
              from coach_settlements cs
              join session_settlements ss on ss.id = cs.session_settlement_id
              join coach_profiles cp on cp.id = cs.coach_profile_id
             where cp.user_id = :userId
               and cp.deleted_at is null
             order by cs.created_at desc, cs.id desc
            """, nativeQuery = true)
    List<MyCoachSettlementRow> findOwnedByUserId(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CoachSettlementEntity c where c.id = :id")
    Optional<CoachSettlementEntity> findLockedById(UUID id);

    interface MyCoachSettlementRow {
        UUID getCoachSettlementId();
        UUID getOrganizationId();
        UUID getCourseSessionId();
        BigDecimal getPayableAmount();
        BigDecimal getPaidAmount();
        String getPayoutStatus();
    }
}
