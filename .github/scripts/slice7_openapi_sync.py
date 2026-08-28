from pathlib import Path

openapi = Path("backend/src/main/resources/openapi/openapi.yaml")
text = openapi.read_text()

if "/course-sessions/{sessionId}/settlement:" in text:
    raise SystemExit("Slice 7 paths already present")

text = text.replace(
    "description: P0 API contract through Slice 6 Finance.",
    "description: P0 API contract through Slice 7 Settlement / Payout.",
    1,
)

paths = r'''

  /course-sessions/{sessionId}/settlement:
    get:
      operationId: getCourseSessionSettlement
      tags: [Settlement]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: sessionId, in: path, required: true, schema: { type: string, format: uuid } }
      responses:
        '200': { description: Session settlement detail, content: { application/json: { schema: { $ref: '#/components/schemas/SessionSettlementEnvelope' } } } }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }

  /course-sessions/{sessionId}/settlement-calculation:
    post:
      operationId: calculateCourseSessionSettlement
      tags: [Settlement]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: sessionId, in: path, required: true, schema: { type: string, format: uuid } }
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: '#/components/schemas/SettlementCalculationRequest' } } }
      responses:
        '200': { description: Settlement calculated from immutable session price and finance facts, content: { application/json: { schema: { $ref: '#/components/schemas/SettlementCalculationEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }
        '422': { $ref: '#/components/responses/Unprocessable' }

  /session-settlements/{settlementId}/confirmation:
    post:
      operationId: confirmSessionSettlement
      tags: [Settlement]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: settlementId, in: path, required: true, schema: { type: string, format: uuid } }
        - { name: Idempotency-Key, in: header, required: true, schema: { type: string, minLength: 1, maxLength: 100 } }
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: '#/components/schemas/SettlementConfirmationRequest' } } }
      responses:
        '200': { description: Settlement confirmed idempotently, content: { application/json: { schema: { $ref: '#/components/schemas/SettlementConfirmationEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }

  /me/coach-settlements:
    get:
      operationId: listMyCoachSettlements
      tags: [Settlement]
      security: [{ bearerAuth: [] }]
      responses:
        '200': { description: Current coach payout-facing settlement read model, content: { application/json: { schema: { $ref: '#/components/schemas/CoachSettlementSelfListEnvelope' } } } }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }

  /payout-batches:
    post:
      operationId: createPayoutBatch
      tags: [Payouts]
      security: [{ bearerAuth: [] }]
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: '#/components/schemas/PayoutBatchCreateRequest' } } }
      responses:
        '201': { description: Draft manual payout batch, content: { application/json: { schema: { $ref: '#/components/schemas/PayoutBatchCreateEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }
        '422': { $ref: '#/components/responses/Unprocessable' }

  /payout-batches/{batchId}:
    get:
      operationId: getPayoutBatch
      tags: [Payouts]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: batchId, in: path, required: true, schema: { type: string, format: uuid } }
      responses:
        '200': { description: Payout batch detail, content: { application/json: { schema: { $ref: '#/components/schemas/PayoutBatchEnvelope' } } } }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }

  /payout-batches/{batchId}/execution:
    post:
      operationId: executePayoutBatch
      tags: [Payouts]
      security: [{ bearerAuth: [] }]
      parameters:
        - { name: batchId, in: path, required: true, schema: { type: string, format: uuid } }
        - { name: Idempotency-Key, in: header, required: true, schema: { type: string, minLength: 1, maxLength: 100 } }
      requestBody:
        required: true
        content: { application/json: { schema: { $ref: '#/components/schemas/PayoutExecutionRequest' } } }
      responses:
        '200': { description: Manual payout batch executed idempotently, content: { application/json: { schema: { $ref: '#/components/schemas/PayoutExecutionEnvelope' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/InvalidToken' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '409': { $ref: '#/components/responses/Conflict' }
        '422': { $ref: '#/components/responses/Unprocessable' }
'''

