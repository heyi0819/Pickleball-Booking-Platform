import {
  createApiClient,
  type AdminNotification,
  type AdminOutboxEvent,
} from "@pickleball/api-client";
import { ConfirmationDialog } from "@pickleball/ui";
import { useEffect, useState } from "react";
import { formatTaipeiDateTime, statusLabel } from "@pickleball/shared";

const api = createApiClient({ baseUrl: import.meta.env.VITE_API_BASE_URL ?? "/api/v1" });
const statuses = ["", "PENDING", "FAILED", "DEAD"] as const;

export function AdminOperationsPanel({ token, organizationId, platformAdmin, organizationOptions = [] }: {
  token: string;
  organizationId?: string;
  platformAdmin: boolean;
  organizationOptions?: { id: string; name: string }[];
}) {
  const [scope, setScope] = useState(organizationId ?? "");
  const [status, setStatus] = useState<(typeof statuses)[number]>("FAILED");
  const [retryDue, setRetryDue] = useState(false);
  const [outbox, setOutbox] = useState<AdminOutboxEvent[]>([]);
  const [notifications, setNotifications] = useState<AdminNotification[]>([]);
  const [reasons, setReasons] = useState<Record<string, string>>({});
  const [state, setState] = useState<"idle" | "loading" | "loaded" | "error">("idle");
  const [message, setMessage] = useState("");
  const [pending, setPending] = useState<{ kind: "outbox" | "notification"; id: string; reason: string; action: string } | null>(null);

  useEffect(() => { if (organizationId) setScope(organizationId); }, [organizationId]);
  useEffect(() => { if (organizationId) void load(organizationId); }, [organizationId, status, retryDue]);

  async function load(requestedScope = scope) {
    if (!requestedScope.trim()) { setMessage("請先選擇組織範圍。"); return; }
    setState("loading"); setMessage("");
    const query = { organizationId: requestedScope.trim(), status: status || undefined, retryDue, size: 50 };
    try {
      const [events, deliveries] = await Promise.all([
        api.listAdminOutboxEvents(token, query), api.listAdminNotifications(token, query),
      ]);
      setOutbox(events.items); setNotifications(deliveries.items); setState("loaded");
    } catch (caught) {
      setState("error"); setMessage(errorMessage(caught, "無法載入營運待辦，請稍後再試。"));
    }
  }

  function prepareRecovery(kind: "outbox" | "notification", id: string, action: string) {
    const reason = reasons[id]?.trim();
    if (!reason) { setMessage("請先填寫處理原因。"); return; }
    setPending({ kind, id, reason, action });
  }

  async function recover() {
    if (!pending) return;
    const { kind, id, reason } = pending;
    setMessage("");
    try {
      const key = `admin-recovery-${id}-${crypto.randomUUID()}`;
      if (kind === "outbox") await api.retryAdminOutboxEvent(token, id, key, reason);
      else await api.retryAdminNotification(token, id, key, reason);
      setReasons((current) => ({ ...current, [id]: "" }));
      await load();
      setMessage("處理請求已送出並留下稽核紀錄。");
      setPending(null);
    } catch (caught) {
      setMessage(errorMessage(caught, "處理請求失敗，請稍後再試。"));
    }
  }

  return <section aria-label="Admin operations recovery">
    <h2>營運待辦與失敗復原</h2>
    <p>僅處理營運待辦；失敗或無法處理的項目需填寫稽核原因才能復原。</p>
    {platformAdmin ? <label>組織範圍 <select value={scope} onChange={(event) => setScope(event.target.value)}><option value="">選擇可用的組織</option>{organizationOptions.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}</select></label>
      : <p>組織範圍：<code>{scope}</code></p>}
    {platformAdmin && !organizationOptions.length && <p role="alert">目前帳號沒有可用的組織範圍，無法載入營運資料。</p>}
    <label htmlFor="admin-recovery-state">處理狀態</label>
    <select id="admin-recovery-state" value={status} onChange={(event) => setStatus(event.target.value as (typeof statuses)[number])}>
      {statuses.map((item) => <option key={item || "ALL"} value={item}>{item ? statusLabel(item) : "全部"}</option>)}
    </select>
    <label><input type="checkbox" checked={retryDue} onChange={(event) => setRetryDue(event.target.checked)} /> 僅顯示到期項目</label>
    <button disabled={!scope.trim() || state === "loading"} onClick={() => void load()}>{state === "loading" ? "載入中…" : "重新整理營運待辦"}</button>
    {message && <p aria-live="polite">{message}</p>}
    {state === "error" && <p role="alert">營運資料暫時無法載入。</p>}
    <OperationalTable title="待處理事件" empty="目前沒有符合條件的事件。" rows={outbox.map((item) => ({
      id: item.id, status: item.status, attempts: item.attemptCount, context: `${item.eventType} · ${item.aggregateType} ${item.aggregateId}`,
      dueAt: item.availableAt, error: item.lastError, recoverable: item.status === "FAILED" || item.status === "DEAD",
      action: item.status === "DEAD" ? "重新排入事件" : "重試事件",
    }))} reasons={reasons} setReasons={setReasons} recover={(id, action) => prepareRecovery("outbox", id, action)} />
    <OperationalTable title="通知" empty="目前沒有符合條件的通知。" rows={notifications.map((item) => ({
      id: item.id, status: item.status, attempts: item.attemptCount, context: `${item.templateCode} · ${item.businessType} ${item.businessId}`,
      dueAt: item.nextAttemptAt, error: [item.lastErrorCode, item.lastErrorMessage].filter(Boolean).join(": ") || null,
      recoverable: item.status === "FAILED" || item.status === "DEAD",
      action: item.status === "DEAD" ? "重新排入通知" : "重新傳送通知",
    }))} reasons={reasons} setReasons={setReasons} recover={(id, action) => prepareRecovery("notification", id, action)} />
    <ConfirmationDialog open={pending !== null} title="確認復原" description={pending ? `${pending.action}：${pending.reason}` : ""} confirmLabel="確認" cancelLabel="取消" danger onConfirm={() => void recover()} onCancel={() => setPending(null)} />
  </section>;
}

