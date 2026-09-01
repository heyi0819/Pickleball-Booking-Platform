import { expect, test } from "@playwright/test";

const envelope = (data: unknown) => ({ data, meta: { requestId: "slice9-e2e" } });
const emptyPage = () => ({ items: [], page: 0, size: 100, totalElements: 0 });

test("committee prices a ready match and confirms formal course creation", async ({ page }) => {
  const matchId = "11111111-1111-1111-1111-111111111111";
  const fingerprint = "a".repeat(64);
  let pricingConfirmed = false;
  let formed = false;
  const idempotencyKeys: string[] = [];

  const readiness = () => ({
    lessonRequestApproved: true,
    coachesAccepted: true,
    sessionsFuture: true,
    scheduleConflictFree: true,
    venueReady: true,
    pricingConfirmed,
    participantCountValid: true,
    readyToConfirm: pricingConfirmed,
    blockingReasons: pricingConfirmed ? [] : ["PRICING_NOT_CONFIRMED"],
  });
  const summary = () => ({
    id: matchId,
    lessonRequestId: "22222222-2222-2222-2222-222222222222",
    status: formed ? "CONFIRMED" : "DRAFT",
    participantCount: 2,
    version: 1,
    createdAt: "2026-08-30T00:00:00Z",
    readiness: readiness(),
    pricing: { status: pricingConfirmed ? "CONFIRMED" : "NOT_CONFIRMED", priceSnapshotId: pricingConfirmed ? "price-1" : null },
  });
  const detail = () => ({
    ...summary(),
    minimumParticipants: 1,
    maximumParticipants: 4,
    sessions: [{ id: "session-plan-1", sequenceNo: 1, startAt: "2026-09-15T02:00:00Z", endAt: "2026-09-15T03:00:00Z", venueType: "OTHER", venueId: null, venueName: "Court A", venueAddress: "Taipei" }],
    coachInvitations: [{ invitationId: "invite-1", courseMatchSessionId: "session-plan-1", sessionIndex: 1, coachProfileId: "coach-1", assignmentOrder: 1, status: "ACCEPTED", invitationSentAt: "2026-08-29T00:00:00Z", respondedAt: "2026-08-29T01:00:00Z", responseNote: "accepted" }],
  });

  await page.addInitScript(() => sessionStorage.setItem("platform.access-token", "slice9-token"));
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();

    if (method === "GET" && path === "/api/v1/me") {
      return route.fulfill({ json: envelope({ id: "committee-user", displayName: "Committee", email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "COMMITTEE", organizationId: "org-1", organizationCode: "MVP", organizationName: "MVP" }] }) });
    }
    if (method === "GET" && path === "/api/v1/course-matches") return route.fulfill({ json: envelope([summary()]) });
    if (method === "GET" && path === `/api/v1/course-matches/${matchId}`) return route.fulfill({ json: envelope(detail()) });
    if (method === "POST" && path === `/api/v1/course-matches/${matchId}/pricing-preview`) {
      return route.fulfill({ json: envelope({ courseMatchId: matchId, currency: "TWD", billingMode: "FULL_COURSE", totalAmount: "1800.00", breakdown: [{ courseMatchSessionId: "session-plan-1", itemType: "TUITION", description: "Tuition", quantity: "1", unitAmount: "1800.00", lineAmount: "1800.00", sourceReferenceType: null, sourceReferenceId: null }], pricingFingerprint: fingerprint }) });
    }
    if (method === "POST" && path === `/api/v1/course-matches/${matchId}/pricing-confirmation`) {
      idempotencyKeys.push(request.headers()["idempotency-key"] ?? "");
      expect(request.postDataJSON()).toMatchObject({ acceptedTotalAmount: "1800.00", currency: "TWD", pricingFingerprint: fingerprint });
      pricingConfirmed = true;
      return route.fulfill({ status: 201, json: envelope({ priceSnapshotId: "price-1", courseMatchId: matchId, status: "CONFIRMED", billingMode: "FULL_COURSE", totalAmount: "1800.00", currency: "TWD", pricingFingerprint: fingerprint, confirmedBy: "committee-user", confirmedAt: "2026-08-30T01:00:00Z" }) });
    }
    if (method === "POST" && path === `/api/v1/course-matches/${matchId}/confirmation`) {
      idempotencyKeys.push(request.headers()["idempotency-key"] ?? "");
      formed = true;
      return route.fulfill({ status: 201, json: envelope({ courseMatchId: matchId, courseMatchStatus: "CONFIRMED", courseId: "course-1", courseStatus: "ACTIVE", sessionIds: ["session-1"], receivableIds: ["receivable-1"] }) });
    }
    if (method === "GET" && path === "/api/v1/course-offerings") return route.fulfill({ json: envelope(emptyPage()) });
    if (method === "GET" && path === "/api/v1/courses") return route.fulfill({ json: envelope(emptyPage()) });
    if (method === "GET" && path.startsWith("/api/v1/admin/")) return route.fulfill({ json: envelope({ items: [], page: 0, size: 50, totalElements: 0 }) });
    if (method === "GET") return route.fulfill({ json: envelope([]) });
    return route.abort();
  });

  await page.goto("http://127.0.0.1:4174");
  const matching = page.getByRole("region", { name: "Course matching" });
  await matching.getByRole("button", { name: "Open match" }).click();
  await expect(matching).toContainText("Pricing confirmed");
  await matching.getByRole("button", { name: "Preview pricing" }).click();
  await expect(matching).toContainText("TWD 1800.00");
  await matching.getByRole("button", { name: "Confirm this price" }).click();
  await expect(matching.getByRole("button", { name: "Form course" })).toBeEnabled();
  await matching.getByRole("button", { name: "Form course" }).click();
  await page.getByRole("dialog", { name: "Confirm course formation" }).getByRole("button", { name: "Confirm formation" }).click();

  await expect(matching.getByRole("status")).toContainText("Course formed: course-1");
  expect(formed).toBe(true);
  expect(idempotencyKeys).toHaveLength(2);
  expect(idempotencyKeys[0]).toBe(`match-price-${matchId}-${fingerprint}`);
  expect(idempotencyKeys[1]).toBe(`match-formation-${matchId}-1`);
});
