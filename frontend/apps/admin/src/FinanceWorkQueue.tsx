import {
  ApiClientError,
  createApiClient,
  type AdminFinancePayment,
  type AdminFinanceReceivable,
  type AdminFinanceRefund,
} from "@pickleball/api-client";
import { ConfirmationDialog } from "@pickleball/ui";
import { useEffect, useState } from "react";

const api = createApiClient({ baseUrl: import.meta.env.VITE_API_BASE_URL ?? "/api/v1" });
type FinanceItem = AdminFinanceReceivable | AdminFinancePayment | AdminFinanceRefund;
type FinancePaymentMethod = "CASH" | "BANK_TRANSFER" | "OTHER";
type FinanceReference = Pick<AdminFinanceReceivable, "id" | "memberId" | "memberName" | "courseNo" | "currency">;
type Selection = { kind: "receivable"; item: AdminFinanceReceivable } | { kind: "payment"; item: AdminFinancePayment } | { kind: "refund"; item: AdminFinanceRefund };
type PendingCommand =
  | { kind: "payment"; receivable: FinanceReference; amount: string; method: FinancePaymentMethod; paidAt: Date; note?: string }
  | { kind: "refund-request"; receivable: FinanceReference; payment: AdminFinancePayment; amount: string; reason: string }
  | { kind: "refund-review"; refund: AdminFinanceRefund; decision: "APPROVE" | "REJECT"; reason: string }
  | { kind: "refund-execution"; refund: AdminFinanceRefund; method: FinancePaymentMethod; refundedAt: Date; reference?: string };

