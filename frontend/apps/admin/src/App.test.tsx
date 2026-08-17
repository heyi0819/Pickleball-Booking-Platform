import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { render, screen } from "@testing-library/react";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { App } from "./App";
const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: "error" })); afterEach(() => { server.resetHandlers(); sessionStorage.clear(); }); afterAll(() => server.close());
describe("admin authorization", () => {
  it("allows committee users", async () => {
    sessionStorage.setItem("platform.access-token", "token");
    server.use(http.get("/api/v1/me", () => HttpResponse.json({ data: { id: "u", displayName: "Committee", phone: null, email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "COMMITTEE", organizationId: "o", organizationCode: "MVP", organizationName: "MVP" }] }, meta: { requestId: "test" } })));
    render(<App />); expect(await screen.findByRole("heading", { name: "Authorized admin entry" })).toBeTruthy();
  });
  it("denies non-admin roles", async () => {
    sessionStorage.setItem("platform.access-token", "token");
    server.use(http.get("/api/v1/me", () => HttpResponse.json({ data: { id: "u", displayName: "Student", phone: null, email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "STUDENT", organizationId: "o", organizationCode: "MVP", organizationName: "MVP" }] }, meta: { requestId: "test" } })));
    render(<App />); expect(await screen.findByRole("alert")).toBeTruthy();
  });
});
