import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { FinanceWorkQueue } from "./FinanceWorkQueue";

const receivable = { id: "ar-1", receivableNo: "AR-001", organizationId: "org-1", organizationName: "示範球會", memberId: "member-1", memberName: "王小明", courseId: "course-1", courseNo: "PB-101", currency: "TWD", totalAmount: "1200.00", adjustedAmount: "0.00", paidAmount: "600.00", refundedAmount: "0.00", outstandingAmount: "600.00", status: "PARTIALLY_PAID", createdAt: "2026-01-01T00:00:00Z", dueAt: null };
const payment = { id: "pay-1", paymentNo: "PAY-001", organizationId: "org-1", organizationName: "示範球會", memberId: "member-1", memberName: "王小明", amount: "600.00", currency: "TWD", status: "COMPLETED", method: "CASH", paidAt: "2026-01-01T00:00:00Z", recordedAt: "2026-01-01T00:00:00Z", refundableAmount: "550.00", receivables: [{ id: "ar-1", receivableNo: "AR-001", courseId: "course-1", courseNo: "PB-101" }] };
const refund = { id: "refund-1", refundNo: "RF-001", organizationId: "org-1", organizationName: "示範球會", paymentId: "pay-1", paymentNo: "PAY-001", memberId: "member-1", memberName: "王小明", amount: "50.00", currency: "TWD", status: "PENDING_APPROVAL", reason: "停課退款", requestedAt: "2026-01-01T00:00:00Z", approvedAt: null, refundedAt: null, refundableAmount: "600.00", receivables: payment.receivables };

function respond(url: string) {
  if (url.includes("/admin/receivables")) return receivable;
  if (url.includes("/admin/payments")) return payment;
  return refund;
}
function renderQueue() {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    const item = respond(url);
    const list = !url.match(/\/(ar-1|pay-1|refund-1)\?/);
    return Response.json({ data: list ? { items: [item], page: 0, size: 20, totalElements: 1 } : item, meta: { requestId: "test" } });
  }));
  return render(<FinanceWorkQueue token="token" organizationId="org-1" organizationName="示範球會" />);
}
beforeEach(() => {
  HTMLDialogElement.prototype.showModal = function showModal() { this.setAttribute("open", ""); };
  HTMLDialogElement.prototype.close = function close() { this.removeAttribute("open"); };
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe("FinanceWorkQueue readable, confirmed finance commands", () => {
  it("selects a readable receivable and auto-carries its payer into payment confirmation", async () => {
    renderQueue();
    expect(await screen.findByText(/王小明.*PB-101/)).toBeTruthy();
    fireEvent.click(screen.getAllByRole("button", { name: "查看詳情" })[0]);
    expect(await screen.findByText(/付款人會依此應收自動帶入：王小明/)).toBeTruthy();
    expect(screen.queryByLabelText("Receivable ID")).toBeNull();
    expect(screen.queryByLabelText("Payer user ID")).toBeNull();
    fireEvent.change(screen.getByLabelText("付款金額"), { target: { value: "600.00" } });
    fireEvent.change(screen.getByLabelText("付款時間"), { target: { value: "2026-08-26T10:00" } });
    fireEvent.submit(screen.getByRole("form", { name: "記錄付款" }));
    expect((await screen.findByRole("dialog", { name: "確認財務命令" })).textContent).toContain("王小明");
    expect(screen.getByRole("dialog", { name: "確認財務命令" }).textContent).toContain("600.00");
    fireEvent.click(screen.getByRole("button", { name: "取消" }));
  });

  it("starts a refund request from readable payment context, without receivable or payment UUID entry", async () => {
    renderQueue();
    await screen.findByText(/付款紀錄/);
    fireEvent.click(screen.getAllByRole("button", { name: "查看詳情" })[1]);
    expect(await screen.findByText(/關聯應收：AR-001/)).toBeTruthy();
    expect(screen.queryByLabelText("Receivable ID")).toBeNull();
    expect(screen.queryByLabelText("Payment ID")).toBeNull();
    fireEvent.change(screen.getByLabelText("退款金額"), { target: { value: "50.00" } });
    fireEvent.change(screen.getByLabelText("退款原因"), { target: { value: "停課退款" } });
    fireEvent.submit(screen.getByRole("form", { name: "提出退款申請" }));
    expect((await screen.findByRole("dialog", { name: "確認財務命令" })).textContent).toContain("PAY-001");
  });

  it("only offers the lifecycle-appropriate high-risk refund action", async () => {
    renderQueue();
    await screen.findByText(/退款待辦/);
    fireEvent.click(screen.getAllByRole("button", { name: "查看詳情" })[2]);
    expect(await screen.findByRole("form", { name: "審核退款" })).toBeTruthy();
    expect(screen.queryByRole("form", { name: "執行退款" })).toBeNull();
    fireEvent.change(screen.getByLabelText("審核原因"), { target: { value: "符合退款條件" } });
    fireEvent.submit(screen.getByRole("form", { name: "審核退款" }));
    expect((await screen.findByRole("dialog", { name: "確認財務命令" })).textContent).toContain("核准退款 RF-001");
  });
});
