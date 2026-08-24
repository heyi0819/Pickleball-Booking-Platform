-- Slice 3 / S3.2 pre-domain alignment.
-- V4 established the persistence foundation; V5 aligns the Match tables with
-- the canonical 04 Database Design names/semantics before Java domain/API code is added.
-- Forward-only: V4 is intentionally left unchanged.

ALTER TABLE course_matches
    RENAME COLUMN participant_count_snapshot TO participant_count;

ALTER TABLE course_matches
    ADD COLUMN created_by uuid;

-- V4 did not expose a CourseMatch write API, but forward migration must still
-- preserve any fixture/manual data already present. Prefer an explicit Match actor;
-- otherwise derive the actor from the approved lesson request that originated it.
UPDATE course_matches cm
SET created_by = COALESCE(cm.confirmed_by, cm.cancelled_by, lr.reviewed_by, lr.requester_user_id)
FROM lesson_requests lr
WHERE cm.lesson_request_id = lr.id
  AND cm.created_by IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM course_matches WHERE created_by IS NULL) THEN
        RAISE EXCEPTION 'V5 cannot infer course_matches.created_by for existing rows';
    END IF;
END $$;

ALTER TABLE course_matches
    ALTER COLUMN created_by SET NOT NULL,
    ADD CONSTRAINT fk_course_matches_created_by
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT;

ALTER TABLE course_match_sessions RENAME COLUMN sequence_no TO session_index;
ALTER TABLE course_match_sessions RENAME COLUMN start_at TO scheduled_start_at;
ALTER TABLE course_match_sessions RENAME COLUMN end_at TO scheduled_end_at;
ALTER TABLE course_match_sessions RENAME COLUMN venue_id TO venue_snapshot_id;
ALTER TABLE course_match_sessions RENAME COLUMN venue_name_snapshot TO venue_snapshot_name;
ALTER TABLE course_match_sessions RENAME COLUMN venue_address_snapshot TO venue_snapshot_address;

ALTER TABLE course_match_sessions
    ADD COLUMN venue_snapshot_type varchar(20),
    ADD COLUMN venue_fingerprint varchar(64),
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();

UPDATE course_match_sessions
SET venue_snapshot_type = CASE WHEN venue_snapshot_id IS NULL THEN 'OTHER' ELSE 'VENUE' END,
    venue_fingerprint = md5(concat_ws('|',
        CASE WHEN venue_snapshot_id IS NULL THEN 'OTHER' ELSE 'VENUE' END,
        coalesce(venue_snapshot_id::text, ''),
        coalesce(venue_snapshot_name, ''),
        coalesce(venue_snapshot_address, '')))
        || md5(concat_ws('|',
        CASE WHEN venue_snapshot_id IS NULL THEN 'OTHER' ELSE 'VENUE' END,
        coalesce(venue_snapshot_id::text, ''),
        coalesce(venue_snapshot_name, ''),
        coalesce(venue_snapshot_address, ''),
        'v2'))
WHERE venue_snapshot_type IS NULL OR venue_fingerprint IS NULL;

ALTER TABLE course_match_sessions
    ALTER COLUMN venue_snapshot_type SET NOT NULL,
    ALTER COLUMN venue_fingerprint SET NOT NULL,
    ADD CONSTRAINT ck_course_match_sessions_venue_snapshot_type
        CHECK (venue_snapshot_type IN ('VENUE', 'OTHER')),
    ADD CONSTRAINT ck_course_match_sessions_venue_snapshot_identity
        CHECK (
            (venue_snapshot_type = 'VENUE' AND venue_snapshot_id IS NOT NULL)
            OR (venue_snapshot_type = 'OTHER' AND venue_snapshot_id IS NULL)
        );

ALTER TABLE course_match_session_coaches
    DROP CONSTRAINT ck_course_match_session_coaches_role,
    DROP CONSTRAINT ck_course_match_session_coaches_status,
    DROP CONSTRAINT ck_course_match_session_coaches_response,
    DROP CONSTRAINT uk_course_match_session_coaches_session_coach;

DROP INDEX uk_course_match_session_coaches_active_primary;

UPDATE course_match_session_coaches
SET status = 'CANCELLED'
WHERE status = 'REPLACED';

ALTER TABLE course_match_session_coaches
    ADD COLUMN assignment_order smallint;

-- V4 allowed multiple assistants. Assign a deterministic order per session instead
-- of collapsing every assistant to order=2, which would break the new active-order index.
WITH ranked AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY course_match_session_id
               ORDER BY CASE role_type WHEN 'PRIMARY' THEN 0 ELSE 1 END, created_at, id
           )::smallint AS assignment_order
    FROM course_match_session_coaches
)
UPDATE course_match_session_coaches c
SET assignment_order = r.assignment_order
FROM ranked r
WHERE c.id = r.id;

ALTER TABLE course_match_session_coaches
    ALTER COLUMN assignment_order SET NOT NULL,
    ALTER COLUMN assignment_order SET DEFAULT 1,
    DROP COLUMN role_type;
ALTER TABLE course_match_session_coaches RENAME COLUMN invited_at TO invitation_sent_at;
ALTER TABLE course_match_session_coaches RENAME COLUMN response_reason TO response_note;

ALTER TABLE course_match_session_coaches
    ADD CONSTRAINT ck_course_match_session_coaches_assignment_order
        CHECK (assignment_order > 0),
    ADD CONSTRAINT ck_course_match_session_coaches_status
        CHECK (status IN ('INVITED', 'ACCEPTED', 'REJECTED', 'CANCELLED')),
    ADD CONSTRAINT ck_course_match_session_coaches_response
        CHECK (status NOT IN ('ACCEPTED', 'REJECTED') OR responded_at IS NOT NULL);

CREATE UNIQUE INDEX uk_course_match_session_coaches_active_order
    ON course_match_session_coaches(course_match_session_id, assignment_order)
    WHERE status IN ('INVITED', 'ACCEPTED');

CREATE UNIQUE INDEX uk_course_match_session_coaches_active_coach
    ON course_match_session_coaches(course_match_session_id, coach_profile_id)
    WHERE status IN ('INVITED', 'ACCEPTED');
