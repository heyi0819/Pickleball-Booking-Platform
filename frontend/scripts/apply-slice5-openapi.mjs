import fs from 'node:fs';
import path from 'node:path';

const file = path.resolve(process.cwd(), '../backend/src/main/resources/openapi/openapi.yaml');
let text = fs.readFileSync(file, 'utf8');

text = text.replace(
  'description: P0 API contract through Slice 4 Open Enrollment.',
  'description: P0 API contract through Slice 5 Course Operations.'
);

const pathMarker = '\ncomponents:\n';
if (!text.includes('  /courses:\n')) {
  const paths = String.raw`
  /courses:
    get:
      operationId: listCourses
      tags: [Course operations]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: organizationId, in: query, required: false, schema: { type: string, format: uuid } }
        - { name: status, in: query, required: false, schema: { type: string, enum: [DRAFT, ACTIVE, COMPLETED, CANCELLED] } }
        - { name: from, in: query, required: false, schema: { type: string, format: date-time } }
        - { name: to, in: query, required: false, schema: { type: string, format: date-time } }
        - { name: coachProfileId, in: query, required: false, schema: { type: string, format: uuid } }
        - { name: studentUserId, in: query, required: false, schema: { type: string, format: uuid } }
        - { name: courseType, in: query, required: false, schema: { type: string, enum: [PRIVATE, GROUP] } }
        - { name: page, in: query, required: false, schema: { type: integer, minimum: 0, default: 0 } }
        - { name: size, in: query, required: false, schema: { type: integer, minimum: 1, maximum: 100, default: 20 } }
        - { name: sort, in: query, required: false, schema: { type: string, enum: ['createdAt,desc', 'createdAt,asc', 'courseNo,asc', 'courseNo,desc', 'nextSessionAt,asc', 'nextSessionAt,desc'] } }
      responses:
        '200': { description: Role-scoped formal courses, content: { application/json: { schema: { $ref: '#/components/schemas/CoursePageEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }

  /courses/{courseId}:
    get:
      operationId: getCourse
      tags: [Course operations]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: courseId, in: path, required: true, schema: { type: string, format: uuid } }
      responses:
        '200': { description: Formal course detail, content: { application/json: { schema: { $ref: '#/components/schemas/CourseDetailEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }

  /courses/{courseId}/sessions:
    get:
      operationId: listCourseSessions
      tags: [Course operations]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: courseId, in: path, required: true, schema: { type: string, format: uuid } }
      responses:
        '200': { description: Sessions for a visible formal course, content: { application/json: { schema: { $ref: '#/components/schemas/CourseSessionListEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }

  /course-sessions/{sessionId}:
    get:
      operationId: getCourseSession
      tags: [Course operations]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: sessionId, in: path, required: true, schema: { type: string, format: uuid } }
      responses:
        '200': { description: Formal course session detail, content: { application/json: { schema: { $ref: '#/components/schemas/CourseSessionEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }

  /session-enrollments/{enrollmentId}/cancellation:
    post:
      operationId: cancelSessionEnrollment
      tags: [Course operations]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: enrollmentId, in: path, required: true, schema: { type: string, format: uuid } }
      requestBody:
        required: false
        content: { application/json: { schema: { $ref: '#/components/schemas/SessionEnrollmentCancellationRequest' } } }
      responses:
        '200': { description: Enrollment cancelled while preserving the CourseSession, content: { application/json: { schema: { $ref: '#/components/schemas/SessionEnrollmentCancellationEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }
        '422': { $ref: '#/components/responses/Unprocessable' }

  /course-sessions/{sessionId}/coach-cancellation-requests:
    post:
      operationId: requestCoachSessionCancellation
      tags: [Course operations]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: sessionId, in: path, required: true, schema: { type: string, format: uuid } }
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: '#/components/schemas/CoachSessionCancellationRequest' } } }
      responses:
        '201': { description: Coach cancellation request in PENDING_REVIEW state, content: { application/json: { schema: { $ref: '#/components/schemas/CoachSessionCancellationEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }
        '422': { $ref: '#/components/responses/Unprocessable' }

  /coach-cancellation-requests/{requestId}/review:
    post:
      operationId: reviewCoachSessionCancellation
      tags: [Course operations]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: requestId, in: path, required: true, schema: { type: string, format: uuid } }
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: '#/components/schemas/CourseOperationReviewRequest' } } }
      responses:
        '200': { description: Committee cancellation decision, content: { application/json: { schema: { $ref: '#/components/schemas/CoachSessionCancellationReviewEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }

  /course-sessions/{sessionId}/change-requests:
    post:
      operationId: createSessionChangeRequest
      tags: [Course operations]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: sessionId, in: path, required: true, schema: { type: string, format: uuid } }
        - { name: Idempotency-Key, in: header, required: true, schema: { type: string, minLength: 1, maxLength: 100 } }
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: '#/components/schemas/SessionRescheduleRequest' } } }
      responses:
        '201': { description: Pending reschedule request without mutating the Session, content: { application/json: { schema: { $ref: '#/components/schemas/SessionChangeRequestEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }
        '422': { $ref: '#/components/responses/Unprocessable' }

  /session-change-requests/{requestId}/review:
    post:
      operationId: reviewSessionChangeRequest
      tags: [Course operations]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: requestId, in: path, required: true, schema: { type: string, format: uuid } }
        - { name: Idempotency-Key, in: header, required: true, schema: { type: string, minLength: 1, maxLength: 100 } }
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: '#/components/schemas/CourseOperationReviewRequest' } } }
      responses:
        '200': { description: Reschedule decision with current Session state, content: { application/json: { schema: { $ref: '#/components/schemas/SessionRescheduleResultEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }
        '422': { $ref: '#/components/responses/Unprocessable' }

  /course-sessions/{sessionId}/reschedule:
    post:
      operationId: rescheduleCourseSession
      tags: [Course operations]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: sessionId, in: path, required: true, schema: { type: string, format: uuid } }
        - { name: Idempotency-Key, in: header, required: true, schema: { type: string, minLength: 1, maxLength: 100 } }
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: '#/components/schemas/DirectSessionRescheduleRequest' } } }
      responses:
        '200': { description: Committee direct reschedule with APPROVED change history, content: { application/json: { schema: { $ref: '#/components/schemas/SessionRescheduleResultEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }
        '422': { $ref: '#/components/responses/Unprocessable' }
`;
  if (!text.includes(pathMarker)) throw new Error('components marker not found');
  text = text.replace(pathMarker, () => `\n${paths}\ncomponents:\n`);
}