export function FinanceWorkQueue({ token, organizationId, organizationName }: { token: string; organizationId: string; organizationName?: string | null }) {
  const [receivables, setReceivables] = useState<AdminFinanceReceivable[]>([]);
  const [payments, setPayments] = useState<AdminFinancePayment[]>([]);
  const [refunds, setRefunds] = useState<AdminFinanceRefund[]>([]);
  const [selection, setSelection] = useState<Selection | null>(null);
  const [pending, setPending] = useState<PendingCommand | null>(null);
  const [state, setState] = useState<"loading" | "loaded" | "error">("loading");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");

  const load = async () => {
    setState("loading");
    try {
      const query = { organizationId, size: 20, page: 0 };
      const [ar, pay, refund] = await Promise.all([api.listAdminReceivables(token, query), api.listAdminPayments(token, query), api.listAdminRefunds(token, query)]);
      setReceivables(ar.items); setPayments(pay.items); setRefunds(refund.items); setState("loaded");
    } catch (caught) { setState("error"); setMessage(errorMessage(caught, "無法載入財務工作清單。")); }
  };
  useEffect(() => { void load(); }, [token, organizationId]);

  async function open(kind: Selection["kind"], id: string) {
    try {
      const item = kind === "receivable" ? await api.getAdminReceivable(token, organizationId, id)
        : kind === "payment" ? await api.getAdminPayment(token, organizationId, id) : await api.getAdminRefund(token, organizationId, id);
      setSelection({ kind, item } as Selection);
    } catch (caught) { setMessage(errorMessage(caught, "無法載入財務詳情。")); }
  }

  async function execute() {
    if (!pending || busy) return;
    setBusy(true);
    try {
      if (pending.kind === "payment") {
        const result = await api.recordReceivablePayment(token, pending.receivable.id, key("payment"), { amount: pending.amount, method: pending.method, paidAt: pending.paidAt, payerUserId: pending.receivable.memberId, note: pending.note });
        setMessage(`已記錄付款；尚欠 ${result.outstandingAmount}。`);
      } else if (pending.kind === "refund-request") {
        const result = await api.requestReceivableRefund(token, pending.receivable.id, key("refund-request"), { paymentId: pending.payment.id, amount: pending.amount, reason: pending.reason });
        setMessage(`退款申請已建立（${result.status}）。`);
      } else if (pending.kind === "refund-review") {
        const result = await api.reviewRefund(token, pending.refund.id, key("refund-review"), { decision: pending.decision, reason: pending.reason });
        setMessage(`退款審核已完成（${result.status}）。`);
      } else {
        const result = await api.executeRefund(token, pending.refund.id, key("refund-execution"), { method: pending.method, refundedAt: pending.refundedAt, reference: pending.reference });
        setMessage(`退款執行已完成（${result.status}）。`);
      }
      setPending(null); setSelection(null); await load();
    } catch (caught) { setMessage(errorMessage(caught, "財務命令未完成。")); } finally { setBusy(false); }
  }

  function preparePayment(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (selection?.kind !== "receivable") return;
    const data = new FormData(event.currentTarget); const paidAt = new Date(String(data.get("paidAt")));
    if (!validDate(paidAt)) { setMessage("請選擇有效的付款時間。"); return; }
    setPending({ kind: "payment", receivable: selection.item, amount: String(data.get("amount")).trim(), method: String(data.get("method")) as FinancePaymentMethod, paidAt, note: String(data.get("note") ?? "").trim() || undefined });
  }
  function prepareRefundRequest(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (selection?.kind !== "payment") return;
    const reference = selection.item.receivables[0];
    if (!reference) { setMessage("此付款沒有可讀取的應收關聯，無法從工作清單提出退款。"); return; }
    const data = new FormData(event.currentTarget);
    setPending({ kind: "refund-request", receivable: { id: reference.id, memberId: selection.item.memberId, courseNo: reference.courseNo, memberName: selection.item.memberName, currency: selection.item.currency }, payment: selection.item, amount: String(data.get("amount")).trim(), reason: String(data.get("reason")).trim() });
  }
  function prepareReview(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (selection?.kind !== "refund") return; const data = new FormData(event.currentTarget);
    setPending({ kind: "refund-review", refund: selection.item, decision: String(data.get("decision")) as "APPROVE" | "REJECT", reason: String(data.get("reason")).trim() });
  }
  function prepareExecution(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (selection?.kind !== "refund") return; const data = new FormData(event.currentTarget); const refundedAt = new Date(String(data.get("refundedAt")));
    if (!validDate(refundedAt)) { setMessage("請選擇有效的退款時間。"); return; }
    setPending({ kind: "refund-execution", refund: selection.item, method: String(data.get("method")) as FinancePaymentMethod, refundedAt, reference: String(data.get("reference") ?? "").trim() || undefined });
  }

  return <section aria-label="財務工作清單"><h2>財務工作清單</h2>
    <p>組織：{organizationName || organizationId}。金額與狀態由帳務系統決定；此頁只呈現可讀資訊與啟動既有命令。</p>
    <p>現金為 MVP 預設；銀行轉帳僅記錄平台外已完成的交易，不會在此發起轉帳。</p>
    <button onClick={() => void load()} disabled={state === "loading" || busy}>{state === "loading" ? "載入中…" : "重新整理財務清單"}</button>
    {message && <p role={state === "error" ? "alert" : "status"}>{message}</p>}{state === "error" && <p>財務資料暫時無法載入。</p>}{state === "loading" && <p aria-live="polite">正在載入應收、付款與退款工作…</p>}
    {state === "loaded" && <div>
      <Queue title="待收應收" empty="目前沒有可顯示的應收。" items={receivables} summary={(item) => `${item.memberName} · ${item.courseNo} · ${item.currency} ${item.outstandingAmount} 未收 · ${statusLabel(item.status)}`} onOpen={(item) => void open("receivable", item.id)} />
      <Queue title="付款紀錄" empty="目前沒有可顯示的付款。" items={payments} summary={(item) => `${item.memberName} · ${item.currency} ${item.amount} · 可退款參考 ${item.refundableAmount} · ${statusLabel(item.status)}`} onOpen={(item) => void open("payment", item.id)} />
      <Queue title="退款待辦" empty="目前沒有可顯示的退款。" items={refunds} summary={(item) => `${item.memberName} · ${item.currency} ${item.amount} · ${statusLabel(item.status)}`} onOpen={(item) => void open("refund", item.id)} />
    </div>}
    {selection && <FinanceDetail selection={selection} onClose={() => setSelection(null)} onPayment={preparePayment} onRefundRequest={prepareRefundRequest} onReview={prepareReview} onExecution={prepareExecution} busy={busy} />}
    <ConfirmationDialog open={pending !== null} title="確認財務命令" description={pending ? confirmation(pending) : ""} confirmLabel={busy ? "處理中…" : "確認送出"} cancelLabel="取消" danger={pending?.kind !== "payment"} onConfirm={() => void execute()} onCancel={() => !busy && setPending(null)} />
  </section>;
}

