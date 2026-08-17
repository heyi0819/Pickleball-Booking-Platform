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
  http.get("/api/v1/me", () => HttpResponse.json({ data: afterProfile ? { ...member, profileComplete: true, roles: [{ roleCode: "STUDENT", organizationId: "org", organizationCode: "MVP", organizationName: "MVP" }, { roleCode: "COACH", organizationId: "org", organizationCode: "MVP", organizationName: "MVP" }] } : member, meta: { requestId: "test"} }))
);
beforeAll(() => server.listen({ onUnhandledRequest: "error" })); beforeEach(() => vi.stubEnv("VITE_LIFF_ID", "test-liff")); afterEach(() => { cleanup(); server.resetHandlers(); sessionStorage.clear(); afterProfile = false; vi.clearAllMocks(); vi.unstubAllEnvs(); }); afterAll(() => server.close());
describe("LIFF authentication and role flow", () => {
  it("logs in with LIFF, completes profile, and selects a role", async () => {
    render(<App />); expect(await screen.findByRole("heading", { name: "Complete your profile" })).toBeTruthy();
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Updated member" } }); fireEvent.submit(screen.getByRole("button", { name: "Save profile" }).closest("form")!);
    expect(await screen.findByRole("heading", { name: "Select your role" })).toBeTruthy(); fireEvent.click(screen.getByRole("button", { name: "COACH" })); expect(await screen.findByRole("heading", { name: "COACH entry" })).toBeTruthy(); await waitFor(() => expect(liff.getIDToken).toHaveBeenCalled());
  });
  it("shows a recoverable error when LINE login fails", async () => {
    server.use(http.post("/api/v1/auth/line/login", () => HttpResponse.json({ error: "bad" }, { status: 401 })));
    render(<App />); expect(await screen.findByRole("alert")).toBeTruthy(); expect(screen.getByRole("button", { name: "Retry" })).toBeTruthy();
  });
});
