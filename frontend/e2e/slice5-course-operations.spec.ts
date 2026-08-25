import { expect, test, type Page, type Route } from "@playwright/test";

const org = "00000000-0000-0000-0000-000000000010";
const courseId = "00000000-0000-0000-0000-000000000501";
const sessionId = "00000000-0000-0000-0000-000000000502";
const enrollmentId = "00000000-0000-0000-0000-000000000503";
const meta = { requestId: "slice5-browser-e2e" };
const envelope = (data: unknown) => ({ data, meta });

const course = {
  id: courseId,
  organizationId: org,
  courseNo: "PB-2026-005",
  courseType: "GROUP",
  scheduleType: "RECURRING",
  billingMode: "FULL_COURSE",
  skillLevel: "BEGINNER",
  expectedParticipantCount: 6,
  minimumParticipants: 4,
  maximumParticipants: 6,
  totalSessionCount: 8,
  status: "ACTIVE",
  nextSessionStartAt: "2026-09-02T02:00:00Z",
  activeMembershipCount: 6,
};

const baseSession = {
  id: sessionId,
  organizationId: org,
  courseId,
  sequenceNo: 2,
  scheduledStartAt: "2026-09-02T02:00:00Z",
  scheduledEndAt: "2026-09-02T03:00:00Z",
  expectedParticipantCount: 6,
  guestParticipantCount: 0,
  actualParticipantCount: null,
  status: "SCHEDULED",
  cancellationSource: null,
  cancellationNote: null,
  completedAt: null,
  venueId: "venue-1",
  venueName: "Court A",
  venueAddress: "Taipei",
  venueStatus: "CONFIRMED",
  coachProfileId: "coach-profile-1",
  coachDisplayName: "Coach Lin",
  ownEnrollmentId: null,
  ownEnrollmentStatus: null,
};

const studentMe = envelope({
  id: "student-user",
  displayName: "Student",
  phone: null,
  email: null,
  locale: "zh-TW",
  profileComplete: true,
  roles: [{ roleCode: "STUDENT", organizationId: org, organizationCode: "MVP", organizationName: "MVP" }],
});

const coachMe = envelope({
  id: "coach-user",
  displayName: "Coach Lin",
  phone: null,
  email: null,
  locale: "zh-TW",
  profileComplete: true,
  roles: [{ roleCode: "COACH", organizationId: org, organizationCode: "MVP", organizationName: "MVP" }],
});

const committeeMe = envelope({
  id: "committee-user",
  displayName: "Committee",
  phone: null,
  email: null,
  locale: "zh-TW",
  profileComplete: true,
  roles: [{ roleCode: "COMMITTEE", organizationId: org, organizationCode: "MVP", organizationName: "MVP" }],
});

const changeQueueItem = {
  requestId: "change-request-1",
  sessionId,
  courseId,
  courseNo: course.courseNo,
  sequenceNo: 2,
  scheduledStartAt: "2026-09-02T02:00:00Z",
  scheduledEndAt: "2026-09-02T03:00:00Z",
  requestedBy: "student-user",
  requesterDisplayName: "Student",
  proposedStartAt: "2026-09-03T02:00:00Z",
  proposedEndAt: "2026-09-03T03:00:00Z",
  reason: "School event",
  status: "PENDING",
  createdAt: "2026-08-25T12:00:00Z",
};

const cancellationQueueItem = {
  requestId: "coach-cancel-request-1",
  sessionId,
  courseId,
  courseNo: course.courseNo,
  sequenceNo: 2,
  scheduledStartAt: "2026-09-02T02:00:00Z",
  scheduledEndAt: "2026-09-02T03:00:00Z",
  requestedBy: "coach-user",
  requesterDisplayName: "Coach Lin",
  reason: "Tournament duty",
  status: "PENDING_REVIEW",
  createdAt: "2026-08-25T12:05:00Z",
};

async function fulfill(route: Route, data: unknown, status = 200) {
  await route.fulfill({ status, contentType: "application/json", body: JSON.stringify(envelope(data)) });
}

