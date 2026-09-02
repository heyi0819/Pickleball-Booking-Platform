import { expect, test } from "@playwright/test";

const me = { id: "00000000-0000-0000-0000-000000000001", displayName: "Test member", email: "test@example.test", locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "STUDENT", organizationId: "00000000-0000-0000-0000-000000000010", organizationCode: "MVP", organizationName: "MVP" }, { roleCode: "COACH", organizationId: "00000000-0000-0000-0000-000000000010", organizationCode: "MVP", organizationName: "MVP" }] };
test("LIFF login reaches role selection and chosen entry", async ({ page }) => {
  await page.route("**/api/v1/auth/line/login", (route) => route.fulfill({ json: { data: { accessToken: "test-token", tokenType: "Bearer", expiresIn: 1800, user: { id: me.id, displayName: me.displayName, roles: ["STUDENT", "COACH"] } }, meta: { requestId: "test" } } }));
  await page.route("**/api/v1/me", (route) => route.fulfill({ json: { data: me, meta: { requestId: "test" } } }));
  await page.goto("/"); await expect(page.getByRole("heading", { name: "選擇使用身分" })).toBeVisible();
  await page.getByRole("button", { name: "教練" }).click(); await expect(page.getByRole("heading", { name: "教練首頁" })).toBeVisible();
  await page.getByRole("button", { name: "可授課時段" }).click(); await expect(page.getByRole("heading", { name: "可授課時段" })).toBeVisible();
  await page.goBack(); await expect(page.getByRole("heading", { name: "教練首頁" })).toBeVisible();
  for (const width of [320, 375, 390]) {
    await page.setViewportSize({ width, height: 800 });
    await expect(page.getByRole("navigation", { name: "主要導覽" })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  }
});