schemas = r'''

    SettlementMoneyAmount:
      type: string
      pattern: '^[0-9]{1,10}(?:\.[0-9]{1,2})?$'
      example: '1500.00'
    SettlementSignedMoneyAmount:
      type: string
      pattern: '^-?[0-9]{1,10}(?:\.[0-9]{1,2})?$'
      example: '0.00'
    SettlementAllocationValue:
      type: [string, 'null']
      pattern: '^[0-9]{1,10}(?:\.[0-9]{1,4})?$'
    SettlementStatus: { type: string, enum: [PENDING_CALCULATION, CALCULATED, PENDING_APPROVAL, CONFIRMED, VOIDED] }
    SettlementAllocationType: { type: string, enum: [EQUAL, PERCENTAGE, FIXED] }
    CoachPayoutStatus: { type: string, enum: [WAITING_RECEIPT, READY, IN_BATCH, PARTIALLY_PAID, PAID, ON_HOLD, CANCELLED] }

    SettlementCoachAllocationRequest:
      type: object
      additionalProperties: false
      required: [coachProfileId, allocationType]
      properties:
        coachProfileId: { type: string, format: uuid }
        allocationType: { $ref: '#/components/schemas/SettlementAllocationType' }
        allocationValue: { $ref: '#/components/schemas/SettlementAllocationValue' }

    SettlementCalculationRequest:
      type: object
      additionalProperties: false
      properties:
        otherAdjustment: { $ref: '#/components/schemas/SettlementSignedMoneyAmount' }
        coachAllocations:
          type: array
          maxItems: 20
          items: { $ref: '#/components/schemas/SettlementCoachAllocationRequest' }

    SettlementCalculationResponse:
      type: object
      required: [sessionSettlementId, grossReceivable, venueCost, otherAdjustment, distributableAmount, coachPayableTotal, status]
      properties:
        sessionSettlementId: { type: string, format: uuid }
        grossReceivable: { $ref: '#/components/schemas/SettlementMoneyAmount' }
        venueCost: { $ref: '#/components/schemas/SettlementMoneyAmount' }
        otherAdjustment: { $ref: '#/components/schemas/SettlementSignedMoneyAmount' }
        distributableAmount: { $ref: '#/components/schemas/SettlementMoneyAmount' }
        coachPayableTotal: { $ref: '#/components/schemas/SettlementMoneyAmount' }
        status: { type: string, enum: [CALCULATED] }

    SettlementCalculationEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/SettlementCalculationResponse' }
        meta: { $ref: '#/components/schemas/Meta' }

    SettlementConfirmationRequest:
      type: object
      additionalProperties: false
      required: [reason]
      properties:
        reason: { type: string, minLength: 1, maxLength: 5000 }

    SettlementConfirmationResponse:
      type: object
      required: [sessionSettlementId, status, confirmedAt]
      properties:
        sessionSettlementId: { type: string, format: uuid }
        status: { type: string, enum: [CONFIRMED] }
        confirmedAt: { type: string, format: date-time }

    SettlementConfirmationEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/SettlementConfirmationResponse' }
        meta: { $ref: '#/components/schemas/Meta' }

    CoachSettlementAllocationResponse:
      type: object
      required: [coachSettlementId, coachProfileId, payableAmount, paidAmount, payoutStatus, version]
      properties:
        coachSettlementId: { type: string, format: uuid }
        coachProfileId: { type: string, format: uuid }
        payableAmount: { $ref: '#/components/schemas/SettlementMoneyAmount' }
        paidAmount: { $ref: '#/components/schemas/SettlementMoneyAmount' }
        payoutStatus: { $ref: '#/components/schemas/CoachPayoutStatus' }
        version: { type: integer, format: int64, minimum: 0 }

    SessionSettlementResponse:
      type: object
      required: [sessionSettlementId, courseSessionId, status, grossReceivable, venueCost, otherAdjustment, distributableAmount, coachSettlements, version]
      properties:
        sessionSettlementId: { type: string, format: uuid }
        courseSessionId: { type: string, format: uuid }
        status: { $ref: '#/components/schemas/SettlementStatus' }
        grossReceivable: { $ref: '#/components/schemas/SettlementMoneyAmount' }
        venueCost: { $ref: '#/components/schemas/SettlementMoneyAmount' }
        otherAdjustment: { $ref: '#/components/schemas/SettlementSignedMoneyAmount' }
        distributableAmount: { $ref: '#/components/schemas/SettlementMoneyAmount' }
        coachSettlements: { type: array, items: { $ref: '#/components/schemas/CoachSettlementAllocationResponse' } }
        version: { type: integer, format: int64, minimum: 0 }

    SessionSettlementEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/SessionSettlementResponse' }
        meta: { $ref: '#/components/schemas/Meta' }

    CoachSettlementSelfItem:
      type: object
      required: [coachSettlementId, courseSessionId, payableAmount, paidAmount, outstandingAmount, payoutStatus]
      properties:
        coachSettlementId: { type: string, format: uuid }
        courseSessionId: { type: string, format: uuid }
        payableAmount: { $ref: '#/components/schemas/SettlementMoneyAmount' }
        paidAmount: { $ref: '#/components/schemas/SettlementMoneyAmount' }
        outstandingAmount: { $ref: '#/components/schemas/SettlementMoneyAmount' }
        payoutStatus: { $ref: '#/components/schemas/CoachPayoutStatus' }

    CoachSettlementSelfList:
      type: object
      required: [items]
      properties:
        items: { type: array, items: { $ref: '#/components/schemas/CoachSettlementSelfItem' } }

    CoachSettlementSelfListEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/CoachSettlementSelfList' }
        meta: { $ref: '#/components/schemas/Meta' }

    PayoutMethod: { type: string, enum: [CASH, BANK_TRANSFER, OTHER], default: CASH }
    PayoutBatchStatus: { type: string, enum: [DRAFT, APPROVED, PROCESSING, COMPLETED, CANCELLED] }
    PayoutBatchItemStatus: { type: string, enum: [PLANNED, PAID, FAILED, CANCELLED] }

    PayoutBatchCreateItemRequest:
      type: object
      additionalProperties: false
      required: [coachSettlementId, amount]
      properties:
        coachSettlementId: { type: string, format: uuid }
        amount: { $ref: '#/components/schemas/SettlementMoneyAmount' }

    PayoutBatchCreateRequest:
      type: object
      additionalProperties: false
      required: [payoutDate, items]
      properties:
        method: { $ref: '#/components/schemas/PayoutMethod' }
        payoutDate: { type: string, format: date }
        items:
          type: array
          minItems: 1
          maxItems: 100
          items: { $ref: '#/components/schemas/PayoutBatchCreateItemRequest' }

    PayoutBatchCreateResponse:
      type: object
      required: [payoutBatchId, batchNo, status, method, totalAmount, itemCount]
      properties:
        payoutBatchId: { type: string, format: uuid }
        batchNo: { type: string }
        status: { type: string, enum: [DRAFT] }
        method: { $ref: '#/components/schemas/PayoutMethod' }
        totalAmount: { $ref: '#/components/schemas/SettlementMoneyAmount' }
        itemCount: { type: integer, minimum: 1 }

    PayoutBatchCreateEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/PayoutBatchCreateResponse' }
        meta: { $ref: '#/components/schemas/Meta' }

    PayoutBatchItemResponse:
      type: object
      required: [payoutBatchItemId, coachSettlementId, coachProfileId, amount, status]
      properties:
        payoutBatchItemId: { type: string, format: uuid }
        coachSettlementId: { type: string, format: uuid }
        coachProfileId: { type: string, format: uuid }
        amount: { $ref: '#/components/schemas/SettlementMoneyAmount' }
        status: { $ref: '#/components/schemas/PayoutBatchItemStatus' }
        paidAt: { type: [string, 'null'], format: date-time }
        referenceNo: { type: [string, 'null'], maxLength: 100 }
        failureReason: { type: [string, 'null'] }

    PayoutBatchResponse:
      type: object
      required: [payoutBatchId, batchNo, status, payoutDate, method, currency, totalAmount, itemCount, items]
      properties:
        payoutBatchId: { type: string, format: uuid }
        batchNo: { type: string }
        status: { $ref: '#/components/schemas/PayoutBatchStatus' }
        payoutDate: { type: string, format: date }
        method: { $ref: '#/components/schemas/PayoutMethod' }
        currency: { type: string, enum: [TWD] }
        totalAmount: { $ref: '#/components/schemas/SettlementMoneyAmount' }
        itemCount: { type: integer, minimum: 0 }
        approvedAt: { type: [string, 'null'], format: date-time }
        completedAt: { type: [string, 'null'], format: date-time }
        items: { type: array, items: { $ref: '#/components/schemas/PayoutBatchItemResponse' } }

    PayoutBatchEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/PayoutBatchResponse' }
        meta: { $ref: '#/components/schemas/Meta' }

    PayoutExecutionRequest:
      type: object
      additionalProperties: false
      required: [paidAt, reason]
      properties:
        paidAt: { type: string, format: date-time }
        referenceNo: { type: [string, 'null'], maxLength: 100 }
        reason: { type: string, minLength: 1, maxLength: 5000 }

    PayoutExecutionResponse:
      type: object
      required: [payoutBatchId, status, totalAmount, itemCount, completedAt]
      properties:
        payoutBatchId: { type: string, format: uuid }
        status: { type: string, enum: [COMPLETED] }
        totalAmount: { $ref: '#/components/schemas/SettlementMoneyAmount' }
        itemCount: { type: integer, minimum: 1 }
        completedAt: { type: string, format: date-time }

    PayoutExecutionEnvelope:
      type: object
      required: [data, meta]
      properties:
        data: { $ref: '#/components/schemas/PayoutExecutionResponse' }
        meta: { $ref: '#/components/schemas/Meta' }
'''

marker = "\ncomponents:\n"
if marker not in text:
    raise SystemExit("OpenAPI components marker not found")
text = text.replace(marker, paths + marker, 1)

schema_marker = "  schemas:\n"
if schema_marker not in text:
    raise SystemExit("OpenAPI schemas marker not found")
text = text.replace(schema_marker, schema_marker + schemas, 1)

openapi.write_text(text)
