import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const openapiPath = resolve(root, "backend", "src", "main", "resources", "openapi", "openapi.yaml");
let text = readFileSync(openapiPath, "utf8");

if (text.includes("  /course-offerings:\n")) {
  console.log("Slice 4 OpenAPI contract already applied");
  process.exit(0);
}

const paths = String.raw`
  /course-offerings:
    get:
      operationId: listCourseOfferings
      tags: [Course offerings]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: organizationId, in: query, required: false, schema: { type: string, format: uuid } }
        - { name: status, in: query, required: false, schema: { $ref: '#/components/schemas/CourseOfferingStatus' } }
        - { name: from, in: query, required: false, schema: { type: string, format: date-time } }
        - { name: to, in: query, required: false, schema: { type: string, format: date-time } }
        - { name: coachProfileId, in: query, required: false, schema: { type: string, format: uuid } }
        - { name: skillLevel, in: query, required: false, schema: { type: string, maxLength: 30 } }
        - { name: page, in: query, required: false, schema: { type: integer, minimum: 0, default: 0 } }
        - { name: size, in: query, required: false, schema: { type: integer, minimum: 1, maximum: 100, default: 20 } }
        - { name: sort, in: query, required: false, schema: { type: string } }
      responses:
        '200': { description: Visible course offerings, content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingPageEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
    post:
      operationId: createCourseOffering
      tags: [Course offerings]
      security: [{ bearerAuth: [] }]
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingCreateRequest' } } }
      responses:
        '201': { description: Draft course offering, content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingDetailEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '409': { $ref: '#/components/responses/Conflict' }
        '422': { $ref: '#/components/responses/Unprocessable' }

  /course-offerings/{offeringId}:
    get:
      operationId: getCourseOffering
      tags: [Course offerings]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: offeringId, in: path, required: true, schema: { type: string, format: uuid } }
      responses:
        '200': { description: Course offering detail, content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingDetailEnvelope' } } } }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
    patch:
      operationId: updateCourseOffering
      tags: [Course offerings]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: offeringId, in: path, required: true, schema: { type: string, format: uuid } }
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingUpdateRequest' } } }
      responses:
        '200': { description: Revised draft course offering; any confirmed offering price is superseded, content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingDetailEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }
        '422': { $ref: '#/components/responses/Unprocessable' }

  /course-offerings/{offeringId}/pricing-preview:
    post:
      operationId: previewCourseOfferingPricing
      tags: [Course offerings]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: offeringId, in: path, required: true, schema: { type: string, format: uuid } }
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingPricingPreviewRequest' } } }
      responses:
        '200': { description: Manual per-participant pricing preview with deterministic fingerprint, content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingPricingPreviewEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }
        '422': { $ref: '#/components/responses/Unprocessable' }

  /course-offerings/{offeringId}/pricing-confirmation:
    post:
      operationId: confirmCourseOfferingPricing
      tags: [Course offerings]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: offeringId, in: path, required: true, schema: { type: string, format: uuid } }
        - { name: Idempotency-Key, in: header, required: true, schema: { type: string, minLength: 1, maxLength: 100 } }
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingPricingConfirmationRequest' } } }
      responses:
        '201': { description: Confirmed immutable offering price snapshot, content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingPriceSnapshotEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }
        '422': { $ref: '#/components/responses/Unprocessable' }

  /course-offerings/{offeringId}/publication:
    post:
      operationId: publishCourseOffering
      tags: [Course offerings]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: offeringId, in: path, required: true, schema: { type: string, format: uuid } }
        - { name: Idempotency-Key, in: header, required: true, schema: { type: string, minLength: 1, maxLength: 100 } }
      responses:
        '200': { description: Published offering, content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingDetailEnvelope' } } } }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }
        '422': { $ref: '#/components/responses/Unprocessable' }

  /course-offerings/{offeringId}/closure:
    post:
      operationId: closeCourseOffering
      tags: [Course offerings]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: offeringId, in: path, required: true, schema: { type: string, format: uuid } }
        - { name: Idempotency-Key, in: header, required: true, schema: { type: string, minLength: 1, maxLength: 100 } }
      responses:
        '200': { description: Closed offering, content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingDetailEnvelope' } } } }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }

  /course-offerings/{offeringId}/confirmation:
    post:
      operationId: confirmCourseOffering
      tags: [Course offerings]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: offeringId, in: path, required: true, schema: { type: string, format: uuid } }
        - { name: Idempotency-Key, in: header, required: true, schema: { type: string, minLength: 1, maxLength: 100 } }
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingConfirmationRequest' } } }
      responses:
        '201': { description: Formal course created atomically from offering, content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingConfirmationEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }
        '422': { $ref: '#/components/responses/Unprocessable' }

  /course-offerings/{offeringId}/cancellation:
    post:
      operationId: cancelCourseOffering
      tags: [Course offerings]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: offeringId, in: path, required: true, schema: { type: string, format: uuid } }
        - { name: Idempotency-Key, in: header, required: true, schema: { type: string, minLength: 1, maxLength: 100 } }
      requestBody:
        required: false
        content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingCancellationRequest' } } }
      responses:
        '200': { description: Cancelled offering and active registrations, content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingDetailEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }

  /course-offerings/{offeringId}/registrations:
    get:
      operationId: listCourseOfferingRegistrations
      tags: [Course offering registrations]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: offeringId, in: path, required: true, schema: { type: string, format: uuid } }
        - { name: status, in: query, required: false, schema: { $ref: '#/components/schemas/CourseOfferingRegistrationStatus' } }
        - { name: page, in: query, required: false, schema: { type: integer, minimum: 0, default: 0 } }
        - { name: size, in: query, required: false, schema: { type: integer, minimum: 1, maximum: 100, default: 20 } }
      responses:
        '200': { description: Offering registrations for committee, content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingRegistrationPageEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
    post:
      operationId: registerCourseOffering
      tags: [Course offering registrations]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: offeringId, in: path, required: true, schema: { type: string, format: uuid } }
        - { name: Idempotency-Key, in: header, required: true, schema: { type: string, minLength: 1, maxLength: 100 } }
      responses:
        '201': { description: Active offering registration, content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingRegistrationCommandEnvelope' } } } }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }
        '422': { $ref: '#/components/responses/Unprocessable' }

  /course-offering-registrations/{registrationId}/cancellation:
    post:
      operationId: cancelCourseOfferingRegistration
      tags: [Course offering registrations]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: registrationId, in: path, required: true, schema: { type: string, format: uuid } }
        - { name: Idempotency-Key, in: header, required: true, schema: { type: string, minLength: 1, maxLength: 100 } }
      requestBody:
        required: false
        content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingCancellationRequest' } } }
      responses:
        '200': { description: Cancelled own offering registration, content: { application/json: { schema: { $ref: '#/components/schemas/CourseOfferingRegistrationCommandEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }

  /me/course-offering-registrations:
    get:
      operationId: listMyCourseOfferingRegistrations
      tags: [Course offering registrations]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: page, in: query, required: false, schema: { type: integer, minimum: 0, default: 0 } }
        - { name: size, in: query, required: false, schema: { type: integer, minimum: 1, maximum: 100, default: 20 } }
      responses:
        '200': { description: Current student's offering registration history, content: { application/json: { schema: { $ref: '#/components/schemas/MyCourseOfferingRegistrationPageEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
`;

