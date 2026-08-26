from pathlib import Path

path = Path("frontend/packages/api-client/src/index.ts")
text = path.read_text()

if "async recordReceivablePayment(" in text:
    print("Finance handwritten adapter already present.")
    raise SystemExit(0)

replacements = [
    (
        "  CurrentUserApi,\n  LessonRequestsApi,",
        "  CurrentUserApi,\n  FinanceApi,\n  LessonRequestsApi,",
    ),
    (
        "  type CourseOfferingUpdateRequest,\n  type LessonRequest,",
        "  type CourseOfferingUpdateRequest,\n"
        "  type FinancePaymentRequest,\n"
        "  type FinancePaymentResponse,\n"
        "  type FinanceRefundExecutionRequest,\n"
        "  type FinanceRefundExecutionResponse,\n"
        "  type FinanceRefundRequest,\n"
        "  type FinanceRefundRequestResponse,\n"
        "  type FinanceRefundReviewRequest,\n"
        "  type FinanceRefundReviewResponse,\n"
        "  type LessonRequest,",
    ),
    (
        "  CourseOfferingUpdateRequest,\n  LessonRequest,",
        "  CourseOfferingUpdateRequest,\n"
        "  FinancePaymentRequest,\n"
        "  FinancePaymentResponse,\n"
        "  FinanceRefundExecutionRequest,\n"
        "  FinanceRefundExecutionResponse,\n"
        "  FinanceRefundRequest,\n"
        "  FinanceRefundRequestResponse,\n"
        "  FinanceRefundReviewRequest,\n"
        "  FinanceRefundReviewResponse,\n"
        "  LessonRequest,",
    ),
    (
        "  const courseOperations = (token: string) => new CourseOperationsApi(new Configuration({ basePath: baseUrl, accessToken: token }));\n  return {",
        "  const courseOperations = (token: string) => new CourseOperationsApi(new Configuration({ basePath: baseUrl, accessToken: token }));\n"
        "  const finance = (token: string) => new FinanceApi(new Configuration({ basePath: baseUrl, accessToken: token }));\n"
        "  return {",
    ),
    (
        "    async rescheduleCourseSession(token: string, sessionId: string, idempotencyKey: string, startAt: Date, endAt: Date, reason: string): Promise<SessionRescheduleResult> { try { return (await courseOperations(token).rescheduleCourseSession({ sessionId, idempotencyKey, directSessionRescheduleRequest: { startAt, endAt, reason } })).data; } catch (caught) { return mapError(caught); } },\n  };\n}",
        "    async rescheduleCourseSession(token: string, sessionId: string, idempotencyKey: string, startAt: Date, endAt: Date, reason: string): Promise<SessionRescheduleResult> { try { return (await courseOperations(token).rescheduleCourseSession({ sessionId, idempotencyKey, directSessionRescheduleRequest: { startAt, endAt, reason } })).data; } catch (caught) { return mapError(caught); } },\n"
        "    async recordReceivablePayment(token: string, receivableId: string, idempotencyKey: string, request: FinancePaymentRequest): Promise<FinancePaymentResponse> { try { return (await finance(token).recordReceivablePayment({ receivableId, idempotencyKey, financePaymentRequest: request })).data; } catch (caught) { return mapError(caught); } },\n"
        "    async requestReceivableRefund(token: string, receivableId: string, idempotencyKey: string, request: FinanceRefundRequest): Promise<FinanceRefundRequestResponse> { try { return (await finance(token).requestReceivableRefund({ receivableId, idempotencyKey, financeRefundRequest: request })).data; } catch (caught) { return mapError(caught); } },\n"
        "    async reviewRefund(token: string, refundId: string, idempotencyKey: string, request: FinanceRefundReviewRequest): Promise<FinanceRefundReviewResponse> { try { return (await finance(token).reviewRefund({ refundId, idempotencyKey, financeRefundReviewRequest: request })).data; } catch (caught) { return mapError(caught); } },\n"
        "    async executeRefund(token: string, refundId: string, idempotencyKey: string, request: FinanceRefundExecutionRequest): Promise<FinanceRefundExecutionResponse> { try { return (await finance(token).executeRefund({ refundId, idempotencyKey, financeRefundExecutionRequest: request })).data; } catch (caught) { return mapError(caught); } },\n"
        "  };\n}",
    ),
]

for old, new in replacements:
    if text.count(old) != 1:
        raise SystemExit(f"Adapter insertion anchor changed or ambiguous: {old[:80]!r}")
    text = text.replace(old, new, 1)

path.write_text(text)
print("Slice 6 finance handwritten adapter inserted.")
