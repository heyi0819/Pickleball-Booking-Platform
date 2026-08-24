import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
const liff = vi.hoisted(() => ({ init: vi.fn(async () => undefined), isLoggedIn: vi.fn(() => true), login: vi.fn(), getIDToken: vi.fn(() => "line-id-token") }));
vi.mock("@line/liff", () => ({ default: liff }));
import { App } from "./App";
const member = { id: "00000000-0000-0000-0000-000000000001", displayName: "Test member", phone: null, email: null, locale: "zh-TW", profileComplete: false, roles: [] };
let afterProfile = false;
const server = setupServer(
  http.post("/api/v1/auth/line/login", () => HttpResponse.json({ data: { accessToken: "token", tokenType: "Bearer", expiresIn: 1800, user: { id: member.id, displayName: member.displayName, roles: [] } }, meta: { requestId: "test" } })),
  http.patch("/api/v1/me/profile", () => { afterProfile = true; return HttpResponse.json({ data: member, meta: { requestId: "test" } }); }),
  http.get("/api/v1/me", () => HttpResponse.json({ data: afterProfile ? { ...member, profileComplete: true, roles: [{ roleCode: "STUDENT", organizationId: "org", organizationCode: "MVP", organizationName: "MVP" }, { roleCode: "COACH", organizationId: "org", organizationCode: "MVP", organizationName: "MVP" }] } : member, meta: { requestId: "test"} })),
  http.get("/api/v1/coach-availability-proposals/mine", () => HttpResponse.json({ data: [], meta: { requestId: "test" } })),
  http.get("/api/v1/course-match-invitations/mine", () => HttpResponse.json({ data: [], meta: { requestId: "test" } }))
);
beforeAll(() => server.listen({ onUnhandledRequest: "error" })); beforeEach(() => vi.stubEnv("VITE_LIFF_ID", "test-liff")); afterEach(() => { cleanup(); server.resetHandlers(); sessionStorage.clear(); afterProfile = false; vi.clearAllMocks(); vi.unstubAllEnvs(); }); afterAll(() => server.close());

describe("LIFF authentication, role, and Slice 3 coach flow", () => {
  it("logs in with LIFF, completes profile, and selects a role", async () => {
    render(<App />); expect(await screen.findByRole("heading", { name: "Complete your profile" })).toBeTruthy();
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Updated member" } }); fireEvent.submit(screen.getByRole("button", { name: "Save profile" }).closest("form")!);
    expect(await screen.findByRole("heading", { name: "Select your role" })).toBeTruthy(); fireEvent.click(screen.getByRole("button", { name: "COACH" })); expect(await screen.findByRole("heading", { name: "COACH entry" })).toBeTruthy(); await waitFor(() => expect(liff.getIDToken).toHaveBeenCalled());
  });

  it("shows a recoverable error when LINE login fails", async () => {
    server.use(http.post("/api/v1/auth/line/login", () => HttpResponse.json({ error: "bad" }, { status: 401 })));
    render(<App />); expect(await screen.findByRole("alert")).toBeTruthy(); expect(screen.getByRole("button", { name: "Retry" })).toBeTruthy();
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
});
