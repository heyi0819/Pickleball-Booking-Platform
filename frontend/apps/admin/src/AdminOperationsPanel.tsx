import {
  ApiClientError,
  createApiClient,
  type AdminNotification,
  type AdminOutboxEvent,
} from "@pickleball/api-client";
import { ConfirmationDialog } from "@pickleball/ui";
import { useEffect, useState } from "react";

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
    if (!requestedScope.trim()) { setMessage("Organization ID is required."); return; }
    setState("loading"); setMessage("");
    const query = { organizationId: requestedScope.trim(), status: status || undefined, retryDue, size: 50 };
    try {
      const [events, deliveries] = await Promise.all([
        api.listAdminOutboxEvents(token, query), api.listAdminNotifications(token, query),
      ]);
      setOutbox(events.items); setNotifications(deliveries.items); setState("loaded");
    } catch (caught) {
      setState("error"); setMessage(errorMessage(caught, "Unable to load operational queues."));
    }
  }

  function prepareRecovery(kind: "outbox" | "notification", id: string, action: string) {
    const reason = reasons[id]?.trim();
    if (!reason) { setMessage("Enter an operator reason before recovery."); return; }
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
      setMessage("Recovery request accepted and audited.");
      setPending(null);
    } catch (caught) {
      setMessage(errorMessage(caught, "Recovery request failed."));
    }
  }

  return <section aria-label="Admin operations recovery">
    <h2>Operations and failure recovery</h2>
    <p>Operational queue only; recovery is limited to FAILED or DEAD items and requires an audit reason.</p>
    {platformAdmin ? <label>Organization scope <select value={scope} onChange={(event) => setScope(event.target.value)}><option value="">Select an authorized organization</option>{organizationOptions.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}</select></label>
      : <p>Organization scope: <code>{scope}</code></p>}
    {platformAdmin && !organizationOptions.length && <p role="alert">No readable organization scope is available for this account. An authorized organization context is required before operational data can be loaded.</p>}
    <label htmlFor="admin-recovery-state">Recovery state</label>
    <select id="admin-recovery-state" value={status} onChange={(event) => setStatus(event.target.value as (typeof statuses)[number])}>
      {statuses.map((item) => <option key={item || "ALL"} value={item}>{item || "ALL"}</option>)}
    </select>
    <label><input type="checkbox" checked={retryDue} onChange={(event) => setRetryDue(event.target.checked)} /> Retry due only</label>
    <button disabled={!scope.trim() || state === "loading"} onClick={() => void load()}>{state === "loading" ? "Loading…" : "Refresh operations"}</button>
    {message && <p aria-live="polite">{message}</p>}
    {state === "error" && <p role="alert">Operational data could not be loaded.</p>}
    <OperationalTable title="Outbox events" empty="No matching outbox events." rows={outbox.map((item) => ({
      id: item.id, status: item.status, attempts: item.attemptCount, context: `${item.eventType} · ${item.aggregateType} ${item.aggregateId}`,
      dueAt: item.availableAt, error: item.lastError, recoverable: item.status === "FAILED" || item.status === "DEAD",
      action: item.status === "DEAD" ? "Requeue event" : "Retry event",
    }))} reasons={reasons} setReasons={setReasons} recover={(id, action) => prepareRecovery("outbox", id, action)} />
    <OperationalTable title="Notifications" empty="No matching notifications." rows={notifications.map((item) => ({
      id: item.id, status: item.status, attempts: item.attemptCount, context: `${item.templateCode} · ${item.businessType} ${item.businessId}`,
      dueAt: item.nextAttemptAt, error: [item.lastErrorCode, item.lastErrorMessage].filter(Boolean).join(": ") || null,
      recoverable: item.status === "FAILED" || item.status === "DEAD",
      action: item.status === "DEAD" ? "Requeue notification" : "Redeliver notification",
    }))} reasons={reasons} setReasons={setReasons} recover={(id, action) => prepareRecovery("notification", id, action)} />
    <ConfirmationDialog open={pending !== null} title="Confirm recovery" description={pending ? `${pending.action}: ${pending.reason}` : ""} danger onConfirm={() => void recover()} onCancel={() => setPending(null)} />
  </section>;
}

type OperationalRow = { id: string; status: string; attempts: number; context: string; dueAt?: Date | null;
  error?: string | null; recoverable: boolean; action: string };

function OperationalTable({ title, empty, rows, reasons, setReasons, recover }: {
  title: string; empty: string; rows: OperationalRow[]; reasons: Record<string, string>;
  setReasons: React.Dispatch<React.SetStateAction<Record<string, string>>>; recover: (id: string, action: string) => void;
}) {
  return <section><h3>{title}</h3>{rows.length === 0 ? <p>{empty}</p> : <table><thead><tr>
    <th>Status</th><th>Context</th><th>Attempts / due</th><th>Error</th><th>Recovery</th>
  </tr></thead><tbody>{rows.map((row) => <tr key={row.id}>
    <td>{row.status}</td><td><code>{row.id}</code><br />{row.context}</td>
    <td>{row.attempts}<br />{row.dueAt ? new Date(row.dueAt).toLocaleString("zh-TW", { timeZone: "Asia/Taipei" }) : "—"}</td>
    <td>{row.error || "—"}</td><td>{row.recoverable ? <><label>Audit reason <input value={reasons[row.id] ?? ""}
      onChange={(event) => setReasons((current) => ({ ...current, [row.id]: event.target.value }))} maxLength={1000} /></label>
      <button disabled={!reasons[row.id]?.trim()} onClick={() => recover(row.id, row.action)}>{row.action}</button></> : "Not eligible"}</td>
  </tr>)}</tbody></table>}</section>;
}

function errorMessage(caught: unknown, fallback: string) {
  return caught instanceof ApiClientError ? `${fallback} ${caught.code}` : fallback;
}
