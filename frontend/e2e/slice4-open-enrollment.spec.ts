import { expect, test, type Page, type Route } from "@playwright/test";

const meta = { requestId: "slice4-e2e" };
const envelope = (data: unknown) => ({ data, meta });
const committeeMe = envelope({
  id: "committee-user",
  displayName: "Committee",
  email: null,
  locale: "zh-TW",
  profileComplete: true,
  roles: [{ roleCode: "COMMITTEE", organizationId: "org", organizationCode: "MVP", organizationName: "MVP" }],
});
const studentMe = envelope({
  id: "student-user",
  displayName: "Student",
  email: null,
  locale: "zh-TW",
  profileComplete: true,
  roles: [{ roleCode: "STUDENT", organizationId: "org", organizationCode: "MVP", organizationName: "MVP" }],
});

async function fulfill(route: Route, data: unknown, status = 200) {
  await route.fulfill({ status, contentType: "application/json", body: JSON.stringify(envelope(data)) });
}

async function withToken(page: Page) {
  await page.addInitScript(() => sessionStorage.setItem("platform.access-token", "slice4-e2e-token"));
}

test("student LIFF browses, registers, refreshes capacity, and cancels open enrollment", async ({ page }) => {
  await withToken(page);
  let registrationStatus: "NONE" | "ACTIVE" | "CANCELLED" = "NONE";
  let registerIdempotencyKey = "";
  let cancelIdempotencyKey = "";

  const summary = () => ({
    id: "o1",
    organizationId: "org",
    title: "Beginner Group",
    status: "OPEN",
    coach: { coachProfileId: "cp1", userId: "coach-user", displayName: "Coach Lin" },
    scheduleType: "SINGLE",
    firstSessionAt: "2026-09-10T02:00:00Z",
    registrationOpenAt: "2026-08-20T00:00:00Z",
    registrationCloseAt: "2026-09-09T00:00:00Z",
    minimumParticipants: 2,
    maximumParticipants: 6,
    registeredCount: registrationStatus === "ACTIVE" ? 3 : 2,
    remainingCapacity: registrationStatus === "ACTIVE" ? 3 : 4,
    billingMode: "FULL_COURSE",
    skillLevel: "BEGINNER",
    priceSnapshotId: "ps1",
    pricePerParticipant: 1200,
    currency: "TWD",
    registrationState: registrationStatus === "ACTIVE" ? "REGISTERED" : "OPEN",
    ownRegistrationId: registrationStatus === "ACTIVE" ? "r1" : null,
    ownRegistrationStatus: registrationStatus === "NONE" ? null : registrationStatus,
    version: 2,
  });

  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (method === "GET" && path === "/api/v1/me") return route.fulfill({ json: studentMe });
    if (method === "GET" && path === "/api/v1/coach-availability-proposals/available") return fulfill(route, []);
    if (method === "GET" && path === "/api/v1/lesson-requests/mine") return fulfill(route, []);
    if (method === "GET" && path === "/api/v1/courses") return fulfill(route, { items: [], page: 0, size: 100, total: 0 });
    if (method === "GET" && path === "/api/v1/course-offerings") return fulfill(route, { items: [summary()], page: 0, size: 100, total: 1 });
    if (method === "GET" && path === "/api/v1/course-offerings/o1") return fulfill(route, {
      summary: summary(),
      description: "First group class",
      sessionPlans: [{ id: "os1", sequenceNo: 1, startAt: "2026-09-10T02:00:00Z", endAt: "2026-09-10T03:00:00Z", venueId: null, venueName: "Court A", venueAddress: "Taipei" }],
    });
    if (method === "GET" && path === "/api/v1/me/course-offering-registrations") return fulfill(route, {
      items: registrationStatus === "NONE" ? [] : [{
        id: "r1",
        offeringId: "o1",
        offeringTitle: "Beginner Group",
        offeringStatus: "OPEN",
        status: registrationStatus,
        registeredAt: "2026-08-25T03:00:00Z",
        cancelledAt: registrationStatus === "CANCELLED" ? "2026-08-25T04:00:00Z" : null,
        cancelReason: null,
        convertedCourseMembershipId: null,
        courseId: null,
      }],
      page: 0,
      size: 100,
      total: registrationStatus === "NONE" ? 0 : 1,
    });
    if (method === "POST" && path === "/api/v1/course-offerings/o1/registrations") {
      registerIdempotencyKey = request.headers()["idempotency-key"] ?? "";
      registrationStatus = "ACTIVE";
      return fulfill(route, { id: "r1", offeringId: "o1", status: "ACTIVE", registeredAt: "2026-08-25T03:00:00Z", cancelledAt: null, cancelReason: null, convertedCourseMembershipId: null }, 201);
    }
    if (method === "POST" && path === "/api/v1/course-offering-registrations/r1/cancellation") {
      cancelIdempotencyKey = request.headers()["idempotency-key"] ?? "";
      registrationStatus = "CANCELLED";
      return fulfill(route, { id: "r1", offeringId: "o1", status: "CANCELLED", registeredAt: "2026-08-25T03:00:00Z", cancelledAt: "2026-08-25T04:00:00Z", cancelReason: null, convertedCourseMembershipId: null });
    }
    return route.abort();
  });

  await page.goto("http://127.0.0.1:4173");
  await expect(page.getByRole("heading", { name: "學員首頁" })).toBeVisible();
  await page.getByRole("button", { name: "找課與需求" }).click();
  await expect(page.getByText(/Beginner Group/).first()).toBeVisible();

  await page.getByRole("button", { name: "查看課程" }).click();
  await expect(page.getByRole("heading", { name: "Beginner Group", level: 4 })).toBeVisible();
  await expect(page.getByText("教練：Coach Lin")).toBeVisible();
  await expect(page.getByText("費用：TWD 1200")).toBeVisible();

  await page.getByRole("button", { name: "立即報名" }).click();
  await expect(page.getByRole("status")).toContainText("報名成功");
  await expect.poll(() => registrationStatus).toBe("ACTIVE");
  expect(registerIdempotencyKey).toContain("offering-register-o1-");
  await expect(page.getByText(/剩餘 3 名/).first()).toBeVisible();

  await page.getByRole("button", { name: "取消報名" }).click();
  await expect(page.getByRole("status")).toContainText("已取消報名並釋放保留時段");
  await expect.poll(() => registrationStatus).toBe("CANCELLED");
  expect(cancelIdempotencyKey).toContain("offering-registration-cancel-r1");
});

