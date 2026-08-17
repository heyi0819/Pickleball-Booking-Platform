import { expect, test } from "@playwright/test";
const response = (roles: object[]) => ({ data: { id: "admin-user", displayName: "Administrator", phone: null, email: null, locale: "zh-TW", profileComplete: true, roles }, meta: { requestId: "test" } });
test("admin smoke permits committee and rejects a student", async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem("platform.access-token", "test-token"));
  await page.route("**/api/v1/me", route => route.fulfill({ json: response([{ roleCode: "COMMITTEE", organizationId: "org", organizationCode: "MVP", organizationName: "MVP" }]) }));
  await page.goto("http://127.0.0.1:4174"); await expect(page.getByRole("heading", { name: "Authorized admin entry" })).toBeVisible();
  await page.route("**/api/v1/me", route => route.fulfill({ json: response([{ roleCode: "STUDENT", organizationId: "org", organizationCode: "MVP", organizationName: "MVP" }]) }));
  await page.reload(); await expect(page.getByRole("alert")).toContainText("Forbidden");
});