async function withToken(page: Page, token = "slice5-e2e-token") {
  await page.addInitScript((value) => sessionStorage.setItem("platform.access-token", value), token);
}

function emptyPage() {
  return { items: [], page: 0, size: 100, total: 0 };
}

function coursePage() {
  return { items: [course], page: 0, size: 100, total: 1 };
}

test("student LIFF cancels one formal-course session and submits a reschedule request with confirmation", async ({ page }) => {
  await withToken(page, "student-token");
  let enrollmentStatus: "SCHEDULED" | "CANCELLED" = "SCHEDULED";
  let cancellationReason = "";
  let changeReason = "";
  let changeIdempotencyKey = "";

  const studentSession = () => ({
    ...baseSession,
    ownEnrollmentId: enrollmentId,
    ownEnrollmentStatus: enrollmentStatus,
  });

  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();

    if (method === "GET" && path === "/api/v1/me") return route.fulfill({ json: studentMe });
    if (method === "GET" && path === "/api/v1/courses") return fulfill(route, coursePage());
    if (method === "GET" && path === `/api/v1/courses/${courseId}/sessions`) return fulfill(route, [studentSession()]);
    if (method === "GET" && path === "/api/v1/course-offerings") return fulfill(route, emptyPage());
    if (method === "GET" && path === "/api/v1/me/course-offering-registrations") return fulfill(route, emptyPage());
    if (method === "GET" && path === "/api/v1/coach-availability-proposals/available") return fulfill(route, []);
    if (method === "GET" && path === "/api/v1/lesson-requests/mine") return fulfill(route, []);
    if (method === "POST" && path === `/api/v1/session-enrollments/${enrollmentId}/cancellation`) {
      const body = request.postDataJSON() as { reason?: string | null };
      cancellationReason = body.reason ?? "";
      enrollmentStatus = "CANCELLED";
      return fulfill(route, { enrollmentId, sessionId, status: "CANCELLED", reason: body.reason ?? null, cancelledAt: "2026-08-25T12:10:00Z" });
    }
    if (method === "POST" && path === `/api/v1/course-sessions/${sessionId}/change-requests`) {
      const body = request.postDataJSON() as { reason?: string };
      changeReason = body.reason ?? "";
      changeIdempotencyKey = request.headers()["idempotency-key"] ?? "";
      return fulfill(route, { id: "student-change-1", sessionId, status: "PENDING", reason: changeReason, createdAt: "2026-08-25T12:11:00Z" }, 201);
    }
    return route.abort();
  });

  await page.goto("http://127.0.0.1:4173");
  await expect(page.getByRole("heading", { name: "STUDENT entry" })).toBeVisible();
  const operations = page.getByRole("region", { name: "Student course operations" });
  await expect(operations.getByRole("heading", { name: "我的正式課程" })).toBeVisible();
  await expect(operations).toContainText(course.courseNo);

  await operations.getByRole("button", { name: "取消本堂報名" }).click();
  const cancelDialog = page.getByRole("dialog", { name: "確認取消本堂報名" });
  await expect(cancelDialog).toBeVisible();
  await cancelDialog.getByLabel("原因（選填）").fill("Family appointment");
  await cancelDialog.getByRole("button", { name: "確認取消" }).click();
  await expect(operations.getByRole("status")).toContainText("其他堂次不受影響");
  expect(cancellationReason).toBe("Family appointment");
  await expect(operations.getByRole("button", { name: "取消本堂報名" })).toHaveCount(0);

  await operations.getByLabel("新開始時間").fill("2026-09-03T10:00");
  await operations.getByLabel("新結束時間").fill("2026-09-03T11:00");
  await operations.getByLabel("改期原因").fill("School event");
  await operations.getByRole("button", { name: "申請改期" }).click();
  const rescheduleDialog = page.getByRole("dialog", { name: "確認改期申請" });
  await expect(rescheduleDialog).toContainText("School event");
  await rescheduleDialog.getByRole("button", { name: "確認送出" }).click();
  await expect(operations.getByRole("status")).toContainText("等待委員會審核");
  expect(changeReason).toBe("School event");
  expect(changeIdempotencyKey).toContain(`liff-student-reschedule-${sessionId}-`);
});