const schemas = String.raw`
    CourseOfferingStatus: { type: string, enum: [DRAFT, OPEN, CLOSED, CONFIRMED, CANCELLED] }
    CourseOfferingRegistrationStatus: { type: string, enum: [ACTIVE, CANCELLED, CONVERTED] }
    CourseOfferingScheduleType: { type: string, enum: [SINGLE, RECURRING] }
    CourseOfferingBillingMode: { type: string, enum: [FULL_COURSE, PER_SESSION] }

    CourseOfferingSessionPlanRequest:
      type: object
      required: [sequenceNo, startAt, endAt, venueName]
      properties:
        sequenceNo: { type: integer, minimum: 1 }
        startAt: { type: string, format: date-time }
        endAt: { type: string, format: date-time }
        venueId: { type: [string, 'null'], format: uuid }
        venueName: { type: string, minLength: 1, maxLength: 150 }
        venueAddress: { type: [string, 'null'], maxLength: 300 }

    CourseOfferingCreateRequest:
      type: object
      required: [organizationId, lessonType, coachProfileId, title, scheduleType, billingMode, minimumParticipants, maximumParticipants, registrationOpenAt, registrationCloseAt, sessionPlans]
      properties:
        organizationId: { type: string, format: uuid }
        lessonType: { type: string, enum: [GROUP] }
        coachProfileId: { type: string, format: uuid }
        title: { type: string, minLength: 1, maxLength: 200 }
        description: { type: [string, 'null'], maxLength: 10000 }
        scheduleType: { $ref: '#/components/schemas/CourseOfferingScheduleType' }
        billingMode: { $ref: '#/components/schemas/CourseOfferingBillingMode' }
        skillLevel: { type: [string, 'null'], maxLength: 30 }
        minimumParticipants: { type: integer, minimum: 1 }
        maximumParticipants: { type: integer, minimum: 1 }
        registrationOpenAt: { type: string, format: date-time }
        registrationCloseAt: { type: string, format: date-time }
        sessionPlans: { type: array, minItems: 1, items: { $ref: '#/components/schemas/CourseOfferingSessionPlanRequest' } }

    CourseOfferingUpdateRequest:
      type: object
      required: [coachProfileId, title, scheduleType, billingMode, minimumParticipants, maximumParticipants, registrationOpenAt, registrationCloseAt, sessionPlans]
      properties:
        coachProfileId: { type: string, format: uuid }
        title: { type: string, minLength: 1, maxLength: 200 }
        description: { type: [string, 'null'], maxLength: 10000 }
        scheduleType: { $ref: '#/components/schemas/CourseOfferingScheduleType' }
        billingMode: { $ref: '#/components/schemas/CourseOfferingBillingMode' }
        skillLevel: { type: [string, 'null'], maxLength: 30 }
        minimumParticipants: { type: integer, minimum: 1 }
        maximumParticipants: { type: integer, minimum: 1 }
        registrationOpenAt: { type: string, format: date-time }
        registrationCloseAt: { type: string, format: date-time }
        sessionPlans: { type: array, minItems: 1, items: { $ref: '#/components/schemas/CourseOfferingSessionPlanRequest' } }

    CourseOfferingCoachSummary:
      type: object
      required: [coachProfileId, userId, displayName]
      properties:
        coachProfileId: { type: string, format: uuid }
        userId: { type: string, format: uuid }
        displayName: { type: string }

    CourseOfferingSummary:
      type: object
      required: [id, organizationId, title, status, coach, scheduleType, registrationOpenAt, registrationCloseAt, minimumParticipants, maximumParticipants, registeredCount, remainingCapacity, billingMode, registrationState, version]
      properties:
        id: { type: string, format: uuid }
        organizationId: { type: string, format: uuid }
        title: { type: string }
        status: { $ref: '#/components/schemas/CourseOfferingStatus' }
        coach: { $ref: '#/components/schemas/CourseOfferingCoachSummary' }
        scheduleType: { $ref: '#/components/schemas/CourseOfferingScheduleType' }
        firstSessionAt: { type: [string, 'null'], format: date-time }
        registrationOpenAt: { type: string, format: date-time }
        registrationCloseAt: { type: string, format: date-time }
        minimumParticipants: { type: integer, minimum: 1 }
        maximumParticipants: { type: integer, minimum: 1 }
        registeredCount: { type: integer, minimum: 0 }
        remainingCapacity: { type: integer, minimum: 0 }
        billingMode: { $ref: '#/components/schemas/CourseOfferingBillingMode' }
        skillLevel: { type: [string, 'null'] }
        priceSnapshotId: { type: [string, 'null'], format: uuid }
        pricePerParticipant: { type: [number, 'null'] }
        currency: { type: [string, 'null'] }
        registrationState: { type: string, enum: [NOT_OPEN, OPEN, REGISTERED, FULL, CLOSED] }
        ownRegistrationId: { type: [string, 'null'], format: uuid }
        ownRegistrationStatus: { type: [string, 'null'], enum: [ACTIVE, CANCELLED, CONVERTED, null] }
        version: { type: integer, format: int64, minimum: 0 }

    CourseOfferingSessionPlan:
      type: object
      required: [id, sequenceNo, startAt, endAt, venueName]
      properties:
        id: { type: string, format: uuid }
        sequenceNo: { type: integer, minimum: 1 }
        startAt: { type: string, format: date-time }
        endAt: { type: string, format: date-time }
        venueId: { type: [string, 'null'], format: uuid }
        venueName: { type: string }
        venueAddress: { type: [string, 'null'] }

    CourseOfferingDetail:
      type: object
      required: [summary, sessionPlans]
      properties:
        summary: { $ref: '#/components/schemas/CourseOfferingSummary' }
        description: { type: [string, 'null'] }
        sessionPlans: { type: array, items: { $ref: '#/components/schemas/CourseOfferingSessionPlan' } }

    CourseOfferingPricingPreviewRequest:
      type: object
      required: [currency, pricePerParticipant]
      properties:
        currency: { type: string, pattern: '^[A-Za-z]{3}$' }
        pricePerParticipant: { type: number, minimum: 0, multipleOf: 0.01 }

    CourseOfferingPricingConfirmationRequest:
      type: object
      required: [acceptedPricePerParticipant, currency, pricingFingerprint]
      properties:
        acceptedPricePerParticipant: { type: number, minimum: 0, multipleOf: 0.01 }
        currency: { type: string, pattern: '^[A-Za-z]{3}$' }
        pricingFingerprint: { type: string, pattern: '^[0-9a-fA-F]{64}$' }
        confirmationNote: { type: [string, 'null'], maxLength: 5000 }

    CourseOfferingPricingPreview:
      type: object
      required: [offeringId, currency, pricePerParticipant, billingMode, sessionCount, pricingFingerprint]
      properties:
        offeringId: { type: string, format: uuid }
        currency: { type: string }
        pricePerParticipant: { type: string }
        billingMode: { $ref: '#/components/schemas/CourseOfferingBillingMode' }
        sessionCount: { type: integer, minimum: 0 }
        pricingFingerprint: { type: string, minLength: 64, maxLength: 64 }

    CourseOfferingPriceSnapshot:
      type: object
      required: [priceSnapshotId, offeringId, status, currency, pricePerParticipant, pricingFingerprint, confirmedBy, confirmedAt]
      properties:
        priceSnapshotId: { type: string, format: uuid }
        offeringId: { type: string, format: uuid }
        status: { type: string, enum: [CONFIRMED] }
        currency: { type: string }
        pricePerParticipant: { type: string }
        pricingFingerprint: { type: string, minLength: 64, maxLength: 64 }
        confirmedBy: { type: string, format: uuid }
        confirmedAt: { type: string, format: date-time }

    CourseOfferingCancellationRequest:
      type: object
      properties:
        reason: { type: [string, 'null'], maxLength: 5000 }

    CourseOfferingConfirmationRequest:
      type: object
      required: [confirm]
      properties:
        confirm: { type: boolean }

    CourseOfferingConfirmation:
      type: object
      required: [offeringId, offeringStatus, courseId, sessionIds, receivableIds]
      properties:
        offeringId: { type: string, format: uuid }
        offeringStatus: { type: string, enum: [CONFIRMED] }
        courseId: { type: string, format: uuid }
        sessionIds: { type: array, items: { type: string, format: uuid } }
        receivableIds: { type: array, items: { type: string, format: uuid } }

    CourseOfferingRegistrationCommand:
      type: object
      required: [id, offeringId, status, registeredAt]
      properties:
        id: { type: string, format: uuid }
        offeringId: { type: string, format: uuid }
        status: { $ref: '#/components/schemas/CourseOfferingRegistrationStatus' }
        registeredAt: { type: string, format: date-time }
        cancelledAt: { type: [string, 'null'], format: date-time }
        cancelReason: { type: [string, 'null'] }
        convertedCourseMembershipId: { type: [string, 'null'], format: uuid }

    CourseOfferingRegistration:
      type: object
      required: [id, userId, displayName, status, registeredAt, scheduleConflictIndicator]
      properties:
        id: { type: string, format: uuid }
        userId: { type: string, format: uuid }
        displayName: { type: string }
        status: { $ref: '#/components/schemas/CourseOfferingRegistrationStatus' }
        registeredAt: { type: string, format: date-time }
        cancelledAt: { type: [string, 'null'], format: date-time }
        cancelReason: { type: [string, 'null'] }
        scheduleConflictIndicator: { type: boolean }
        convertedCourseMembershipId: { type: [string, 'null'], format: uuid }
        courseId: { type: [string, 'null'], format: uuid }

    MyCourseOfferingRegistration:
      type: object
      required: [id, offeringId, offeringTitle, offeringStatus, status, registeredAt]
      properties:
        id: { type: string, format: uuid }
        offeringId: { type: string, format: uuid }
        offeringTitle: { type: string }
        offeringStatus: { $ref: '#/components/schemas/CourseOfferingStatus' }
        status: { $ref: '#/components/schemas/CourseOfferingRegistrationStatus' }
        registeredAt: { type: string, format: date-time }
        cancelledAt: { type: [string, 'null'], format: date-time }
        cancelReason: { type: [string, 'null'] }
        convertedCourseMembershipId: { type: [string, 'null'], format: uuid }
        courseId: { type: [string, 'null'], format: uuid }

    CourseOfferingPage:
      type: object
      required: [items, page, size, total]
      properties:
        items: { type: array, items: { $ref: '#/components/schemas/CourseOfferingSummary' } }
        page: { type: integer, minimum: 0 }
        size: { type: integer, minimum: 1 }
        total: { type: integer, format: int64, minimum: 0 }

    CourseOfferingRegistrationPage:
      type: object
      required: [items, page, size, total]
      properties:
        items: { type: array, items: { $ref: '#/components/schemas/CourseOfferingRegistration' } }
        page: { type: integer, minimum: 0 }
        size: { type: integer, minimum: 1 }
        total: { type: integer, format: int64, minimum: 0 }

    MyCourseOfferingRegistrationPage:
      type: object
      required: [items, page, size, total]
      properties:
        items: { type: array, items: { $ref: '#/components/schemas/MyCourseOfferingRegistration' } }
        page: { type: integer, minimum: 0 }
        size: { type: integer, minimum: 1 }
        total: { type: integer, format: int64, minimum: 0 }

    CourseOfferingPageEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/CourseOfferingPage' }
        meta: { $ref: '#/components/schemas/Meta' }

    CourseOfferingDetailEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/CourseOfferingDetail' }
        meta: { $ref: '#/components/schemas/Meta' }

    CourseOfferingPricingPreviewEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/CourseOfferingPricingPreview' }
        meta: { $ref: '#/components/schemas/Meta' }

    CourseOfferingPriceSnapshotEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/CourseOfferingPriceSnapshot' }
        meta: { $ref: '#/components/schemas/Meta' }

    CourseOfferingRegistrationCommandEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/CourseOfferingRegistrationCommand' }
        meta: { $ref: '#/components/schemas/Meta' }

    CourseOfferingRegistrationPageEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/CourseOfferingRegistrationPage' }
        meta: { $ref: '#/components/schemas/Meta' }

    MyCourseOfferingRegistrationPageEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/MyCourseOfferingRegistrationPage' }
        meta: { $ref: '#/components/schemas/Meta' }

    CourseOfferingConfirmationEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/CourseOfferingConfirmation' }
        meta: { $ref: '#/components/schemas/Meta' }
`;

text = text.replace(
  "description: P0 API contract through Slice 3 matching and course formation.",
  "description: P0 API contract through Slice 4 Open Enrollment."
);
text = text.replace("\ncomponents:\n", `${paths}\ncomponents:\n`);
text = text.replace("  schemas:\n", `  schemas:\n${schemas}`);
writeFileSync(openapiPath, text, "utf8");
console.log("Applied Slice 4 OpenAPI contract");
