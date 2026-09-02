import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { App } from "./App";

const readiness = (ready: boolean) => ({ lessonRequestApproved: true, coachesAccepted: true, sessionsFuture: true, scheduleConflictFree: true, venueReady: true, pricingConfirmed: ready, participantCountValid: true, readyToConfirm: ready });
const server = setupServer(
  http.get("/api/v1/coach-applications", () => HttpResponse.json({ data: [], meta: { requestId: "test" } })),
  http.get("/api/v1/coach-availability-proposals", () => HttpResponse.json({ data: [], meta: { requestId: "test" } })),
  http.get("/api/v1/lesson-requests", () => HttpResponse.json({ data: [], meta: { requestId: "test" } })),
  http.get("/api/v1/course-matches", () => HttpResponse.json({ data: [], meta: { requestId: "test" } })),
  http.get("/api/v1/course-offerings", () => HttpResponse.json({ data: { items: [], page: 0, size: 100, total: 0 }, meta: { requestId: "test" } })),
  http.get("/api/v1/courses", () => HttpResponse.json({ data: { items: [], page: 0, size: 100, total: 0 }, meta: { requestId: "test" } })),
  http.get("/api/v1/session-change-requests", () => HttpResponse.json({ data: [], meta: { requestId: "test" } })),
  http.get("/api/v1/coach-cancellation-requests", () => HttpResponse.json({ data: [], meta: { requestId: "test" } })),
  http.get("/api/v1/admin/outbox-events", () => HttpResponse.json({ data: { items: [], page: 0, size: 50, totalElements: 0 }, meta: { requestId: "test" } })),
  http.get("/api/v1/admin/notifications", () => HttpResponse.json({ data: { items: [], page: 0, size: 50, totalElements: 0 }, meta: { requestId: "test" } }))
);
beforeAll(() => server.listen({ onUnhandledRequest: "error" })); afterEach(() => { cleanup(); server.resetHandlers(); sessionStorage.clear(); }); afterAll(() => server.close());