test("coach LIFF submits reschedule and cancellation requests through secondary confirmation", async ({ page }) => {
  await withToken(page, "coach-token");
  let sessionStatus: "SCHEDULED" | "CANCEL_PENDING" = "SCHEDULED";
  let rescheduleReason = "";
  let rescheduleIdempotencyKey = "";
  let cancellationReason = "";

  const coachSession = () => ({ ...baseSession, status: sessionStatus });

  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();

    if (method === "GET" && path === "/api/v1/me") return route.fulfill({ json: coachMe });
    if (method === "GET" && path === "/api/v1/courses") return fulfill(route, coursePage());
    if (method === "GET" && path === `/api/v1/courses/${courseId}/sessions`) return fulfill(route, [coachSession()]);
    if (method === "GET" && path === "/api/v1/coach-availability-proposals/mine") return fulfill(route, []);
    if (method === "GET" && path === "/api/v1/course-match-invitations/mine") return fulfill(route, []);
    if (method === "POST" && path === `/api/v1/course-sessions/${sessionId}/change-requests`) {
      const body = request.postDataJSON() as { reason?: string };
      rescheduleReason = body.reason ?? "";
      rescheduleIdempotencyKey = request.headers()["idempotency-key"] ?? "";
      return fulfill(route, { id: "coach-change-1", sessionId, status: "PENDING", reason: rescheduleReason, createdAt: "2026-08-25T12:20:00Z" }, 201);
    }
    if (method === "POST" && path === `/api/v1/course-sessions/${sessionId}/coach-cancellation-requests`) {
      const body = request.postDataJSON() as { reason?: string };
      cancellationReason = body.reason ?? "";
      sessionStatus = "CANCEL_PENDING";
      return fulfill(route, { id: "coach-cancel-1", sessionId, status: "PENDING_REVIEW", reason: cancellationReason, createdAt: "2026-08-25T12:21:00Z" }, 201);
    }
    return route.abort();
  });

  await page.goto("http://127.0.0.1:4173");
  await expect(page.getByRole("heading", { name: "COACH entry" })).toBeVisible();
  const operations = page.getByRole("region", { name: "Coach course operations" });
  await expect(operations.getByRole("heading", { name: "我的授課課程" })).toBeVisible();

  await operations.getByLabel("新開始時間").fill("2026-09-04T10:00");
  await operations.getByLabel("新結束時間").fill("2026-09-04T11:00");
  await operations.getByLabel("改期原因").fill("Coach clinic");
  await operations.getByRole("button", { name: "申請改期" }).click();
  const rescheduleDialog = page.getByRole("dialog", { name: "確認改期申請" });
  await expect(rescheduleDialog).toContainText("Coach clinic");
  await rescheduleDialog.getByRole("button", { name: "確認送出" }).click();
  await expect(operations.getByRole("status")).toContainText("改期申請已送出");
  expect(rescheduleReason).toBe("Coach clinic");
  expect(rescheduleIdempotencyKey).toContain(`liff-coach-reschedule-${sessionId}-`);

  await operations.getByLabel("取消授課原因").fill("Tournament duty");
  await operations.getByRole("button", { name: "申請取消授課" }).click();
  const cancelDialog = page.getByRole("dialog", { name: "確認取消授課申請" });
  await expect(cancelDialog).toContainText("Tournament duty");
  await cancelDialog.getByRole("button", { name: "確認送出" }).click();
  await expect(operations.getByRole("status")).toContainText("取消授課申請已送出");
  expect(cancellationReason).toBe("Tournament duty");
  await expect(operations).toContainText("CANCEL_PENDING");
});

