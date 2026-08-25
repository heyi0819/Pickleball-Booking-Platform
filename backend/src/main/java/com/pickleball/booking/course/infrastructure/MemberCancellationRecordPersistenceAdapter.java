package com.pickleball.booking.course.infrastructure;

import com.pickleball.booking.course.domain.MemberCancellationRecord;
import com.pickleball.booking.course.domain.MemberCancellationRecordRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MemberCancellationRecordPersistenceAdapter implements MemberCancellationRecordRepository {
    private final JdbcTemplate jdbc;

    public MemberCancellationRecordPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MemberCancellationRecord save(MemberCancellationRecord record) {
        jdbc.update("""
                insert into member_cancellation_records(
                    id, organization_id, member_id, enrollment_id, course_session_id,
                    reason, cancelled_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                record.id(), record.organizationId(), record.memberId(), record.enrollmentId(),
                record.courseSessionId(), record.reason(), record.cancelledAt());
        return record;
    }
}