describe("admin authorization, Slice 3 matching, and Slice 4 open enrollment", () => {
  it("allows committee users", async () => {
    sessionStorage.setItem("platform.access-token", "token");
    server.use(http.get("/api/v1/me", () => HttpResponse.json({ data: { id: "u", displayName: "Committee", email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "COMMITTEE", organizationId: "o", organizationCode: "MVP", organizationName: "MVP" }] }, meta: { requestId: "test" } })));
    render(<App />); expect(await screen.findByRole("heading", { name: "管理後台" })).toBeTruthy(); expect(await screen.findByRole("navigation", { name: "管理後台導覽" })).toBeTruthy(); expect(await screen.findByRole("heading", { name: "Course matching" })).toBeTruthy(); expect(await screen.findByRole("heading", { name: "Open enrollment" })).toBeTruthy(); expect(await screen.findByRole("heading", { name: "Course operations" })).toBeTruthy();
  });

  it("denies non-admin roles", async () => {
    sessionStorage.setItem("platform.access-token", "token");
    server.use(http.get("/api/v1/me", () => HttpResponse.json({ data: { id: "u", displayName: "Student", email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "STUDENT", organizationId: "o", organizationCode: "MVP", organizationName: "MVP" }] }, meta: { requestId: "test" } })));
    render(<App />); expect(await screen.findByRole("alert")).toBeTruthy();
  });

  it("keeps committee scope fixed and performs an audited eligible recovery", async () => {
    sessionStorage.setItem("platform.access-token", "token");
    let recovered = false;
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json({ data: { id: "u", displayName: "Committee", email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "COMMITTEE", organizationId: "org-1", organizationCode: "MVP", organizationName: "MVP" }] }, meta: { requestId: "test" } })),
      http.get("/api/v1/admin/outbox-events", ({ request }) => {
        expect(new URL(request.url).searchParams.get("organizationId")).toBe("org-1");
        return HttpResponse.json({ data: { items: recovered ? [] : [{ id: "event-1", organizationId: "org-1", aggregateType: "Course", aggregateId: "course-1", eventType: "CourseChanged", status: "FAILED", attemptCount: 2, availableAt: "2026-08-29T00:00:00Z", processedAt: null, lastError: "dependency timeout", createdAt: "2026-08-29T00:00:00Z" }], page: 0, size: 50, totalElements: recovered ? 0 : 1 }, meta: { requestId: "test" } });
      }),
      http.post("/api/v1/admin/outbox-events/event-1/retry", async ({ request }) => {
        expect(request.headers.get("Idempotency-Key")).toMatch(/^admin-recovery-event-1-/);
        expect(await request.json()).toEqual({ reason: "dependency restored" });
        recovered = true;
        return HttpResponse.json({ data: { id: "event-1", organizationId: "org-1", aggregateType: "Course", aggregateId: "course-1", eventType: "CourseChanged", status: "PENDING", attemptCount: 2, availableAt: "2026-08-29T00:01:00Z", processedAt: null, lastError: null, createdAt: "2026-08-29T00:00:00Z" }, meta: { requestId: "test" } });
      })
    );
    render(<App />);
    expect(await screen.findByText("dependency timeout")).toBeTruthy();
    expect(screen.queryByLabelText("Organization ID")).toBeNull();
    fireEvent.change(screen.getByLabelText("Audit reason"), { target: { value: "dependency restored" } });
    fireEvent.click(screen.getByRole("button", { name: "Retry event" }));
    expect(await screen.findByText("Recovery request accepted and audited.")).toBeTruthy();
    await waitFor(() => expect(recovered).toBe(true));
  });

  it("requires a platform administrator to select an explicit organization scope", async () => {
    sessionStorage.setItem("platform.access-token", "token");
    server.use(http.get("/api/v1/me", () => HttpResponse.json({ data: { id: "pa", displayName: "Platform Admin", email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "PLATFORM_ADMIN", organizationId: null, organizationCode: null, organizationName: null }] }, meta: { requestId: "test" } })));
    render(<App />);
    const input = await screen.findByLabelText("Organization ID");
    expect(screen.getByRole("button", { name: "Refresh operations" }).hasAttribute("disabled")).toBe(true);
    fireEvent.change(input, { target: { value: "org-selected" } });
    expect(screen.getByRole("button", { name: "Refresh operations" }).hasAttribute("disabled")).toBe(false);
  });

  it("previews and confirms pricing before secondary course formation confirmation", async () => {
    sessionStorage.setItem("platform.access-token", "token");
    let pricingConfirmed = false; let formed = false;
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json({ data: { id: "u", displayName: "Committee", email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "COMMITTEE", organizationId: "o", organizationCode: "MVP", organizationName: "MVP" }] }, meta: { requestId: "test" } })),
      http.get("/api/v1/course-matches", () => HttpResponse.json({ data: [{ id: "m1", lessonRequestId: "l1", status: formed ? "CONFIRMED" : "DRAFT", participantCount: 2, version: 1, createdAt: "2026-08-24T10:00:00Z", readiness: readiness(pricingConfirmed), pricing: { status: pricingConfirmed ? "CONFIRMED" : "NOT_CONFIRMED", priceSnapshotId: pricingConfirmed ? "p1" : null } }], meta: { requestId: "test" } })),
      http.get("/api/v1/course-matches/m1", () => HttpResponse.json({ data: { id: "m1", lessonRequestId: "l1", status: formed ? "CONFIRMED" : "DRAFT", participantCount: 2, minimumParticipants: 1, maximumParticipants: 4, version: 1, sessions: [{ id: "s1", sequenceNo: 1, startAt: "2026-09-01T02:00:00Z", endAt: "2026-09-01T03:00:00Z", venueType: "OTHER", venueId: null, venueName: "Court A", venueAddress: "Taipei" }], coachInvitations: [{ invitationId: "i1", courseMatchSessionId: "s1", sessionIndex: 1, coachProfileId: "cp1", assignmentOrder: 1, status: "ACCEPTED", invitationSentAt: "2026-08-24T10:00:00Z", respondedAt: "2026-08-24T11:00:00Z", responseNote: "ok" }], readiness: readiness(pricingConfirmed), pricing: { status: pricingConfirmed ? "CONFIRMED" : "NOT_CONFIRMED", priceSnapshotId: pricingConfirmed ? "p1" : null } }, meta: { requestId: "test" } })),
      http.post("/api/v1/course-matches/m1/pricing-preview", () => HttpResponse.json({ data: { courseMatchId: "m1", currency: "TWD", billingMode: "FULL_COURSE", totalAmount: "1800.00", breakdown: [{ courseMatchSessionId: "s1", itemType: "TUITION", description: "Tuition", quantity: "1", unitAmount: "1800.00", lineAmount: "1800.00", sourceReferenceType: null, sourceReferenceId: null }], pricingFingerprint: "a".repeat(64) }, meta: { requestId: "test" } })),
      http.post("/api/v1/course-matches/m1/pricing-confirmation", () => { pricingConfirmed = true; return HttpResponse.json({ data: { priceSnapshotId: "p1", courseMatchId: "m1", status: "CONFIRMED", billingMode: "FULL_COURSE", totalAmount: "1800.00", currency: "TWD", pricingFingerprint: "a".repeat(64), confirmedBy: "u", confirmedAt: "2026-08-24T12:00:00Z" }, meta: { requestId: "test" } }, { status: 201 }); }),
      http.post("/api/v1/course-matches/m1/confirmation", () => { formed = true; return HttpResponse.json({ data: { courseMatchId: "m1", courseMatchStatus: "CONFIRMED", courseId: "c1", courseStatus: "ACTIVE", sessionIds: ["cs1"], receivableIds: ["r1"] }, meta: { requestId: "test" } }, { status: 201 }); })
    );
    render(<App />);
    fireEvent.click(await screen.findByRole("button", { name: "Open match" }));
    fireEvent.click(await screen.findByRole("button", { name: "Preview pricing" }));
    expect(await screen.findByText("TWD 1800.00")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Confirm this price" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Form course" }).hasAttribute("disabled")).toBe(false));
    fireEvent.click(screen.getByRole("button", { name: "Form course" }));
    expect(await screen.findByRole("dialog", { name: "Confirm course formation" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Confirm formation" }));
    await waitFor(() => expect(formed).toBe(true));
    expect(await screen.findByText("Course formed: c1")).toBeTruthy();
  });

  it("runs the committee offering pricing, publish, close, and formation workflow", async () => {
    sessionStorage.setItem("platform.access-token", "token");
    let status: "DRAFT" | "OPEN" | "CLOSED" | "CONFIRMED" = "DRAFT";
    let priceConfirmed = false;
    let formed = false;
    const summary = () => ({ id: "o1", organizationId: "o", title: "Weekend Beginners", status, coach: { coachProfileId: "cp1", userId: "coach-user", displayName: "Coach Lin" }, scheduleType: "SINGLE", firstSessionAt: "2026-09-12T02:00:00Z", registrationOpenAt: "2026-08-26T00:00:00Z", registrationCloseAt: "2026-09-10T00:00:00Z", minimumParticipants: 2, maximumParticipants: 6, registeredCount: 3, remainingCapacity: 3, billingMode: "FULL_COURSE", skillLevel: "BEGINNER", priceSnapshotId: priceConfirmed ? "ps1" : null, pricePerParticipant: priceConfirmed ? 1200 : null, currency: priceConfirmed ? "TWD" : null, registrationState: status === "OPEN" ? "OPEN" : status === "DRAFT" ? "NOT_OPEN" : "CLOSED", ownRegistrationId: null, ownRegistrationStatus: null, version: status === "DRAFT" ? 1 : status === "OPEN" ? 2 : 3 });
    const detail = () => ({ summary: summary(), description: "Weekend class", sessionPlans: [{ id: "os1", sequenceNo: 1, startAt: "2026-09-12T02:00:00Z", endAt: "2026-09-12T03:00:00Z", venueId: null, venueName: "Court A", venueAddress: "Taipei" }] });
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json({ data: { id: "u", displayName: "Committee", email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "COMMITTEE", organizationId: "o", organizationCode: "MVP", organizationName: "MVP" }] }, meta: { requestId: "test" } })),
      http.get("/api/v1/coach-applications", () => HttpResponse.json({ data: [{ id: "ca1", coachProfileId: "cp1", status: "APPROVED", applicationNote: null, submittedAt: "2026-08-01T00:00:00Z", reviewedBy: "u", reviewedAt: "2026-08-02T00:00:00Z", reviewNote: "approved" }], meta: { requestId: "test" } })),
      http.get("/api/v1/course-offerings", () => HttpResponse.json({ data: { items: [summary()], page: 0, size: 100, total: 1 }, meta: { requestId: "test" } })),
      http.get("/api/v1/course-offerings/o1", () => HttpResponse.json({ data: detail(), meta: { requestId: "test" } })),
      http.get("/api/v1/course-offerings/o1/registrations", () => HttpResponse.json({ data: { items: [{ id: "r1", userId: "s1", displayName: "Student One", status: "ACTIVE", registeredAt: "2026-08-27T01:00:00Z", cancelledAt: null, cancelReason: null, scheduleConflictIndicator: false, convertedCourseMembershipId: null, courseId: null }], page: 0, size: 100, total: 1 }, meta: { requestId: "test" } })),
      http.post("/api/v1/course-offerings/o1/pricing-preview", async ({ request }) => { const body = await request.json() as { currency: string; pricePerParticipant: number }; return HttpResponse.json({ data: { offeringId: "o1", currency: body.currency, pricePerParticipant: body.pricePerParticipant.toFixed(2), billingMode: "FULL_COURSE", sessionCount: 1, pricingFingerprint: "b".repeat(64) }, meta: { requestId: "test" } }); }),
      http.post("/api/v1/course-offerings/o1/pricing-confirmation", () => { priceConfirmed = true; return HttpResponse.json({ data: { priceSnapshotId: "ps1", offeringId: "o1", status: "CONFIRMED", currency: "TWD", pricePerParticipant: "1200.00", pricingFingerprint: "b".repeat(64), confirmedBy: "u", confirmedAt: "2026-08-25T04:00:00Z" }, meta: { requestId: "test" } }, { status: 201 }); }),
      http.post("/api/v1/course-offerings/o1/publication", () => { status = "OPEN"; return HttpResponse.json({ data: detail(), meta: { requestId: "test" } }); }),
      http.post("/api/v1/course-offerings/o1/closure", () => { status = "CLOSED"; return HttpResponse.json({ data: detail(), meta: { requestId: "test" } }); }),
      http.post("/api/v1/course-offerings/o1/confirmation", () => { status = "CONFIRMED"; formed = true; return HttpResponse.json({ data: { offeringId: "o1", offeringStatus: "CONFIRMED", courseId: "course-1", courseStatus: "ACTIVE", sessionIds: ["cs1"], membershipIds: ["m1", "m2", "m3"], enrollmentIds: ["e1", "e2", "e3"], receivableIds: ["rv1", "rv2", "rv3"] }, meta: { requestId: "test" } }, { status: 201 }); })
    );
    render(<App />);
    fireEvent.click(await screen.findByRole("button", { name: "Open offering" }));
    fireEvent.change(await screen.findByLabelText("Price per participant"), { target: { value: "1200" } });
    fireEvent.click(screen.getByRole("button", { name: "Preview offering price" }));
    expect(await screen.findByText(/TWD 1200\.00 per participant/)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Confirm offering price" }));
    await waitFor(() => expect(priceConfirmed).toBe(true));
    await waitFor(() => expect(screen.getByRole("button", { name: "Publish offering" }).hasAttribute("disabled")).toBe(false));
    fireEvent.click(screen.getByRole("button", { name: "Publish offering" }));
    expect(await screen.findByRole("button", { name: "Close registration" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Close registration" }));
    expect(await screen.findByRole("button", { name: "Form course from offering" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Form course from offering" }));
    expect(await screen.findByRole("dialog", { name: "Confirm offering formation" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Confirm formation" }));
    await waitFor(() => expect(formed).toBe(true));
    expect(await screen.findByText("Course formed from offering: course-1")).toBeTruthy();
  });
});
