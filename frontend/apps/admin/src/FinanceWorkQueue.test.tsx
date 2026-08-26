import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { FinanceWorkQueue } from "./FinanceWorkQueue";

function form(container: HTMLElement, name: string) {
  return container.querySelector(`form[aria-label="${name}"]`) as HTMLFormElement;
}
function input(formElement: HTMLElement, name: string) {
  return formElement.querySelector(`input[name="${name}"]`) as HTMLInputElement;
}
function textarea(formElement: HTMLElement, name: string) {
  return formElement.querySelector(`textarea[name="${name}"]`) as HTMLTextAreaElement;
}

function expectConfirmation(text: string) {
  const dialog = screen.getByRole("dialog", { name: "Confirm finance command" });
  expect(dialog.textContent).toContain(text);
  fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
}

describe("FinanceWorkQueue confirmation gates", () => {
  it("requires secondary confirmation before recording a payment", () => {
    const { container } = render(<FinanceWorkQueue token="token" />);
    const payment = form(container, "Record payment");
    fireEvent.change(input(payment, "receivableId"), { target: { value: "11111111-1111-1111-1111-111111111111" } });
    fireEvent.change(input(payment, "payerUserId"), { target: { value: "22222222-2222-2222-2222-222222222222" } });
    fireEvent.change(input(payment, "amount"), { target: { value: "600.00" } });
    fireEvent.change(input(payment, "paidAt"), { target: { value: "2026-08-26T10:00" } });
    fireEvent.submit(payment);
    expectConfirmation("Record 600.00");
  });

  it("keeps refund request, review and execution behind separate confirmation gates", () => {
    const { container } = render(<FinanceWorkQueue token="token" />);

    const request = form(container, "Request refund");
    fireEvent.change(input(request, "receivableId"), { target: { value: "11111111-1111-1111-1111-111111111111" } });
    fireEvent.change(input(request, "paymentId"), { target: { value: "33333333-3333-3333-3333-333333333333" } });
    fireEvent.change(input(request, "amount"), { target: { value: "300.00" } });
    fireEvent.change(textarea(request, "reason"), { target: { value: "student withdrawal" } });
    fireEvent.submit(request);
    expectConfirmation("Request refund 300.00");

    const review = form(container, "Review refund");
    fireEvent.change(input(review, "refundId"), { target: { value: "44444444-4444-4444-4444-444444444444" } });
    fireEvent.change(textarea(review, "reason"), { target: { value: "approved" } });
    fireEvent.submit(review);
    expectConfirmation("APPROVE refund 44444444-4444-4444-4444-444444444444");

    const execution = form(container, "Execute refund");
    fireEvent.change(input(execution, "refundId"), { target: { value: "44444444-4444-4444-4444-444444444444" } });
    fireEvent.change(input(execution, "refundedAt"), { target: { value: "2026-08-26T10:30" } });
    fireEvent.submit(execution);
    expectConfirmation("Execute refund 44444444-4444-4444-4444-444444444444");
  });
});