test("committee Admin creates, prices, publishes, closes, and forms an open enrollment course", async ({ page }) => {
  await withToken(page);
  let created = false;
  let status: "DRAFT" | "OPEN" | "CLOSED" | "CONFIRMED" = "DRAFT";
  let priceConfirmed = false;
  const idempotencyKeys: string[] = [];

  const summary = () => ({
    id: "o-admin",
    organizationId: "org",
    title: "Weekend Group",
    status,
    coach: { coachProfileId: "cp1", userId: "coach-user", displayName: "Coach Lin" },
    scheduleType: "SINGLE",
    firstSessionAt: "2026-09-12T02:00:00Z",
    registrationOpenAt: "2026-08-26T00:00:00Z",
    registrationCloseAt: "2026-09-11T00:00:00Z",
    minimumParticipants: 2,
    maximumParticipants: 6,
    registeredCount: status === "DRAFT" ? 0 : 2,
    remainingCapacity: status === "DRAFT" ? 6 : 4,
    billingMode: "FULL_COURSE",
    skillLevel: "BEGINNER",
    priceSnapshotId: priceConfirmed ? "ps-admin" : null,
    pricePerParticipant: priceConfirmed ? 1500 : null,
    currency: priceConfirmed ? "TWD" : null,
    registrationState: status === "OPEN" ? "OPEN" : "CLOSED",
    ownRegistrationId: null,
    ownRegistrationStatus: null,
    version: status === "DRAFT" ? 1 : status === "OPEN" ? 2 : status === "CLOSED" ? 3 : 4,
  });
  const detail = () => ({
    summary: summary(),
    description: "Weekend beginner class",
    sessionPlans: [{ id: "os-admin", sequenceNo: 1, startAt: "2026-09-12T02:00:00Z", endAt: "2026-09-12T03:00:00Z", venueId: null, venueName: "Court A", venueAddress: "Taipei" }],
  });

  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (method === "GET" && path === "/api/v1/me") return route.fulfill({ json: committeeMe });
    if (method === "GET" && path === "/api/v1/coach-applications") return fulfill(route, [{ id: "ca1", coachProfileId: "cp1", status: "APPROVED", applicationNote: null, submittedAt: "2026-08-20T00:00:00Z", reviewedBy: "committee-user", reviewedAt: "2026-08-20T01:00:00Z", reviewNote: "approved" }]);
    if (method === "GET" && path === "/api/v1/coach-availability-proposals") return fulfill(route, []);
    if (method === "GET" && path === "/api/v1/lesson-requests") return fulfill(route, []);
    if (method === "GET" && path === "/api/v1/course-matches") return fulfill(route, []);
    if (method === "GET" && path === "/api/v1/courses") return fulfill(route, { items: [], page: 0, size: 100, total: 0 });
    if (method === "GET" && path === "/api/v1/session-change-requests") return fulfill(route, []);
    if (method === "GET" && path === "/api/v1/coach-cancellation-requests") return fulfill(route, []);
    if (method === "GET" && path === "/api/v1/course-offerings") return fulfill(route, { items: created ? [summary()] : [], page: 0, size: 100, total: created ? 1 : 0 });
    if (method === "POST" && path === "/api/v1/course-offerings") {
      created = true;
      status = "DRAFT";
      return fulfill(route, detail(), 201);
    }
    if (method === "GET" && path === "/api/v1/course-offerings/o-admin") return fulfill(route, detail());
    if (method === "GET" && path === "/api/v1/course-offerings/o-admin/registrations") return fulfill(route, {
      items: status === "DRAFT" ? [] : [
        { id: "r1", userId: "student-1", displayName: "Student One", status: "ACTIVE", registeredAt: "2026-08-27T01:00:00Z", cancelledAt: null, cancelReason: null, convertedCourseMembershipId: null, scheduleConflictIndicator: false },
        { id: "r2", userId: "student-2", displayName: "Student Two", status: "ACTIVE", registeredAt: "2026-08-27T01:05:00Z", cancelledAt: null, cancelReason: null, convertedCourseMembershipId: null, scheduleConflictIndicator: false },
      ],
      page: 0,
      size: 100,
      total: status === "DRAFT" ? 0 : 2,
    });
    if (method === "POST" && path === "/api/v1/course-offerings/o-admin/pricing-preview") return fulfill(route, { offeringId: "o-admin", currency: "TWD", pricePerParticipant: "1500.00", billingMode: "FULL_COURSE", sessionCount: 1, pricingFingerprint: "a".repeat(64) });
    if (method === "POST" && path === "/api/v1/course-offerings/o-admin/pricing-confirmation") {
      idempotencyKeys.push(request.headers()["idempotency-key"] ?? "");
      priceConfirmed = true;
      return fulfill(route, { priceSnapshotId: "ps-admin", offeringId: "o-admin", status: "CONFIRMED", currency: "TWD", pricePerParticipant: "1500.00", pricingFingerprint: "a".repeat(64), confirmedBy: "committee-user", confirmedAt: "2026-08-25T05:00:00Z" }, 201);
    }
    if (method === "POST" && path === "/api/v1/course-offerings/o-admin/publication") {
      idempotencyKeys.push(request.headers()["idempotency-key"] ?? "");
      status = "OPEN";
      return fulfill(route, detail());
    }
    if (method === "POST" && path === "/api/v1/course-offerings/o-admin/closure") {
      idempotencyKeys.push(request.headers()["idempotency-key"] ?? "");
      status = "CLOSED";
      return fulfill(route, detail());
    }
    if (method === "POST" && path === "/api/v1/course-offerings/o-admin/confirmation") {
      idempotencyKeys.push(request.headers()["idempotency-key"] ?? "");
      status = "CONFIRMED";
      return fulfill(route, { offeringId: "o-admin", offeringStatus: "CONFIRMED", courseId: "course-1", courseStatus: "ACTIVE", sessionIds: ["course-session-1"], receivableIds: ["recv-1", "recv-2"] }, 201);
    }
    return route.abort();
  });

  await page.goto("http://127.0.0.1:4174");
  await expect(page.getByRole("heading", { name: "Authorized admin entry" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Open enrollment" })).toBeVisible();

  await page.getByLabel("Title").fill("Weekend Group");
  await page.getByLabel("Description").fill("Weekend beginner class");
  await page.getByLabel("Coach").selectOption("cp1");
  await page.getByLabel("Skill level").fill("BEGINNER");
  await page.getByLabel("Registration opens").fill("2026-08-26T08:00");
  await page.getByLabel("Registration closes").fill("2026-09-11T08:00");
  await page.getByLabel("Start").fill("2026-09-12T10:00");
  await page.getByLabel("End").fill("2026-09-12T11:00");
  await page.getByLabel("Venue name").fill("Court A");
  await page.getByLabel("Venue address").fill("Taipei");
  await page.getByRole("button", { name: "Create offering draft" }).click();

  await expect(page.getByRole("heading", { name: "Weekend Group", level: 3 })).toBeVisible();
  await expect(page.getByText("Status: DRAFT")).toBeVisible();
  await page.getByLabel("Price per participant").fill("1500");
  await page.getByRole("button", { name: "Preview offering price" }).click();
  await expect(page.getByLabel("Offering pricing preview")).toContainText("TWD 1500.00 per participant");
  await page.getByRole("button", { name: "Confirm offering price" }).click();
  await expect(page.getByText("Offering price confirmed.")).toBeVisible();

  await page.getByRole("button", { name: "Publish offering" }).click();
  await expect(page.getByText("Offering published and registration is available during the configured window.")).toBeVisible();
  await expect(page.getByText("Status: OPEN")).toBeVisible();

  await page.getByRole("button", { name: "Close registration" }).click();
  await expect(page.getByText("Offering registration closed.")).toBeVisible();
  await expect(page.getByText("Status: CLOSED")).toBeVisible();
  await expect(page.getByText(/Student One · ACTIVE/)).toBeVisible();

  await page.getByRole("button", { name: "Form course from offering" }).click();
  await expect(page.getByRole("dialog", { name: "Confirm offering formation" })).toBeVisible();
  await page.getByRole("button", { name: "Confirm formation" }).click();
  await expect(page.getByRole("status")).toContainText("Course formed from offering: course-1");
  await expect(page.getByText("Status: CONFIRMED")).toBeVisible();

  expect(idempotencyKeys).toHaveLength(4);
  for (const key of idempotencyKeys) expect(key).not.toBe("");
});