const schemaMarker = '    LineLoginRequest:\n';
if (!text.includes('    CourseSummary:\n')) {
  const schemas = String.raw`    CourseSummary:
      type: object
      required: [id, organizationId, courseNo, courseType, scheduleType, billingMode, expectedParticipantCount, totalSessionCount, status, activeMembershipCount]
      properties:
        id: { type: string, format: uuid }
        organizationId: { type: string, format: uuid }
        courseNo: { type: string }
        courseType: { type: string, enum: [PRIVATE, GROUP] }
        scheduleType: { type: string, enum: [SINGLE, RECURRING] }
        billingMode: { type: string, enum: [FULL_COURSE, PER_SESSION] }
        skillLevel: { type: [string, 'null'] }
        expectedParticipantCount: { type: integer, minimum: 1 }
        minimumParticipants: { type: [integer, 'null'], minimum: 1 }
        maximumParticipants: { type: [integer, 'null'], minimum: 1 }
        totalSessionCount: { type: integer, minimum: 1 }
        status: { type: string, enum: [DRAFT, ACTIVE, COMPLETED, CANCELLED] }
        nextSessionStartAt: { type: [string, 'null'], format: date-time }
        activeMembershipCount: { type: integer, minimum: 0 }

    CourseDetail:
      type: object
      required: [id, organizationId, courseNo, createdByUserId, courseType, scheduleType, billingMode, expectedParticipantCount, guestParticipantCount, totalSessionCount, status, createdAt, updatedAt, activeMembershipCount]
      properties:
        id: { type: string, format: uuid }
        organizationId: { type: string, format: uuid }
        courseNo: { type: string }
        sourceMatchId: { type: [string, 'null'], format: uuid }
        sourceOfferingId: { type: [string, 'null'], format: uuid }
        createdByUserId: { type: string, format: uuid }
        courseType: { type: string, enum: [PRIVATE, GROUP] }
        scheduleType: { type: string, enum: [SINGLE, RECURRING] }
        billingMode: { type: string, enum: [FULL_COURSE, PER_SESSION] }
        skillLevel: { type: [string, 'null'] }
        expectedParticipantCount: { type: integer, minimum: 1 }
        guestParticipantCount: { type: integer, minimum: 0 }
        minimumParticipants: { type: [integer, 'null'], minimum: 1 }
        maximumParticipants: { type: [integer, 'null'], minimum: 1 }
        totalSessionCount: { type: integer, minimum: 1 }
        status: { type: string, enum: [DRAFT, ACTIVE, COMPLETED, CANCELLED] }
        activatedAt: { type: [string, 'null'], format: date-time }
        completedAt: { type: [string, 'null'], format: date-time }
        cancelledAt: { type: [string, 'null'], format: date-time }
        createdAt: { type: string, format: date-time }
        updatedAt: { type: string, format: date-time }
        nextSessionStartAt: { type: [string, 'null'], format: date-time }
        activeMembershipCount: { type: integer, minimum: 0 }

    CourseSessionSummary:
      type: object
      required: [id, organizationId, courseId, sequenceNo, scheduledStartAt, scheduledEndAt, expectedParticipantCount, guestParticipantCount, status]
      properties:
        id: { type: string, format: uuid }
        organizationId: { type: string, format: uuid }
        courseId: { type: string, format: uuid }
        sequenceNo: { type: integer, minimum: 1 }
        scheduledStartAt: { type: string, format: date-time }
        scheduledEndAt: { type: string, format: date-time }
        expectedParticipantCount: { type: integer, minimum: 1 }
        guestParticipantCount: { type: integer, minimum: 0 }
        actualParticipantCount: { type: [integer, 'null'], minimum: 0 }
        status: { type: string, enum: [SCHEDULED, CANCEL_PENDING, CANCELLED, COMPLETED, POSTPONED] }
        cancellationSource: { type: [string, 'null'], enum: [STUDENT, COACH, COMMITTEE, SYSTEM, null] }
        cancellationNote: { type: [string, 'null'] }
        completedAt: { type: [string, 'null'], format: date-time }
        venueId: { type: [string, 'null'], format: uuid }
        venueName: { type: [string, 'null'] }
        venueAddress: { type: [string, 'null'] }
        venueStatus: { type: [string, 'null'], enum: [CONFIRMED, null] }
        coachProfileId: { type: [string, 'null'], format: uuid }
        coachDisplayName: { type: [string, 'null'] }
        ownEnrollmentId: { type: [string, 'null'], format: uuid }
        ownEnrollmentStatus: { type: [string, 'null'], enum: [SCHEDULED, CANCELLED, ATTENDED, ABSENT, null] }

    CoursePage:
      type: object
      required: [items, page, size, total]
      properties:
        items: { type: array, items: { $ref: '#/components/schemas/CourseSummary' } }
        page: { type: integer, minimum: 0 }
        size: { type: integer, minimum: 1 }
        total: { type: integer, format: int64, minimum: 0 }

    SessionEnrollmentCancellationRequest:
      type: object
      properties:
        reason: { type: [string, 'null'], maxLength: 5000 }

    SessionEnrollmentCancellation:
      type: object
      required: [enrollmentId, status, cancelledAt, courseSessionStatus]
      properties:
        enrollmentId: { type: string, format: uuid }
        status: { type: string, enum: [CANCELLED] }
        cancelledAt: { type: string, format: date-time }
        courseSessionStatus: { type: string, enum: [SCHEDULED, CANCEL_PENDING, CANCELLED, COMPLETED, POSTPONED] }

    CoachSessionCancellationRequest:
      type: object
      required: [reason]
      properties:
        reason: { type: string, minLength: 1, maxLength: 5000 }

    CoachSessionCancellation:
      type: object
      required: [requestId, sessionId, status, reason, createdAt]
      properties:
        requestId: { type: string, format: uuid }
        sessionId: { type: string, format: uuid }
        status: { type: string, enum: [PENDING_REVIEW, APPROVED, REJECTED, WITHDRAWN] }
        reason: { type: string }
        createdAt: { type: string, format: date-time }
        reviewedAt: { type: [string, 'null'], format: date-time }

    CourseOperationReviewRequest:
      type: object
      required: [decision, reason]
      properties:
        decision: { type: string, enum: [APPROVE, REJECT] }
        reason: { type: string, minLength: 1, maxLength: 5000 }

    CoachSessionCancellationReview:
      type: object
      required: [requestId, status, sessionId, sessionStatus]
      properties:
        requestId: { type: string, format: uuid }
        status: { type: string, enum: [APPROVED, REJECTED] }
        sessionId: { type: string, format: uuid }
        sessionStatus: { type: string, enum: [SCHEDULED, CANCELLED] }
        reviewedAt: { type: [string, 'null'], format: date-time }

    SessionRescheduleRequest:
      type: object
      required: [requestType, proposedStartAt, proposedEndAt, reason]
      properties:
        requestType: { type: string, enum: [RESCHEDULE] }
        proposedStartAt: { type: string, format: date-time }
        proposedEndAt: { type: string, format: date-time }
        reason: { type: string, minLength: 1, maxLength: 5000 }

    SessionChangeRequest:
      type: object
      required: [changeRequestId, sessionId, status, requestType, proposedStartAt, proposedEndAt, reason]
      properties:
        changeRequestId: { type: string, format: uuid }
        sessionId: { type: string, format: uuid }
        status: { type: string, enum: [PENDING, APPROVED, REJECTED, WITHDRAWN] }
        requestType: { type: string, enum: [RESCHEDULE] }
        proposedStartAt: { type: string, format: date-time }
        proposedEndAt: { type: string, format: date-time }
        reason: { type: string }
        decidedBy: { type: [string, 'null'], format: uuid }
        decidedAt: { type: [string, 'null'], format: date-time }
        decisionReason: { type: [string, 'null'] }

    DirectSessionRescheduleRequest:
      type: object
      required: [startAt, endAt, reason]
      properties:
        startAt: { type: string, format: date-time }
        endAt: { type: string, format: date-time }
        reason: { type: string, minLength: 1, maxLength: 5000 }

    SessionRescheduleResult:
      type: object
      required: [changeRequestId, status, sessionId, sessionStatus, scheduledStartAt, scheduledEndAt]
      properties:
        changeRequestId: { type: string, format: uuid }
        status: { type: string, enum: [APPROVED, REJECTED] }
        sessionId: { type: string, format: uuid }
        sessionStatus: { type: string, enum: [SCHEDULED, POSTPONED, CANCEL_PENDING, CANCELLED, COMPLETED] }
        scheduledStartAt: { type: string, format: date-time }
        scheduledEndAt: { type: string, format: date-time }

    CoursePageEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/CoursePage' }
        meta: { $ref: '#/components/schemas/Meta' }

    CourseDetailEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/CourseDetail' }
        meta: { $ref: '#/components/schemas/Meta' }

    CourseSessionListEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { type: array, items: { $ref: '#/components/schemas/CourseSessionSummary' } }
        meta: { $ref: '#/components/schemas/Meta' }

    CourseSessionEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/CourseSessionSummary' }
        meta: { $ref: '#/components/schemas/Meta' }

    SessionEnrollmentCancellationEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/SessionEnrollmentCancellation' }
        meta: { $ref: '#/components/schemas/Meta' }

    CoachSessionCancellationEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/CoachSessionCancellation' }
        meta: { $ref: '#/components/schemas/Meta' }

    CoachSessionCancellationReviewEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/CoachSessionCancellationReview' }
        meta: { $ref: '#/components/schemas/Meta' }

    SessionChangeRequestEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/SessionChangeRequest' }
        meta: { $ref: '#/components/schemas/Meta' }

    SessionRescheduleResultEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/SessionRescheduleResult' }
        meta: { $ref: '#/components/schemas/Meta' }

`;
  if (!text.includes(schemaMarker)) throw new Error('schema marker not found');
  text = text.replace(schemaMarker, () => schemas + schemaMarker);
}

fs.writeFileSync(file, text);
console.log('Applied Slice 5 Course Operations OpenAPI contract');
