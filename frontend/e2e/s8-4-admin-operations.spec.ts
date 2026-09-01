import { expect, test } from "@playwright/test";

test("platform admin scopes and audits failed outbox recovery", async ({ page }) => {
  let recovered = false;
  await page.addInitScript(() => sessionStorage.setItem("platform.access-token", "test-token"));
  await page.route("**/api/v1/me", route => route.fulfill({ json: {
    data: { id: "platform-admin", displayName: "Platform Admin", email: null, locale: "zh-TW",
      profileComplete: true, roles: [{ roleCode: "PLATFORM_ADMIN", organizationId: null, organizationCode: null, organizationName: null }] },
    meta: { requestId: "e2e" },
  } }));
  await page.route("**/api/v1/admin/outbox-events**", async route => {
    if (route.request().method() === "POST") {
      expect(route.request().headers()["idempotency-key"]).toMatch(/^admin-recovery-outbox-1-/);
      expect(route.request().postDataJSON()).toEqual({ reason: "upstream repaired" });
      recovered = true;
      await route.fulfill({ json: { data: outbox("PENDING", null), meta: { requestId: "e2e" } } });
      return;
    }
    const url = new URL(route.request().url());
    expect(url.searchParams.get("organizationId")).toBe("11111111-1111-1111-1111-111111111111");
    await route.fulfill({ json: { data: { items: recovered ? [] : [outbox("FAILED", "timeout")], page: 0, size: 50,
      totalElements: recovered ? 0 : 1 }, meta: { requestId: "e2e" } } });
  });
  await page.route("**/api/v1/admin/notifications**", route => route.fulfill({ json: {
    data: { items: [], page: 0, size: 50, totalElements: 0 }, meta: { requestId: "e2e" },
  } }));

  await page.goto("http://127.0.0.1:4174");
  await page.getByLabel("Organization ID").fill("11111111-1111-1111-1111-111111111111");
  await page.getByRole("button", { name: "Refresh operations" }).click();
  await expect(page.getByText("timeout")).toBeVisible();
  await page.getByLabel("Audit reason").fill("upstream repaired");
  await page.getByRole("button", { name: "Retry event" }).click();
  await expect(page.getByText("Recovery request accepted and audited.")).toBeVisible();
  expect(recovered).toBe(true);
});

function outbox(status: "FAILED" | "PENDING", lastError: string | null) {
  return { id: "outbox-1", organizationId: "11111111-1111-1111-1111-111111111111", aggregateType: "Course",
    aggregateId: "22222222-2222-2222-2222-222222222222", eventType: "CourseChanged", status, attemptCount: 2,
    availableAt: "2026-08-29T01:00:00Z", processedAt: null, lastError, createdAt: "2026-08-29T00:00:00Z" };
}
