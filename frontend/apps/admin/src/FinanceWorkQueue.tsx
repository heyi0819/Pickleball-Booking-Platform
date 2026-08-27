import {
  ApiClientError,
  createApiClient,
  type FinancePaymentRequest,
  type FinanceRefundExecutionRequest,
  type FinanceRefundRequest,
  type FinanceRefundReviewRequest,
} from "@pickleball/api-client";
import { useState } from "react";

const api = createApiClient({ baseUrl: import.meta.env.VITE_API_BASE_URL ?? "/api/v1" });

type PendingCommand =
  | { kind: "payment"; receivableId: string; request: FinancePaymentRequest; idempotencyKey: string }
  | { kind: "refund-request"; receivableId: string; request: FinanceRefundRequest; idempotencyKey: string }
  | { kind: "refund-review"; refundId: string; request: FinanceRefundReviewRequest; idempotencyKey: string }
  | { kind: "refund-execution"; refundId: string; request: FinanceRefundExecutionRequest; idempotencyKey: string };

export function FinanceWorkQueue({ token }: { token: string }) {
  const [message, setMessage] = useState("");
  const [pending, setPending] = useState<PendingCommand | null>(null);
  const [busy, setBusy] = useState(false);
  const [lastRefundId, setLastRefundId] = useState("");

  async function executePending() {
    if (!pending || busy) return;
    setBusy(true);
    try {
      if (pending.kind === "payment") {
        const result = await api.recordReceivablePayment(token, pending.receivableId, pending.idempotencyKey, pending.request);
        setMessage(`Payment recorded: ${result.paymentId}. Outstanding ${result.outstandingAmount}.`);
      } else if (pending.kind === "refund-request") {
        const result = await api.requestReceivableRefund(token, pending.receivableId, pending.idempotencyKey, pending.request);
        setLastRefundId(result.refundId);
        setMessage(`Refund requested: ${result.refundId} (${result.status}).`);
      } else if (pending.kind === "refund-review") {
        const result = await api.reviewRefund(token, pending.refundId, pending.idempotencyKey, pending.request);
        setLastRefundId(result.refundId);
        setMessage(`Refund review saved: ${result.refundId} (${result.status}).`);
      } else {
        const result = await api.executeRefund(token, pending.refundId, pending.idempotencyKey, pending.request);
        setLastRefundId(result.refundId);
        setMessage(`Refund execution saved: ${result.refundId} (${result.status}).`);
      }
      setPending(null);
    } catch (caught) {
      setMessage(commandError(caught, "Finance command failed."));
    } finally {
      setBusy(false);
    }
  }

  function preparePayment(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const receivableId = String(data.get("receivableId")).trim();
    const amount = String(data.get("amount")).trim();
    const paidAt = new Date(String(data.get("paidAt")));
    const request = {
      amount,
      method: String(data.get("method")),
      paidAt,
      payerUserId: String(data.get("payerUserId")).trim(),
      note: String(data.get("note") ?? "").trim() || undefined,
    } as FinancePaymentRequest;
    setPending({ kind: "payment", receivableId, request, idempotencyKey: key("payment", receivableId, amount, paidAt.toISOString()) });
  }

  function prepareRefundRequest(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const receivableId = String(data.get("receivableId")).trim();
    const paymentId = String(data.get("paymentId")).trim();
    const amount = String(data.get("amount")).trim();
    const request = { paymentId, amount, reason: String(data.get("reason")).trim() } as FinanceRefundRequest;
    setPending({ kind: "refund-request", receivableId, request, idempotencyKey: key("refund-request", receivableId, paymentId, amount, request.reason) });
  }

  function prepareRefundReview(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const refundId = String(data.get("refundId")).trim();
    const request = { decision: String(data.get("decision")), reason: String(data.get("reason")).trim() } as FinanceRefundReviewRequest;
    setPending({ kind: "refund-review", refundId, request, idempotencyKey: key("refund-review", refundId, request.decision, request.reason) });
  }

  function prepareRefundExecution(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const refundId = String(data.get("refundId")).trim();
    const refundedAt = new Date(String(data.get("refundedAt")));
    const request = {
      method: String(data.get("method")),
      refundedAt,
      reference: String(data.get("reference") ?? "").trim() || undefined,
    } as FinanceRefundExecutionRequest;
    setPending({ kind: "refund-execution", refundId, request, idempotencyKey: key("refund-execution", refundId, request.method, refundedAt.toISOString(), request.reference ?? "") });
  }

  return <section aria-label="Finance operations">
    <h2>Finance operations</h2>
    <p>Slice 6 closure workspace. Use known receivable, payment and refund IDs; Finance queue/read-side APIs are not invented here.</p>
    <p>Cash is the MVP default. Bank transfer means a transaction completed outside the platform and recorded here afterward; this screen does not initiate bank transfers.</p>
    {message && <p role="status">{message}</p>}
    {lastRefundId && <p>Last refund ID: <code>{lastRefundId}</code></p>}

    <h3>Record payment</h3>
    <form aria-label="Record payment" onSubmit={preparePayment}>
      <label>Receivable ID<input name="receivableId" required /></label>
      <label>Payer user ID<input name="payerUserId" required /></label>
      <label>Amount<input name="amount" inputMode="decimal" pattern="[0-9]+([.][0-9]{1,2})?" required /></label>
      <label>Method<select name="method" defaultValue="CASH"><option value="CASH">Cash</option><option value="BANK_TRANSFER">Bank transfer (record only)</option><option value="OTHER">Other</option></select></label>
      <label>Paid at<input name="paidAt" type="datetime-local" required /></label>
      <label>Note<input name="note" maxLength={5000} /></label>
      <button disabled={busy}>Review payment</button>
    </form>

    <h3>Request refund</h3>
    <form aria-label="Request refund" onSubmit={prepareRefundRequest}>
      <label>Receivable ID<input name="receivableId" required /></label>
      <label>Payment ID<input name="paymentId" required /></label>
      <label>Amount<input name="amount" inputMode="decimal" pattern="[0-9]+([.][0-9]{1,2})?" required /></label>
      <label>Reason<textarea name="reason" required maxLength={5000} /></label>
      <button disabled={busy}>Review refund request</button>
    </form>

    <h3>Review refund</h3>
    <form aria-label="Review refund" onSubmit={prepareRefundReview}>
      <label>Refund ID<input name="refundId" defaultValue={lastRefundId} required /></label>
      <label>Decision<select name="decision" defaultValue="APPROVE"><option value="APPROVE">Approve</option><option value="REJECT">Reject</option></select></label>
      <label>Reason<textarea name="reason" required maxLength={5000} /></label>
      <button disabled={busy}>Review decision</button>
    </form>

    <h3>Execute approved refund</h3>
    <form aria-label="Execute refund" onSubmit={prepareRefundExecution}>
      <label>Refund ID<input name="refundId" defaultValue={lastRefundId} required /></label>
      <label>Method<select name="method" defaultValue="CASH"><option value="CASH">Cash</option><option value="BANK_TRANSFER">Bank transfer (record only)</option><option value="OTHER">Other</option></select></label>
      <label>Refunded at<input name="refundedAt" type="datetime-local" required /></label>
      <label>Reference<input name="reference" maxLength={100} /></label>
      <button disabled={busy}>Review refund execution</button>
    </form>

    {pending && <section role="dialog" aria-label="Confirm finance command">
      <h3>Confirm finance command</h3>
      <p>{confirmationText(pending)}</p>
      <p>This action is idempotency-protected and will be written to the financial audit/outbox trail.</p>
      <button disabled={busy} onClick={() => void executePending()}>{busy ? "Saving…" : "Confirm command"}</button>
      <button disabled={busy} onClick={() => setPending(null)}>Cancel</button>
    </section>}
  </section>;
}

function key(...parts: string[]) {
  return `admin-${parts[0]}-${crypto.randomUUID()}`;
}

function confirmationText(command: PendingCommand) {
  if (command.kind === "payment") return `Record ${command.request.amount} to receivable ${command.receivableId}.`;
  if (command.kind === "refund-request") return `Request refund ${command.request.amount} against payment ${command.request.paymentId}.`;
  if (command.kind === "refund-review") return `${command.request.decision} refund ${command.refundId}.`;
  return `Execute refund ${command.refundId} using ${command.request.method}.`;
}

function commandError(caught: unknown, fallback: string) {
  return caught instanceof ApiClientError ? `${fallback} ${caught.code}` : fallback;
}