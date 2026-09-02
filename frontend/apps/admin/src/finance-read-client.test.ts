import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiClientError, createApiClient } from "@pickleball/api-client";

const api = createApiClient({ baseUrl: "https://finance.test/api/v1" });
const context = {
  id: "record-1", organizationId: "org-1", organizationName: "Fixture club",
  memberId: "member-1", memberName: "Fixture member", currency: "TWD",
};
const reference = { id: "receivable-1", receivableNo: "AR-001", courseId: "course-1", courseNo: "COURSE-001" };
const at = "2026-01-01T00:00:00Z";
const fixtures = {
  receivables: { ...context, ...reference, totalAmount: "1200.00", adjustedAmount: "0.00", paidAmount: "600.00",
    refundedAmount: "0.00", outstandingAmount: "600.00", status: "PARTIALLY_PAID", createdAt: at, dueAt: null },
  payments: { ...context, paymentNo: "PAY-001", amount: "600.00", status: "COMPLETED", method: "CASH",
    paidAt: at, recordedAt: at, refundableAmount: "550.00", receivables: [reference] },
  refunds: { ...context, refundNo: "RF-001", paymentId: "payment-1", paymentNo: "PAY-001", amount: "50.00",
    status: "PENDING_APPROVAL", reason: "Fixture refund", requestedAt: at, approvedAt: null, refundedAt: null,
    refundableAmount: "600.00", receivables: [reference] },
};
afterEach(() => vi.unstubAllGlobals());

describe("finance read adapter and generated contract", () => {
  it("sends explicit scope, bearer authentication and bounded filters for all list operations", async () => {
    const fetch = vi.fn(async (url: string, init: RequestInit) => {
      const request = new URL(url);
      expect(new Headers(init.headers).get("Authorization")).toBe("Bearer fixture-token");
      expect(init.method).toBe("GET");
      expect(init.body).toBeUndefined();
      expect(request.searchParams.get("organizationId")).toBe("org-1");
      expect(request.searchParams.get("memberId")).toBe("member-1");
      expect(request.searchParams.get("page")).toBe("2");
      expect(request.searchParams.get("size")).toBe("10");
      const kind = request.pathname.split("/").at(-1) as keyof typeof fixtures;
      const filter = kind === "receivables" ? "courseId" : kind === "payments" ? "receivableId" : "paymentId";
      expect(request.searchParams.get(filter)).toBe("related-1");
      return Response.json({ data: { items: [fixtures[kind]], page: 2, size: 10, totalElements: 21 }, meta: { requestId: "test" } });
    });
    vi.stubGlobal("fetch", fetch);
    const query = { organizationId: "org-1", memberId: "member-1", page: 2, size: 10 };
    const receivables = await api.listAdminReceivables("fixture-token", { ...query, courseId: "related-1", status: "PARTIALLY_PAID" });
    const payments = await api.listAdminPayments("fixture-token", { ...query, receivableId: "related-1", status: "COMPLETED" });
    const refunds = await api.listAdminRefunds("fixture-token", { ...query, paymentId: "related-1", status: "PENDING_APPROVAL" });
    expect(fetch).toHaveBeenCalledTimes(3);
    expect(receivables.items[0].createdAt).toEqual(new Date(at));
    expect(receivables.items[0].dueAt).toBeNull();
    expect(receivables.items[0].outstandingAmount).toBe("600.00");
    expect(payments.items[0].receivables).toEqual([reference]);
    expect(payments.items[0].refundableAmount).toBe("550.00");
    expect(refunds.items[0].approvedAt).toBeNull();
    expect(refunds.items[0].refundedAt).toBeNull();
  });

  it("uses scoped detail routes and preserves readable data and exact decimal strings", async () => {
    const fetch = vi.fn(async (url: string) => {
      const request = new URL(url);
      expect(request.searchParams.get("organizationId")).toBe("org-1");
      expect(request.pathname.endsWith("/record-1")).toBe(true);
      const kind = request.pathname.split("/").at(-2) as keyof typeof fixtures;
      return Response.json({ data: fixtures[kind], meta: { requestId: "test" } });
    });
    vi.stubGlobal("fetch", fetch);
    const records = await Promise.all([
      api.getAdminReceivable("fixture-token", "org-1", "record-1"),
      api.getAdminPayment("fixture-token", "org-1", "record-1"),
      api.getAdminRefund("fixture-token", "org-1", "record-1"),
    ]);
    expect(records.every(record => record.memberName === "Fixture member" && record.currency === "TWD")).toBe(true);
    expect(fetch).toHaveBeenCalledTimes(3);
  });

  it("maps errors without returning raw backend messages on any read operation", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => Response.json(
      { error: { code: "ORG_SCOPE_DENIED", message: "Synthetic private backend diagnostic" } }, { status: 403 },
    )));
    const query = { organizationId: "org-1" };
    const calls = [
      () => api.listAdminReceivables("fixture-token", query),
      () => api.listAdminPayments("fixture-token", query),
      () => api.listAdminRefunds("fixture-token", query),
      () => api.getAdminReceivable("fixture-token", "org-1", "record-1"),
      () => api.getAdminPayment("fixture-token", "org-1", "record-1"),
      () => api.getAdminRefund("fixture-token", "org-1", "record-1"),
    ];
    for (const call of calls) {
      await expect(call()).rejects.toEqual(new ApiClientError(403, "ORG_SCOPE_DENIED"));
    }
  });
});
