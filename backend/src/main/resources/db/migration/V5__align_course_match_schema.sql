-- Slice 3 / S3.2 pre-domain alignment.
-- V4 established the persistence foundation; V5 aligns the Match tables with
-- the canonical 04 Database Design names/semantics before Java domain/API code is added.
-- Forward-only: V4 is intentionally left unchanged.

ALTER TABLE course_matches
    RENAME COLUMN participant_count_snapshot TO participant_count;

ALTER TABLE course_matches
    ADD COLUMN created_by uuid NOT NULL,
    ADD CONSTRAINT fk_course_matches_created_by
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT;

ALTER TABLE course_match_sessions
    RENAME COLUMN sequence_no TO session_index;
ALTER TABLE course_match_sessions
    RENAME COLUMN start_at TO scheduled_start_at;
ALTER TABLE course_match_sessions
    RENAME COLUMN end_at TO scheduled_end_at;
ALTER TABLE course_match_sessions
    RENAME COLUMN venue_id TO venue_snapshot_id;
ALTER TABLE course_match_sessions
    RENAME COLUMN venue_name_snapshot TO venue_snapshot_name;
ALTER TABLE course_match_sessions
    RENAME COLUMN venue_address_snapshot TO venue_snapshot_address;

ALTER TABLE course_match_sessions
    ADD COLUMN venue_snapshot_type varchar(20) NOT NULL,
    ADD COLUMN venue_fingerprint varchar(64) NOT NULL,
    ADD CONSTRAINT ck_course_match_sessions_venue_snapshot_type
        CHECK (venue_snapshot_type IN ('VENUE', 'OTHER')),
    ADD CONSTRAINT ck_course_match_sessions_venue_snapshot_identity
        CHECK (
            (venue_snapshot_type = 'VENUE' AND venue_snapshot_id IS NOT NULL)
            OR (venue_snapshot_type = 'OTHER' AND venue_snapshot_id IS NULL)
        );

ALTER TABLE course_match_session_coaches
    DROP CONSTRAINT ck_course_match_session_coaches_role,
    DROP CONSTRAINT ck_course_match_session_coaches_status;

DROP INDEX uk_course_match_session_coaches_active_primary;

UPDATE course_match_session_coaches
SET status = 'CANCELLED'
WHERE status = 'REPLACED';

ALTER TABLE course_match_session_coaches
    ADD COLUMN assignment_order smallint NOT NULL DEFAULT 1,
    DROP COLUMN role_type,
    RENAME COLUMN invited_at TO invitation_sent_at;
ALTER TABLE course_match_session_coaches
    RENAME COLUMN response_reason TO response_note;

ALTER TABLE course_match_session_coaches
    ADD CONSTRAINT ck_course_match_session_coaches_assignment_order
        CHECK (assignment_order > 0),
    ADD CONSTRAINT ck_course_match_session_coaches_status
        CHECK (status IN ('INVITED', 'ACCEPTED', 'REJECTED', 'CANCELLED'));

CREATE UNIQUE INDEX uk_course_match_session_coaches_active_order
    ON course_match_session_coaches(course_match_session_id, assignment_order)
    WHERE status IN ('INVITED', 'ACCEPTED');
