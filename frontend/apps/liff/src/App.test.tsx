import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
const liff = vi.hoisted(() => ({ init: vi.fn(async () => undefined), isLoggedIn: vi.fn(() => true), login: vi.fn(), getIDToken: vi.fn(() => "line-id-token") }));
vi.mock("@line/liff", () => ({ default: liff }));
import { App, BOOTSTRAP_TIMEOUT_MS } from "./App";
const member = { id: "00000000-0000-0000-0000-000000000001", displayName: "Test member", email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "STUDENT", organizationId: "org", organizationCode: "MVP", organizationName: "MVP" }, { roleCode: "COACH", organizationId: "org", organizationCode: "MVP", organizationName: "MVP" }] };
const server = setupServer(
  http.post("/api/v1/auth/line/login", () => HttpResponse.json({ data: { accessToken: "token", tokenType: "Bearer", expiresIn: 1800, user: { id: member.id, displayName: member.displayName, roles: [] } }, meta: { requestId: "test" } })),
  http.get("/api/v1/me", () => HttpResponse.json({ data: member, meta: { requestId: "test"} })),
  http.get("/api/v1/coach-availability-proposals/mine", () => HttpResponse.json({ data: [], meta: { requestId: "test" } })),
  http.get("/api/v1/course-match-invitations/mine", () => HttpResponse.json({ data: [], meta: { requestId: "test" } })),
  http.get("/api/v1/courses", () => HttpResponse.json({ data: { items: [], page: 0, size: 100, total: 0 }, meta: { requestId: "test" } }))
);
beforeAll(() => server.listen({ onUnhandledRequest: "error" })); beforeEach(() => { vi.stubEnv("VITE_LIFF_ID", "test-liff"); liff.init.mockResolvedValue(undefined); liff.isLoggedIn.mockReturnValue(true); liff.getIDToken.mockReturnValue("line-id-token"); }); afterEach(() => { cleanup(); server.resetHandlers(); sessionStorage.clear(); vi.restoreAllMocks(); vi.clearAllMocks(); vi.unstubAllEnvs(); vi.useRealTimers(); }); afterAll(() => server.close());