test("committee LIFF reviews pending reschedule and coach cancellation queues", async ({ page }) => {
  await withToken(page, "committee-token");
  let changePending = true;
  let cancellationPending = true;
  let changeReview: { decision?: string; reason?: string } = {};
  let cancellationReview: { decision?: string; reason?: string } = {};
  let changeReviewIdempotencyKey = "";

  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();

    if (method === "GET" && path === "/api/v1/me") return route.fulfill({ json: committeeMe });
    if (method === "GET" && path === "/api/v1/session-change-requests") return fulfill(route, changePending ? [changeQueueItem] : []);
    if (method === "GET" && path === "/api/v1/coach-cancellation-requests") return fulfill(route, cancellationPending ? [cancellationQueueItem] : []);
    if (method === "GET" && path === "/api/v1/course-offerings") return fulfill(route, emptyPage());
    if (method === "POST" && path === `/api/v1/session-change-requests/${changeQueueItem.requestId}/review`) {
      changeReview = request.postDataJSON() as { decision?: string; reason?: string };
      changeReviewIdempotencyKey = request.headers()["idempotency-key"] ?? "";
      changePending = false;
      return fulfill(route, { sessionId, status: "POSTPONED", scheduledStartAt: changeQueueItem.proposedStartAt, scheduledEndAt: changeQueueItem.proposedEndAt });
    }
    if (method === "POST" && path === `/api/v1/coach-cancellation-requests/${cancellationQueueItem.requestId}/review`) {
      cancellationReview = request.postDataJSON() as { decision?: string; reason?: string };
      cancellationPending = false;
      return fulfill(route, { requestId: cancellationQueueItem.requestId, sessionId, decision: cancellationReview.decision, status: "REJECTED" });
    }
    return route.abort();
  });

  await page.goto("http://127.0.0.1:4173");
  await expect(page.getByRole("heading", { name: "COMMITTEE entry" })).toBeVisible();
  const operations = page.getByRole("region", { name: "Committee course operations" });
  await expect(operations).toContainText("School event");
  await expect(operations).toContainText("Tournament duty");

  const changeRow = operations.getByRole("listitem").filter({ hasText: "School event" });
  await changeRow.getByRole("button", { name: "核准" }).click();
  const changeDialog = page.getByRole("dialog", { name: "確認課程異動審核" });
  await changeDialog.getByLabel("審核原因").fill("Court and coach confirmed");
  await changeDialog.getByRole("button", { name: "確認送出" }).click();
  await expect(operations).toContainText("沒有待審改期。");
  expect(changeReview).toEqual({ decision: "APPROVE", reason: "Court and coach confirmed" });
  expect(changeReviewIdempotencyKey).toContain(`liff-change-review-${changeQueueItem.requestId}-APPROVE`);

  const cancellationRow = operations.getByRole("listitem").filter({ hasText: "Tournament duty" });
  await cancellationRow.getByRole("button", { name: "駁回" }).click();
  const cancellationDialog = page.getByRole("dialog", { name: "確認課程異動審核" });
  await cancellationDialog.getByLabel("審核原因").fill("Replacement coach arranged");
  await cancellationDialog.getByRole("button", { name: "確認送出" }).click();
  await expect(operations).toContainText("沒有待審取消授課。");
  expect(cancellationReview).toEqual({ decision: "REJECT", reason: "Replacement coach arranged" });
});