function Queue<T extends FinanceItem>({ title, empty, items, summary, onOpen }: { title: string; empty: string; items: T[]; summary: (item: T) => string; onOpen: (item: T) => void }) {
  return <section><h3>{title}</h3>{items.length === 0 ? <p>{empty}</p> : <ul>{items.map((item) => <li key={item.id}><button onClick={() => onOpen(item)}>查看詳情</button> {summary(item)} <small>技術編號：<code>{item.id}</code></small></li>)}</ul>}</section>;
}
function FinanceDetail({ selection, onClose, onPayment, onRefundRequest, onReview, onExecution, busy }: { selection: Selection; onClose: () => void; onPayment: (event: React.FormEvent<HTMLFormElement>) => void; onRefundRequest: (event: React.FormEvent<HTMLFormElement>) => void; onReview: (event: React.FormEvent<HTMLFormElement>) => void; onExecution: (event: React.FormEvent<HTMLFormElement>) => void; busy: boolean }) {
  const { item } = selection;
  const receivable = selection.kind === "receivable" ? selection.item : null;
  const payment = selection.kind === "payment" ? selection.item : null;
  const refund = selection.kind === "refund" ? selection.item : null;
  return <section aria-label="財務詳情"><h3>{selection.kind === "receivable" ? "應收詳情" : selection.kind === "payment" ? "付款詳情" : "退款詳情"}</h3><p>會員：{item.memberName} 組織：{item.organizationName} 幣別：{item.currency}</p><p>狀態：{statusLabel(item.status)}</p>
    {receivable && <><p>課程：{receivable.courseNo} 應收總額：{receivable.totalAmount} 已付：{receivable.paidAmount} 尚欠：{receivable.outstandingAmount}</p><form aria-label="記錄付款" onSubmit={onPayment}><h4>記錄付款</h4><p>付款人會依此應收自動帶入：{receivable.memberName}</p><AmountField label="付款金額" name="amount" max={receivable.outstandingAmount} /><MethodField /><label>付款時間<input name="paidAt" type="datetime-local" required /></label><label>備註（選填）<input name="note" maxLength={5000} /></label><button disabled={busy || receivable.status === "CANCELLED" || receivable.status === "REFUNDED"}>確認付款內容</button></form></>}
    {payment && <><p>付款：{payment.paymentNo} 金額：{payment.amount} 可退款參考：{payment.refundableAmount}</p><p>關聯應收：{payment.receivables.length ? payment.receivables.map((ref) => `${ref.receivableNo}（${ref.courseNo}）`).join("、") : "無可讀關聯"}</p><form aria-label="提出退款申請" onSubmit={onRefundRequest}><h4>提出退款申請</h4><AmountField label="退款金額" name="amount" max={payment.refundableAmount} /><label>退款原因<textarea name="reason" required maxLength={5000} /></label><button disabled={busy || payment.receivables.length === 0 || !["COMPLETED", "PARTIALLY_REFUNDED"].includes(payment.status)}>確認退款內容</button></form></>}
    {refund && <><p>退款：{refund.refundNo} 付款：{refund.paymentNo} 金額：{refund.amount} 可退款參考：{refund.refundableAmount}</p><p>原因：{refund.reason}</p>{refund.status === "PENDING_APPROVAL" && <form aria-label="審核退款" onSubmit={onReview}><h4>審核退款</h4><label>決定<select name="decision" defaultValue="APPROVE"><option value="APPROVE">核准</option><option value="REJECT">駁回</option></select></label><label>審核原因<textarea name="reason" required maxLength={5000} /></label><button disabled={busy}>確認審核內容</button></form>}{refund.status === "APPROVED" && <form aria-label="執行退款" onSubmit={onExecution}><h4>執行退款</h4><MethodField /><label>退款時間<input name="refundedAt" type="datetime-local" required /></label><label>外部參考（選填）<input name="reference" maxLength={100} /></label><button disabled={busy}>確認執行內容</button></form>}</>}
    <button onClick={onClose} disabled={busy}>返回財務工作清單</button></section>;
}
function AmountField({ label, name, max }: { label: string; name: string; max: string }) { return <label>{label}<input name={name} inputMode="decimal" pattern="[0-9]+([.][0-9]{1,2})?" min="0.01" max={max} required /></label>; }
function MethodField() { return <label>方式<select name="method" defaultValue="CASH"><option value="CASH">現金</option><option value="BANK_TRANSFER">銀行轉帳（僅記錄）</option><option value="OTHER">其他</option></select></label>; }
function confirmation(command: PendingCommand) { if (command.kind === "payment") return `將為 ${command.receivable.memberName} 的 ${command.receivable.courseNo} 記錄 ${command.receivable.currency} ${command.amount} 付款。系統會再次驗證金額與應收狀態。`; if (command.kind === "refund-request") return `將對 ${command.payment.memberName} 的付款 ${command.payment.paymentNo} 申請 ${command.payment.currency} ${command.amount} 退款。系統會再次驗證可退款額。`; if (command.kind === "refund-review") return `將${command.decision === "APPROVE" ? "核准" : "駁回"}退款 ${command.refund.refundNo}。此動作會留下既有稽核紀錄。`; return `將執行退款 ${command.refund.refundNo}，金額 ${command.refund.currency} ${command.refund.amount}。系統會再次驗證退款狀態。`; }
function statusLabel(status: string) { return ({ OPEN: "待收", PARTIALLY_PAID: "部分付款", PAID: "已付清", OVERDUE: "逾期", CANCELLED: "已取消", REFUNDED: "已退款", COMPLETED: "已完成", PARTIALLY_REFUNDED: "部分退款", VOIDED: "已作廢", PENDING_APPROVAL: "待審核", APPROVED: "已核准", REJECTED: "已駁回", FAILED: "失敗" } as Record<string, string>)[status] ?? status; }
function key(kind: string) { return `admin-${kind}-${crypto.randomUUID()}`; }
function validDate(value: Date) { return Number.isFinite(value.getTime()); }
function errorMessage(caught: unknown, fallback: string) { return caught instanceof ApiClientError ? `${fallback}（${caught.code}）` : fallback; }
