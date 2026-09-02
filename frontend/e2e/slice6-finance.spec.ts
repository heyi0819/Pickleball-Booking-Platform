import { expect, test } from "@playwright/test";

const envelope = (data: unknown) => ({ data, meta: { requestId: "slice6-e2e" } });
test("committee completes readable payment and refund workflow with confirmations", async ({ page }) => {
  const observedKeys: string[] = [];
  const receivableId = "11111111-1111-1111-1111-111111111111";
  const paymentId = "33333333-3333-3333-3333-333333333333";
  const refundId = "44444444-4444-4444-4444-444444444444";
  let refundStatus = "PENDING_APPROVAL";
  const receivable = () => ({ id: receivableId, receivableNo: "AR-001", organizationId: "org", organizationName: "MVP", memberId: "payer", memberName: "Student One", courseId: "course-1", courseNo: "PB-101", currency: "TWD", totalAmount: "1200.00", adjustedAmount: "0.00", paidAmount: "600.00", refundedAmount: "0.00", outstandingAmount: "600.00", status: "PARTIALLY_PAID", createdAt: "2026-01-01T00:00:00Z", dueAt: null });
  const payment = () => ({ id: paymentId, paymentNo: "PAY-001", organizationId: "org", organizationName: "MVP", memberId: "payer", memberName: "Student One", amount: "600.00", currency: "TWD", status: "COMPLETED", method: "BANK_TRANSFER", paidAt: "2026-08-26T02:00:00Z", recordedAt: "2026-08-26T02:00:00Z", refundableAmount: "550.00", receivables: [{ id: receivableId, receivableNo: "AR-001", courseId: "course-1", courseNo: "PB-101" }] });
  const refund = () => ({ id: refundId, refundNo: "RF-001", organizationId: "org", organizationName: "MVP", paymentId, paymentNo: "PAY-001", memberId: "payer", memberName: "Student One", amount: "300.00", currency: "TWD", status: refundStatus, reason: "Student withdrawal", requestedAt: "2026-08-26T02:00:00Z", approvedAt: refundStatus === "PENDING_APPROVAL" ? null : "2026-08-26T02:10:00Z", refundedAt: refundStatus === "COMPLETED" ? "2026-08-26T02:30:00Z" : null, refundableAmount: "600.00", receivables: payment().receivables });

  await page.addInitScript(() => sessionStorage.setItem("platform.access-token", "test-token"));
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request(); const url = new URL(request.url()); const path = url.pathname;
    if (request.method() === "GET" && path === "/api/v1/me") return route.fulfill({ json: envelope({ id: "committee-user", displayName: "Finance Committee", email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "COMMITTEE", organizationId: "org", organizationCode: "MVP", organizationName: "MVP" }] }) });
    const isDetail = /\/(11111111-1111-1111-1111-111111111111|33333333-3333-3333-3333-333333333333|44444444-4444-4444-4444-444444444444)$/.test(path);
    if (request.method() === "GET" && path === "/api/v1/admin/receivables") return route.fulfill({ json: envelope({ items: [receivable()], page: 0, size: 20, totalElements: 1 }) });
    if (request.method() === "GET" && path === "/api/v1/admin/payments") return route.fulfill({ json: envelope({ items: [payment()], page: 0, size: 20, totalElements: 1 }) });
    if (request.method() === "GET" && path === "/api/v1/admin/refunds") return route.fulfill({ json: envelope({ items: [refund()], page: 0, size: 20, totalElements: 1 }) });
    if (request.method() === "GET" && isDetail && path.includes("receivables")) return route.fulfill({ json: envelope(receivable()) });
    if (request.method() === "GET" && isDetail && path.includes("payments")) return route.fulfill({ json: envelope(payment()) });
    if (request.method() === "GET" && isDetail && path.includes("refunds")) return route.fulfill({ json: envelope(refund()) });
    if (request.method() === "GET" && (path === "/api/v1/course-offerings" || path === "/api/v1/courses")) return route.fulfill({ json: envelope({ items: [], page: 0, size: 100, total: 0 }) });
    if (request.method() === "GET") return route.fulfill({ json: envelope([]) });
    const idempotencyKey = request.headers()["idempotency-key"]; if (idempotencyKey) observedKeys.push(idempotencyKey);
    if (path === `/api/v1/receivables/${receivableId}/payments`) return route.fulfill({ status: 201, json: envelope({ paymentId, receivableId, amount: "600.00", method: "BANK_TRANSFER", paymentStatus: "PARTIALLY_PAID", paidTotal: "600.00", outstandingAmount: "600.00" }) });
    if (path === `/api/v1/receivables/${receivableId}/refunds`) return route.fulfill({ status: 201, json: envelope({ refundId, paymentId, status: "PENDING_APPROVAL", amount: "300.00", currency: "TWD" }) });
    if (path === `/api/v1/refunds/${refundId}/review`) { refundStatus = "APPROVED"; return route.fulfill({ json: envelope({ refundId, status: refundStatus, approvedBy: "committee-user", approvedAt: "2026-08-26T02:10:00Z" }) }); }
    if (path === `/api/v1/refunds/${refundId}/execution`) { refundStatus = "COMPLETED"; return route.fulfill({ json: envelope({ refundId, status: refundStatus, processedBy: "committee-user", refundedAt: "2026-08-26T02:30:00Z" }) }); }
    return route.fulfill({ status: 404, json: { error: { code: "NOT_MOCKED" } } });
  });

  await page.goto("http://127.0.0.1:4174");
  await expect(page.getByRole("heading", { name: "財務工作清單" })).toBeVisible();
  await page.getByRole("button", { name: "查看詳情" }).nth(0).click();
  await expect(page.getByText("付款人會依此應收自動帶入：Student One")).toBeVisible();
  const paymentForm = page.getByRole("form", { name: "記錄付款" });
  await paymentForm.getByLabel("付款金額").fill("600.00"); await paymentForm.getByLabel("方式").selectOption("BANK_TRANSFER"); await paymentForm.getByLabel("付款時間").fill("2026-08-26T10:00");
  await paymentForm.getByRole("button", { name: "確認付款內容" }).click();
  await expect(page.getByRole("dialog", { name: "確認財務命令" })).toContainText("Student One");
  await page.getByRole("button", { name: "確認送出" }).click(); await expect(page.getByRole("status")).toContainText("已記錄付款");

  await page.getByRole("button", { name: "查看詳情" }).nth(1).click();
  const requestForm = page.getByRole("form", { name: "提出退款申請" });
  await requestForm.getByLabel("退款金額").fill("300.00"); await requestForm.getByLabel("退款原因").fill("Student withdrawal");
  await requestForm.getByRole("button", { name: "確認退款內容" }).click(); await page.getByRole("button", { name: "確認送出" }).click();
  await expect(page.getByRole("status")).toContainText("退款申請已建立");

  await page.getByRole("button", { name: "查看詳情" }).nth(2).click();
  const reviewForm = page.getByRole("form", { name: "審核退款" });
  await reviewForm.getByLabel("審核原因").fill("Committee approved"); await reviewForm.getByRole("button", { name: "確認審核內容" }).click(); await page.getByRole("button", { name: "確認送出" }).click();
  await expect(page.getByRole("status")).toContainText("退款審核已完成");

  await page.getByRole("button", { name: "查看詳情" }).nth(2).click();
  const executionForm = page.getByRole("form", { name: "執行退款" });
  await executionForm.getByLabel("方式").selectOption("BANK_TRANSFER"); await executionForm.getByLabel("退款時間").fill("2026-08-26T10:30"); await executionForm.getByRole("button", { name: "確認執行內容" }).click(); await page.getByRole("button", { name: "確認送出" }).click();
  await expect(page.getByRole("status")).toContainText("退款執行已完成");
  expect(observedKeys).toHaveLength(4); expect(observedKeys.every((key) => key.startsWith("admin-"))).toBe(true);
});
