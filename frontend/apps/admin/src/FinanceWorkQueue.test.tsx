import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { FinanceWorkQueue } from "./FinanceWorkQueue";

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

function fillPayment() {
  fireEvent.change(screen.getByLabelText("Receivable ID", { selector: "input" }), { target: { value: "11111111-1111-1111-1111-111111111111" } });
  fireEvent.change(screen.getByLabelText("Payer user ID"), { target: { value: "22222222-2222-2222-2222-222222222222" } });
  fireEvent.change(screen.getByLabelText("Amount", { selector: "input" }), { target: { value: "600.00" } });
  fireEvent.change(screen.getByLabelText("Paid at"), { target: { value: "2026-08-26T10:00" } });
}

describe("FinanceWorkQueue", () => {
  it("requires a secondary confirmation and sends idempotency-protected payment commands", async () => {
    let idempotencyKey = "";
    server.use(http.post("/api/v1/receivables/:id/payments", async ({ request }) => {
      idempotencyKey = request.headers.get("Idempotency-Key") ?? "";
      return HttpResponse.json({ data: { paymentId: "p1", receivableId: "11111111-1111-1111-1111-111111111111", amount: "600.00", method: "CASH", paymentStatus: "PARTIALLY_PAID", paidTotal: "600.00", outstandingAmount: "600.00" }, meta: { requestId: "test" } }, { status: 201 });
    }));
    render(<FinanceWorkQueue token="token" />);
    fillPayment();
    fireEvent.click(screen.getByRole("button", { name: "Review payment" }));
    expect(await screen.findByRole("dialog", { name: "Confirm finance command" })).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Confirm command" }));
    expect(await screen.findByText(/Payment recorded: p1/)).toBeTruthy();
    expect(idempotencyKey).toMatch(/^admin-payment-/);
  });

  it("keeps refund request, approval and execution as separate confirmed commands", async () => {
    const operations: string[] = [];
    server.use(
      http.post("/api/v1/receivables/:id/refunds", ({ request }) => { operations.push(`request:${request.headers.get("Idempotency-Key")}`); return HttpResponse.json({ data: { refundId: "r1", paymentId: "p1", status: "PENDING_APPROVAL", amount: "300.00", currency: "TWD" }, meta: { requestId: "test" } }, { status: 201 }); }),
      http.post("/api/v1/refunds/:id/review", ({ request }) => { operations.push(`review:${request.headers.get("Idempotency-Key")}`); return HttpResponse.json({ data: { refundId: "r1", status: "APPROVED", approvedBy: "committee", approvedAt: "2026-08-26T02:00:00Z" }, meta: { requestId: "test" } }); }),
      http.post("/api/v1/refunds/:id/execution", ({ request }) => { operations.push(`execution:${request.headers.get("Idempotency-Key")}`); return HttpResponse.json({ data: { refundId: "r1", status: "COMPLETED", processedBy: "committee", refundedAt: "2026-08-26T02:30:00Z" }, meta: { requestId: "test" } }); })
    );
    render(<FinanceWorkQueue token="token" />);

    const refundForm = screen.getByRole("form", { name: "Request refund" });
    fireEvent.change(refundForm.querySelector('input[name="receivableId"]')!, { target: { value: "11111111-1111-1111-1111-111111111111" } });
    fireEvent.change(screen.getByLabelText("Payment ID"), { target: { value: "p1" } });
    fireEvent.change(refundForm.querySelector('input[name="amount"]')!, { target: { value: "300.00" } });
    fireEvent.change(refundForm.querySelector('textarea[name="reason"]')!, { target: { value: "student withdrawal" } });
    fireEvent.click(screen.getByRole("button", { name: "Review refund request" }));
    fireEvent.click(await screen.findByRole("button", { name: "Confirm command" }));
    expect(await screen.findByText(/Refund requested: r1/)).toBeTruthy();

    const reviewForm = screen.getByRole("form", { name: "Review refund" });
    fireEvent.change(reviewForm.querySelector('input[name="refundId"]')!, { target: { value: "r1" } });
    fireEvent.change(reviewForm.querySelector('textarea[name="reason"]')!, { target: { value: "approved" } });
    fireEvent.click(screen.getByRole("button", { name: "Review decision" }));
    fireEvent.click(await screen.findByRole("button", { name: "Confirm command" }));
    expect(await screen.findByText(/Refund review saved: r1 \(APPROVED\)/)).toBeTruthy();

    const executionForm = screen.getByRole("form", { name: "Execute refund" });
    fireEvent.change(executionForm.querySelector('input[name="refundId"]')!, { target: { value: "r1" } });
    fireEvent.change(screen.getByLabelText("Refunded at"), { target: { value: "2026-08-26T10:30" } });
    fireEvent.click(screen.getByRole("button", { name: "Review refund execution" }));
    fireEvent.click(await screen.findByRole("button", { name: "Confirm command" }));
    await waitFor(() => expect(screen.getByText(/Refund execution saved: r1 \(COMPLETED\)/)).toBeTruthy());

    expect(operations).toHaveLength(3);
    expect(operations.every((entry) => entry.includes("admin-refund-"))).toBe(true);
  });
});
