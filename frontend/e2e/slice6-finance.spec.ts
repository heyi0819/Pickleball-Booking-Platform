import { expect, test } from "@playwright/test";

const envelope = (data: unknown) => ({ data, meta: { requestId: "slice6-e2e" } });

test("committee records payment then requests, approves and executes a partial refund", async ({ page }) => {
  const observedKeys: string[] = [];
  const receivableId = "11111111-1111-1111-1111-111111111111";
  const payerId = "22222222-2222-2222-2222-222222222222";
  const paymentId = "33333333-3333-3333-3333-333333333333";
  const refundId = "44444444-4444-4444-4444-444444444444";

  await page.addInitScript(() => sessionStorage.setItem("platform.access-token", "test-token"));
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    if (request.method() === "GET" && path === "/api/v1/me") {
      await route.fulfill({ json: envelope({ id: "committee-user", displayName: "Finance Committee", phone: null, email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "COMMITTEE", organizationId: "org", organizationCode: "MVP", organizationName: "MVP" }] }) });
      return;
    }
    if (request.method() === "GET" && (path === "/api/v1/course-offerings" || path === "/api/v1/courses")) {
      await route.fulfill({ json: envelope({ items: [], page: 0, size: 100, total: 0 }) });
      return;
    }
    if (request.method() === "GET") {
      await route.fulfill({ json: envelope([]) });
      return;
    }

    const idempotencyKey = request.headers()["idempotency-key"];
    if (idempotencyKey) observedKeys.push(idempotencyKey);
    if (request.method() === "POST" && path === `/api/v1/receivables/${receivableId}/payments`) {
      await route.fulfill({ status: 201, json: envelope({ paymentId, receivableId, amount: "600.00", method: "BANK_TRANSFER", paymentStatus: "PARTIALLY_PAID", paidTotal: "600.00", outstandingAmount: "600.00" }) });
      return;
    }
    if (request.method() === "POST" && path === `/api/v1/receivables/${receivableId}/refunds`) {
      await route.fulfill({ status: 201, json: envelope({ refundId, paymentId, status: "PENDING_APPROVAL", amount: "300.00", currency: "TWD" }) });
      return;
    }
    if (request.method() === "POST" && path === `/api/v1/refunds/${refundId}/review`) {
      await route.fulfill({ json: envelope({ refundId, status: "APPROVED", approvedBy: "committee-user", approvedAt: "2026-08-26T02:00:00Z" }) });
      return;
    }
    if (request.method() === "POST" && path === `/api/v1/refunds/${refundId}/execution`) {
      await route.fulfill({ json: envelope({ refundId, status: "COMPLETED", processedBy: "committee-user", refundedAt: "2026-08-26T02:30:00Z" }) });
      return;
    }
    await route.fulfill({ status: 404, json: { error: { code: "NOT_MOCKED", message: path, traceId: "slice6-e2e" } } });
  });

  await page.goto("http://127.0.0.1:4174");
  await expect(page.getByRole("heading", { name: "Finance operations" })).toBeVisible();

  const paymentForm = page.getByRole("form", { name: "Record payment" });
  await paymentForm.locator('input[name="receivableId"]').fill(receivableId);
  await paymentForm.locator('input[name="payerUserId"]').fill(payerId);
  await paymentForm.locator('input[name="amount"]').fill("600.00");
  await paymentForm.locator('select[name="method"]').selectOption("BANK_TRANSFER");
  await paymentForm.locator('input[name="paidAt"]').fill("2026-08-26T10:00");
  await paymentForm.getByRole("button", { name: "Review payment" }).click();
  await expect(page.getByRole("dialog", { name: "Confirm finance command" })).toContainText("Record 600.00");
  await page.getByRole("button", { name: "Confirm command" }).click();
  await expect(page.getByRole("status")).toContainText(`Payment recorded: ${paymentId}`);

  const refundForm = page.getByRole("form", { name: "Request refund" });
  await refundForm.locator('input[name="receivableId"]').fill(receivableId);
  await refundForm.locator('input[name="paymentId"]').fill(paymentId);
  await refundForm.locator('input[name="amount"]').fill("300.00");
  await refundForm.locator('textarea[name="reason"]').fill("Student withdrawal");
  await refundForm.getByRole("button", { name: "Review refund request" }).click();
  await page.getByRole("button", { name: "Confirm command" }).click();
  await expect(page.getByRole("status")).toContainText(`Refund requested: ${refundId}`);

  const reviewForm = page.getByRole("form", { name: "Review refund" });
  await reviewForm.locator('input[name="refundId"]').fill(refundId);
  await reviewForm.locator('select[name="decision"]').selectOption("APPROVE");
  await reviewForm.locator('textarea[name="reason"]').fill("Committee approved");
  await reviewForm.getByRole("button", { name: "Review decision" }).click();
  await expect(page.getByRole("dialog", { name: "Confirm finance command" })).toContainText("APPROVE");
  await page.getByRole("button", { name: "Confirm command" }).click();
  await expect(page.getByRole("status")).toContainText("Refund review saved");

  const executionForm = page.getByRole("form", { name: "Execute refund" });
  await executionForm.locator('input[name="refundId"]').fill(refundId);
  await executionForm.locator('select[name="method"]').selectOption("BANK_TRANSFER");
  await executionForm.locator('input[name="refundedAt"]').fill("2026-08-26T10:30");
  await executionForm.locator('input[name="reference"]').fill("RF-12345");
  await executionForm.getByRole("button", { name: "Review refund execution" }).click();
  await expect(page.getByRole("dialog", { name: "Confirm finance command" })).toContainText("Execute refund");
  await page.getByRole("button", { name: "Confirm command" }).click();
  await expect(page.getByRole("status")).toContainText("Refund execution saved");

  expect(observedKeys).toHaveLength(4);
  expect(observedKeys.every((key) => key.startsWith("admin-"))).toBe(true);
});