test("committee Admin reviews a reschedule request and directly reschedules a formal session", async ({ page }) => {
  await withToken(page, "admin-token");
  let changePending = true;
  let sessionStart = baseSession.scheduledStartAt;
  let sessionEnd = baseSession.scheduledEndAt;
  let sessionStatus: "SCHEDULED" | "POSTPONED" = "SCHEDULED";
  let reviewBody: { decision?: string; reason?: string } = {};
  let reviewIdempotencyKey = "";
  let directBody: { reason?: string } = {};
  let directIdempotencyKey = "";

  const currentSession = () => ({ ...baseSession, scheduledStartAt: sessionStart, scheduledEndAt: sessionEnd, status: sessionStatus });

  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();

    if (method === "GET" && path === "/api/v1/me") return route.fulfill({ json: committeeMe });
    if (method === "GET" && path === "/api/v1/coach-applications") return fulfill(route, []);
    if (method === "GET" && path === "/api/v1/coach-availability-proposals") return fulfill(route, []);
    if (method === "GET" && path === "/api/v1/lesson-requests") return fulfill(route, []);
    if (method === "GET" && path === "/api/v1/course-matches") return fulfill(route, []);
    if (method === "GET" && path === "/api/v1/course-offerings") return fulfill(route, emptyPage());
    if (method === "GET" && path === "/api/v1/courses") return fulfill(route, coursePage());
    if (method === "GET" && path === "/api/v1/session-change-requests") return fulfill(route, changePending ? [changeQueueItem] : []);
    if (method === "GET" && path === "/api/v1/coach-cancellation-requests") return fulfill(route, []);
    if (method === "GET" && path === `/api/v1/courses/${courseId}/sessions`) return fulfill(route, [currentSession()]);
    if (method === "POST" && path === `/api/v1/session-change-requests/${changeQueueItem.requestId}/review`) {
      reviewBody = request.postDataJSON() as { decision?: string; reason?: string };
      reviewIdempotencyKey = request.headers()["idempotency-key"] ?? "";
      changePending = false;
      return fulfill(route, { sessionId, status: "POSTPONED", scheduledStartAt: changeQueueItem.proposedStartAt, scheduledEndAt: changeQueueItem.proposedEndAt });
    }
    if (method === "POST" && path === `/api/v1/course-sessions/${sessionId}/reschedule`) {
      directBody = request.postDataJSON() as { reason?: string; startAt?: string; endAt?: string };
      directIdempotencyKey = request.headers()["idempotency-key"] ?? "";
      sessionStart = directBody.startAt ?? "2026-09-05T02:00:00Z";
      sessionEnd = directBody.endAt ?? "2026-09-05T03:00:00Z";
      sessionStatus = "POSTPONED";
      return fulfill(route, { sessionId, status: sessionStatus, scheduledStartAt: sessionStart, scheduledEndAt: sessionEnd });
    }
    return route.abort();
  });

  await page.goto("http://127.0.0.1:4174");
  await expect(page.getByRole("heading", { name: "Authorized admin entry" })).toBeVisible();
  const operations = page.getByRole("region", { name: "Course operations" });
  await expect(operations.getByRole("heading", { name: "Course operations" })).toBeVisible();
  await expect(operations).toContainText("School event");

  const changeRow = operations.getByRole("listitem").filter({ hasText: "School event" });
  await changeRow.getByRole("button", { name: "Approve" }).click();
  const reviewDialog = page.getByRole("dialog", { name: "Confirm Course Operations review" });
  await reviewDialog.getByLabel("Decision reason").fill("Admin review passed");
  await reviewDialog.getByRole("button", { name: "Confirm review" }).click();
  await expect(operations.getByRole("status")).toContainText("Review saved");
  await expect(operations).toContainText("No pending reschedule requests.");
  expect(reviewBody).toEqual({ decision: "APPROVE", reason: "Admin review passed" });
  expect(reviewIdempotencyKey).toContain(`admin-change-review-${changeQueueItem.requestId}-APPROVE`);

  await operations.getByRole("button", { name: "Open course" }).click();
  const courseSessions = page.getByRole("region", { name: "Formal course sessions" });
  await expect(courseSessions).toContainText(course.courseNo);
  await courseSessions.getByLabel("New start").fill("2026-09-05T10:00");
  await courseSessions.getByLabel("New end").fill("2026-09-05T11:00");
  await courseSessions.getByLabel("Reason").fill("Court maintenance");
  await courseSessions.getByRole("button", { name: "Direct reschedule" }).click();
  const directDialog = page.getByRole("dialog", { name: "Confirm direct reschedule" });
  await expect(directDialog).toContainText("Court maintenance");
  await directDialog.getByRole("button", { name: "Confirm reschedule" }).click();
  await expect(operations.getByRole("status")).toContainText("reservations were shifted atomically");
  expect(directBody.reason).toBe("Court maintenance");
  expect(directIdempotencyKey).toContain(`admin-direct-reschedule-${sessionId}-`);
  await expect(courseSessions).toContainText("POSTPONED");
});