type OperationalRow = { id: string; status: string; attempts: number; context: string; dueAt?: Date | null;
  error?: string | null; recoverable: boolean; action: string };

function OperationalTable({ title, empty, rows, reasons, setReasons, recover }: {
  title: string; empty: string; rows: OperationalRow[]; reasons: Record<string, string>;
  setReasons: React.Dispatch<React.SetStateAction<Record<string, string>>>; recover: (id: string, action: string) => void;
}) {
  return <section><h3>{title}</h3>{rows.length === 0 ? <p>{empty}</p> : <table><thead><tr>
    <th>狀態</th><th>內容</th><th>嘗試次數／到期時間</th><th>錯誤</th><th>復原</th>
  </tr></thead><tbody>{rows.map((row) => <tr key={row.id}>
    <td>{statusLabel(row.status)}</td><td>{row.context}</td>
    <td>{row.attempts}<br />{row.dueAt ? formatTaipeiDateTime(row.dueAt) : "—"}</td>
    <td>{row.error || "—"}</td><td>{row.recoverable ? <><label>Audit reason <input value={reasons[row.id] ?? ""}
      onChange={(event) => setReasons((current) => ({ ...current, [row.id]: event.target.value }))} maxLength={1000} aria-label="稽核原因" /></label>
      <button disabled={!reasons[row.id]?.trim()} onClick={() => recover(row.id, row.action)}>{row.action}</button></> : "目前不可處理"}</td>
  </tr>)}</tbody></table>}</section>;
}

function errorMessage(caught: unknown, fallback: string) {
  return fallback;
}
