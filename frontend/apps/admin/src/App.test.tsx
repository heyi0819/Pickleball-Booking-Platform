import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { App } from "./App";

const readiness = (ready: boolean) => ({ lessonRequestApproved: true, coachesAccepted: true, sessionsFuture: true, scheduleConflictFree: true, venueReady: true, pricingConfirmed: ready, participantCountValid: true, readyToConfirm: ready });
const server = setupServer(
  http.get("/api/v1/coach-applications", () => HttpResponse.json({ data: [], meta: { requestId: "test" } })),
  http.get("/api/v1/coach-availability-proposals", () => HttpResponse.json({ data: [], meta: { requestId: "test" } })),
  http.get("/api/v1/lesson-requests", () => HttpResponse.json({ data: [], meta: { requestId: "test" } })),
  http.get("/api/v1/course-matches", () => HttpResponse.json({ data: [], meta: { requestId: "test" } }))
);
beforeAll(() => server.listen({ onUnhandledRequest: "error" })); afterEach(() => { server.resetHandlers(); sessionStorage.clear(); }); afterAll(() => server.close());

describe("admin authorization and Slice 3 matching", () => {
  it("allows committee users", async () => {
    sessionStorage.setItem("platform.access-token", "token");
    server.use(http.get("/api/v1/me", () => HttpResponse.json({ data: { id: "u", displayName: "Committee", phone: null, email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "COMMITTEE", organizationId: "o", organizationCode: "MVP", organizationName: "MVP" }] }, meta: { requestId: "test" } })));
    render(<App />); expect(await screen.findByRole("heading", { name: "Authorized admin entry" })).toBeTruthy(); expect(await screen.findByRole("heading", { name: "Course matching" })).toBeTruthy();
  });

  it("denies non-admin roles", async () => {
    sessionStorage.setItem("platform.access-token", "token");
    server.use(http.get("/api/v1/me", () => HttpResponse.json({ data: { id: "u", displayName: "Student", phone: null, email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "STUDENT", organizationId: "o", organizationCode: "MVP", organizationName: "MVP" }] }, meta: { requestId: "test" } })));
    render(<App />); expect(await screen.findByRole("alert")).toBeTruthy();
  });

  it("previews and confirms pricing before secondary course formation confirmation", async () => {
    sessionStorage.setItem("platform.access-token", "token");
    let pricingConfirmed = false; let formed = false;
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json({ data: { id: "u", displayName: "Committee", phone: null, email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "COMMITTEE", organizationId: "o", organizationCode: "MVP", organizationName: "MVP" }] }, meta: { requestId: "test" } })),
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
});
