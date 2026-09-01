import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Alert, Button, ConfirmationDialog, FormField } from "./index";

describe("UI foundation primitives", () => {
  it("exposes loading state and accessible form errors", () => { render(<><Button loading>儲存</Button><FormField label="電子郵件" error="請輸入有效電子郵件" required><input /></FormField><Alert tone="danger">操作失敗</Alert></>); expect(screen.getByRole("button").disabled).toBe(true); expect(screen.getByLabelText(/電子郵件/).getAttribute("aria-invalid")).toBe("true"); expect(screen.getByRole("alert").textContent).toContain("操作失敗"); });
  it("focuses the confirmation action and invokes cancellation", () => { HTMLDialogElement.prototype.showModal = function showModal() { this.setAttribute("open", ""); }; HTMLDialogElement.prototype.close = function close() { this.removeAttribute("open"); }; const cancel = vi.fn(); render(<ConfirmationDialog open title="確認取消" description="請確認操作。" onConfirm={vi.fn()} onCancel={cancel} />); expect(document.activeElement).toBe(screen.getByRole("button", { name: "確認" })); fireEvent.click(screen.getByRole("button", { name: "取消" })); expect(cancel).toHaveBeenCalledOnce(); });
});