describe("LIFF authentication, role, Slice 3 coach flow, and Slice 4 enrollment", () => {
  it("logs in with LIFF without optional contact data and selects a role", async () => {
    render(<App />); expect(await screen.findByRole("heading", { name: "Select your role" })).toBeTruthy();
    expect(screen.queryByRole("heading", { name: "Complete your profile" })).toBeNull(); expect(screen.queryByLabelText("Phone")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "COACH" })); expect(await screen.findByRole("heading", { name: "COACH entry" })).toBeTruthy(); await waitFor(() => expect(liff.getIDToken).toHaveBeenCalled());
  });

  it("shows a recoverable error when LINE login fails", async () => {
    server.use(http.post("/api/v1/auth/line/login", () => HttpResponse.json({ error: "bad" }, { status: 401 })));
    render(<App />); expect(await screen.findByRole("alert")).toBeTruthy(); expect(screen.getByRole("button", { name: "Retry" })).toBeTruthy();
  });

  it("does not leave a LIFF initialization that never resolves on the signing-in screen", async () => {
    vi.useFakeTimers();
    liff.init.mockImplementation(() => new Promise(() => undefined));
    render(<App />);
    await act(async () => { await Promise.resolve(); });
    await act(async () => { await vi.advanceTimersByTimeAsync(BOOTSTRAP_TIMEOUT_MS + 1); });
    expect(screen.getByRole("alert").textContent).toContain("LIFF SDK initialization");
    expect(screen.getByRole("button", { name: "Retry" })).toBeTruthy();
  });

  it("retries successfully after a LIFF initialization timeout", async () => {
    vi.useFakeTimers();
    liff.init.mockImplementationOnce(() => new Promise(() => undefined));
    render(<App />);
    await act(async () => { await Promise.resolve(); });
    await act(async () => { await vi.advanceTimersByTimeAsync(BOOTSTRAP_TIMEOUT_MS + 1); });
    vi.useRealTimers();
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    expect(await screen.findByRole("heading", { name: "Select your role" })).toBeTruthy();
  });

  it("does not leave a stalled backend authentication request on the signing-in screen", async () => {
    vi.useFakeTimers();
    server.use(http.post("/api/v1/auth/line/login", () => new Promise<never>(() => undefined)));
    render(<App />);
    await act(async () => { await Promise.resolve(); });
    await act(async () => { await vi.advanceTimersByTimeAsync(BOOTSTRAP_TIMEOUT_MS + 1); });
    expect(screen.getByRole("alert").textContent).toContain("backend authentication");
    expect(screen.getByRole("button", { name: "Retry" })).toBeTruthy();
  });

  it("lets a coach accept a match invitation and refreshes the inbox", async () => {
    sessionStorage.setItem("platform.access-token", "token");
    let accepted = false;
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json({ data: { ...member, profileComplete: true, roles: [{ roleCode: "COACH", organizationId: "org", organizationCode: "MVP", organizationName: "MVP" }] }, meta: { requestId: "test" } })),
      http.get("/api/v1/course-match-invitations/mine", () => HttpResponse.json({ data: [{ invitationId: "i1", courseMatchId: "m1", courseMatchSessionId: "s1", sessionIndex: 1, startAt: "2026-09-01T02:00:00Z", endAt: "2026-09-01T03:00:00Z", venueName: "Court A", coachProfileId: "cp1", status: accepted ? "ACCEPTED" : "INVITED", invitationSentAt: "2026-08-24T10:00:00Z", respondedAt: accepted ? "2026-08-24T12:00:00Z" : null, responseNote: accepted ? "Accepted via Coach LIFF" : null }], meta: { requestId: "test" } })),
      http.post("/api/v1/course-match-invitations/i1/response", async ({ request }) => { const body = await request.json() as { status: string }; accepted = body.status === "ACCEPTED"; return HttpResponse.json({ data: { invitationId: "i1", courseMatchId: "m1", courseMatchSessionId: "s1", coachProfileId: "cp1", status: "ACCEPTED", respondedAt: "2026-08-24T12:00:00Z", responseNote: "Accepted via Coach LIFF" }, meta: { requestId: "test" } }); })
    );
    render(<App />);
    expect(await screen.findByRole("heading", { name: "COACH entry" })).toBeTruthy();
    fireEvent.click(await screen.findByRole("button", { name: "Accept match" }));
    await waitFor(() => expect(accepted).toBe(true));
    expect(await screen.findByText("Match invitation accepted.")).toBeTruthy();
    await waitFor(() => expect(screen.queryByRole("button", { name: "Accept match" })).toBeNull());
  });

  it("lets a student inspect, register, and cancel an open enrollment offering", async () => {
    sessionStorage.setItem("platform.access-token", "token");
    let registrationStatus: "NONE" | "ACTIVE" | "CANCELLED" = "NONE";
    const summary = () => ({ id: "o1", organizationId: "org", title: "Beginner Group", status: "OPEN", coach: { coachProfileId: "cp1", userId: "coach-user", displayName: "Coach Lin" }, scheduleType: "SINGLE", firstSessionAt: "2026-09-10T02:00:00Z", registrationOpenAt: "2026-08-20T00:00:00Z", registrationCloseAt: "2026-09-09T00:00:00Z", minimumParticipants: 2, maximumParticipants: 6, registeredCount: registrationStatus === "ACTIVE" ? 3 : 2, remainingCapacity: registrationStatus === "ACTIVE" ? 3 : 4, billingMode: "FULL_COURSE", skillLevel: "BEGINNER", priceSnapshotId: "ps1", pricePerParticipant: 1200, currency: "TWD", registrationState: registrationStatus === "ACTIVE" ? "REGISTERED" : "OPEN", ownRegistrationId: registrationStatus === "ACTIVE" ? "r1" : null, ownRegistrationStatus: registrationStatus === "NONE" ? null : registrationStatus, version: 2 });
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json({ data: { ...member, profileComplete: true, roles: [{ roleCode: "STUDENT", organizationId: "org", organizationCode: "MVP", organizationName: "MVP" }] }, meta: { requestId: "test" } })),
      http.get("/api/v1/coach-availability-proposals/available", () => HttpResponse.json({ data: [], meta: { requestId: "test" } })),
      http.get("/api/v1/lesson-requests/mine", () => HttpResponse.json({ data: [], meta: { requestId: "test" } })),
      http.get("/api/v1/course-offerings", () => HttpResponse.json({ data: { items: [summary()], page: 0, size: 100, total: 1 }, meta: { requestId: "test" } })),
      http.get("/api/v1/course-offerings/o1", () => HttpResponse.json({ data: { summary: summary(), description: "First group class", sessionPlans: [{ id: "os1", sequenceNo: 1, startAt: "2026-09-10T02:00:00Z", endAt: "2026-09-10T03:00:00Z", venueId: null, venueName: "Court A", venueAddress: "Taipei" }] }, meta: { requestId: "test" } })),
      http.get("/api/v1/me/course-offering-registrations", () => HttpResponse.json({ data: { items: registrationStatus === "NONE" ? [] : [{ id: "r1", offeringId: "o1", offeringTitle: "Beginner Group", offeringStatus: "OPEN", status: registrationStatus, registeredAt: "2026-08-25T03:00:00Z", cancelledAt: registrationStatus === "CANCELLED" ? "2026-08-25T04:00:00Z" : null, cancelReason: null, convertedCourseMembershipId: null, courseId: null }], page: 0, size: 100, total: registrationStatus === "NONE" ? 0 : 1 }, meta: { requestId: "test" } })),
      http.post("/api/v1/course-offerings/o1/registrations", () => { registrationStatus = "ACTIVE"; return HttpResponse.json({ data: { id: "r1", offeringId: "o1", status: "ACTIVE", registeredAt: "2026-08-25T03:00:00Z", cancelledAt: null, cancelReason: null, convertedCourseMembershipId: null }, meta: { requestId: "test" } }, { status: 201 }); }),
      http.post("/api/v1/course-offering-registrations/r1/cancellation", () => { registrationStatus = "CANCELLED"; return HttpResponse.json({ data: { id: "r1", offeringId: "o1", status: "CANCELLED", registeredAt: "2026-08-25T03:00:00Z", cancelledAt: "2026-08-25T04:00:00Z", cancelReason: null, convertedCourseMembershipId: null }, meta: { requestId: "test" } }); })
    );
    render(<App />);
    expect(await screen.findByRole("heading", { name: "STUDENT entry" })).toBeTruthy();
    expect(await screen.findByText(/Beginner Group/)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "查看課程" }));
    expect(await screen.findByRole("heading", { name: "Beginner Group", level: 4 })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "立即報名" }));
    expect(await screen.findByText("報名成功，系統已保留你的課程時段。")).toBeTruthy();
    await waitFor(() => expect(registrationStatus).toBe("ACTIVE"));
    fireEvent.click(await screen.findByRole("button", { name: "取消報名" }));
    expect(await screen.findByText("已取消報名並釋放保留時段。")).toBeTruthy();
    await waitFor(() => expect(registrationStatus).toBe("CANCELLED"));
  });

  it("lets a student cancel one formal-course session with secondary confirmation", async () => {
    sessionStorage.setItem("platform.access-token", "token");
    let cancelled = false;
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json({ data: { ...member, profileComplete: true, roles: [{ roleCode: "STUDENT", organizationId: "org", organizationCode: "MVP", organizationName: "MVP" }] }, meta: { requestId: "test" } })),
      http.get("/api/v1/course-offerings", () => HttpResponse.json({ data: { items: [], page: 0, size: 100, total: 0 }, meta: { requestId: "test" } })),
      http.get("/api/v1/me/course-offering-registrations", () => HttpResponse.json({ data: { items: [], page: 0, size: 100, total: 0 }, meta: { requestId: "test" } })),
      http.get("/api/v1/coach-availability-proposals/available", () => HttpResponse.json({ data: [], meta: { requestId: "test" } })),
      http.get("/api/v1/lesson-requests/mine", () => HttpResponse.json({ data: [], meta: { requestId: "test" } })),
      http.get("/api/v1/courses", () => HttpResponse.json({ data: { items: [{ id: "c1", organizationId: "org", courseNo: "C-001", courseType: "GROUP", scheduleType: "SINGLE", billingMode: "PER_SESSION", skillLevel: null, expectedParticipantCount: 4, minimumParticipants: 2, maximumParticipants: 6, totalSessionCount: 1, status: "ACTIVE", nextSessionStartAt: "2026-09-15T02:00:00Z", activeMembershipCount: 4 }], page: 0, size: 100, total: 1 }, meta: { requestId: "test" } })),
      http.get("/api/v1/courses/c1/sessions", () => HttpResponse.json({ data: [{ id: "s1", organizationId: "org", courseId: "c1", sequenceNo: 1, scheduledStartAt: "2026-09-15T02:00:00Z", scheduledEndAt: "2026-09-15T03:00:00Z", expectedParticipantCount: 4, guestParticipantCount: 0, actualParticipantCount: null, status: "SCHEDULED", cancellationSource: null, cancellationNote: null, completedAt: null, venueId: null, venueName: "Court A", venueAddress: "Taipei", venueStatus: "CONFIRMED", coachProfileId: "cp1", coachDisplayName: "Coach Lin", ownEnrollmentId: "e1", ownEnrollmentStatus: cancelled ? "CANCELLED" : "SCHEDULED" }], meta: { requestId: "test" } })),
      http.post("/api/v1/session-enrollments/e1/cancellation", () => { cancelled = true; return HttpResponse.json({ data: { enrollmentId: "e1", status: "CANCELLED", cancelledAt: "2026-08-25T14:00:00Z", courseSessionStatus: "SCHEDULED" }, meta: { requestId: "test" } }); })
    );
    render(<App />);
    expect(await screen.findByText(/C-001/)).toBeTruthy();
    fireEvent.click(await screen.findByRole("button", { name: "取消本堂報名" }));
    expect(await screen.findByRole("dialog", { name: "確認取消本堂報名" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "確認取消" }));
    await waitFor(() => expect(cancelled).toBe(true));
    expect(await screen.findByText("本堂報名已取消，其他堂次不受影響。")).toBeTruthy();
  });

});
