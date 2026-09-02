import {
  ApiClientError,
  createApiClient,
  type CoachCancellationReviewQueueItem,
  type CourseSessionSummary,
  type SessionChangeReviewQueueItem,
} from "@pickleball/api-client";
import { useEffect, useState } from "react";
import { presentApiError } from "@pickleball/shared";

const api = createApiClient({ baseUrl: import.meta.env.VITE_API_BASE_URL ?? "/api/v1" });
type SessionRow = CourseSessionSummary & { courseNo: string };
type RescheduleDraft = { sessionId: string; label: string; startAt: Date; endAt: Date; reason: string };
type ReviewDraft = { kind: "change" | "cancellation"; requestId: string; decision: "APPROVE" | "REJECT"; label: string };

async function loadCourseRows(token: string) {
  const courses = await api.listCourses(token, { status: "ACTIVE", size: 100, sort: "nextSessionAt,asc" });
  const rows = await Promise.all(courses.map(async (course) => (await api.listCourseSessions(token, course.id)).map((session) => ({ ...session, courseNo: course.courseNo }))));
  return { courses, sessions: rows.flat() };
}

export function StudentCourseOperations({ token }: { token: string }) {
  const [sessions, setSessions] = useState<SessionRow[]>([]);
  const [message, setMessage] = useState("");
  const [cancel, setCancel] = useState<SessionRow | null>(null);
  const [reschedule, setReschedule] = useState<RescheduleDraft | null>(null);
  const refresh = async () => setSessions((await loadCourseRows(token)).sessions);
  useEffect(() => { void refresh().catch(() => setMessage("無法載入正式課程。")); }, [token]);
  async function confirmCancellation(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!cancel?.ownEnrollmentId) return; const form = new FormData(event.currentTarget);
    try { await api.cancelSessionEnrollment(token, cancel.ownEnrollmentId, String(form.get("reason")) || null); setMessage("本堂報名已取消，其他堂次不受影響。"); setCancel(null); await refresh(); }
    catch (caught) { setMessage(operationError(caught, "無法取消本堂報名。")); }
  }
  async function confirmReschedule() {
    if (!reschedule) return;
    try { await api.createSessionRescheduleRequest(token, reschedule.sessionId, `liff-student-reschedule-${reschedule.sessionId}-${reschedule.startAt.toISOString()}`, reschedule.startAt, reschedule.endAt, reschedule.reason); setMessage("改期申請已送出，等待委員會審核。"); setReschedule(null); }
    catch (caught) { setMessage(operationError(caught, "無法送出改期申請。")); }
  }
  return <section aria-label="Student course operations"><h3>我的正式課程</h3>{message && <p role="status">{message}</p>}{sessions.length === 0 ? <p>目前沒有進行中的正式課程。</p> : <ul>{sessions.map((session) => <li key={session.id}><strong>{session.courseNo} · 第 {session.sequenceNo} 堂</strong> · {formatDate(session.scheduledStartAt)} · {session.venueName ?? "場地待確認"} · {session.ownEnrollmentStatus ?? "-"}{session.ownEnrollmentId && session.ownEnrollmentStatus === "SCHEDULED" && <button onClick={() => setCancel(session)}>取消本堂報名</button>}{["SCHEDULED", "POSTPONED"].includes(session.status) && <RescheduleForm session={session} onPrepare={setReschedule} />}</li>)}</ul>}{cancel && <section role="dialog" aria-label="確認取消本堂報名"><h4>確認取消本堂報名</h4><p>{cancel.courseNo} 第 {cancel.sequenceNo} 堂，{formatDate(cancel.scheduledStartAt)}</p><form onSubmit={confirmCancellation}><label>原因（選填）<textarea name="reason" maxLength={5000} /></label><button>確認取消</button><button type="button" onClick={() => setCancel(null)}>返回</button></form></section>}{reschedule && <ConfirmReschedule draft={reschedule} onConfirm={() => void confirmReschedule()} onCancel={() => setReschedule(null)} />}</section>;
}

export function CoachCourseOperations({ token }: { token: string }) {
  const [sessions, setSessions] = useState<SessionRow[]>([]);
  const [message, setMessage] = useState("");
  const [cancellation, setCancellation] = useState<{ session: SessionRow; reason: string } | null>(null);
  const [reschedule, setReschedule] = useState<RescheduleDraft | null>(null);
  const refresh = async () => setSessions((await loadCourseRows(token)).sessions);
  useEffect(() => { void refresh().catch(() => setMessage("無法載入授課課程。")); }, [token]);
  async function confirmCancellation() {
    if (!cancellation) return;
    try { await api.requestCoachSessionCancellation(token, cancellation.session.id, cancellation.reason); setMessage("取消授課申請已送出，等待委員會審核。"); setCancellation(null); await refresh(); }
    catch (caught) { setMessage(operationError(caught, "無法送出取消授課申請。")); }
  }
  async function confirmReschedule() {
    if (!reschedule) return;
    try { await api.createSessionRescheduleRequest(token, reschedule.sessionId, `liff-coach-reschedule-${reschedule.sessionId}-${reschedule.startAt.toISOString()}`, reschedule.startAt, reschedule.endAt, reschedule.reason); setMessage("改期申請已送出，等待委員會審核。"); setReschedule(null); }
    catch (caught) { setMessage(operationError(caught, "無法送出改期申請。")); }
  }
  return <section aria-label="Coach course operations"><h3>我的授課課程</h3>{message && <p role="status">{message}</p>}{sessions.length === 0 ? <p>目前沒有進行中的授課課程。</p> : <ul>{sessions.map((session) => <li key={session.id}><strong>{session.courseNo} · 第 {session.sequenceNo} 堂</strong> · {formatDate(session.scheduledStartAt)} · {session.status}<CancellationForm session={session} onPrepare={(reason) => setCancellation({ session, reason })} /><RescheduleForm session={session} onPrepare={setReschedule} /></li>)}</ul>}{cancellation && <section role="dialog" aria-label="確認取消授課申請"><h4>確認取消授課申請</h4><p>{cancellation.session.courseNo} 第 {cancellation.session.sequenceNo} 堂</p><p>原因：{cancellation.reason}</p><button onClick={() => void confirmCancellation()}>確認送出</button><button onClick={() => setCancellation(null)}>返回</button></section>}{reschedule && <ConfirmReschedule draft={reschedule} onConfirm={() => void confirmReschedule()} onCancel={() => setReschedule(null)} />}</section>;
}

export function CommitteeCourseOperations({ token, organizationId }: { token: string; organizationId: string }) {
  const [changes, setChanges] = useState<SessionChangeReviewQueueItem[]>([]);
  const [cancellations, setCancellations] = useState<CoachCancellationReviewQueueItem[]>([]);
  const [message, setMessage] = useState("");
  const [review, setReview] = useState<ReviewDraft | null>(null);
  const refresh = async () => { const [changeQueue, cancellationQueue] = await Promise.all([api.sessionChangeRequestsForReview(token, organizationId), api.coachCancellationRequestsForReview(token, organizationId)]); setChanges(changeQueue); setCancellations(cancellationQueue); };
  useEffect(() => { void refresh().catch(() => setMessage("無法載入課程異動待辦。")); }, [token, organizationId]);
  async function submitReview(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!review) return; const reason = String(new FormData(event.currentTarget).get("reason"));
    try { if (review.kind === "change") await api.reviewSessionChangeRequest(token, review.requestId, `liff-change-review-${review.requestId}-${review.decision}`, { decision: review.decision, reason }); else await api.reviewCoachSessionCancellation(token, review.requestId, { decision: review.decision, reason }); setMessage("審核完成，待辦已重新整理。"); setReview(null); await refresh(); }
    catch (caught) { setMessage(operationError(caught, "無法完成審核。")); }
  }
  return <section aria-label="Committee course operations"><h3>正式課程異動待辦</h3>{message && <p role="status">{message}</p>}<h4>改期申請</h4>{changes.length === 0 ? <p>沒有待審改期。</p> : <ul>{changes.map((item) => <li key={item.requestId}><strong>{item.courseNo} · 第 {item.sequenceNo} 堂</strong> · {item.requesterDisplayName}<br />原：{formatDate(item.scheduledStartAt)}<br />新：{formatDate(item.proposedStartAt)}<br />原因：{item.reason}<ReviewButtons onChoose={(decision) => setReview({ kind: "change", requestId: item.requestId, decision, label: `${item.courseNo} 第 ${item.sequenceNo} 堂改期` })} /></li>)}</ul>}<h4>教練取消授課</h4>{cancellations.length === 0 ? <p>沒有待審取消授課。</p> : <ul>{cancellations.map((item) => <li key={item.requestId}><strong>{item.courseNo} · 第 {item.sequenceNo} 堂</strong> · {item.requesterDisplayName} · {formatDate(item.scheduledStartAt)}<br />原因：{item.reason}<ReviewButtons onChoose={(decision) => setReview({ kind: "cancellation", requestId: item.requestId, decision, label: `${item.courseNo} 第 ${item.sequenceNo} 堂取消授課` })} /></li>)}</ul>}{review && <section role="dialog" aria-label="確認課程異動審核"><h4>{review.decision === "APPROVE" ? "確認核准" : "確認駁回"}</h4><p>{review.label}</p><form onSubmit={submitReview}><label>審核原因<textarea name="reason" required maxLength={5000} /></label><button>確認送出</button><button type="button" onClick={() => setReview(null)}>返回</button></form></section>}</section>;
}

function RescheduleForm({ session, onPrepare }: { session: SessionRow; onPrepare: (draft: RescheduleDraft) => void }) {
  return <form onSubmit={(event) => { event.preventDefault(); const data = new FormData(event.currentTarget); const startAt = new Date(String(data.get("startAt"))); const endAt = new Date(String(data.get("endAt"))); const reason = String(data.get("reason")); if (!Number.isFinite(startAt.getTime()) || !Number.isFinite(endAt.getTime()) || startAt >= endAt) return; onPrepare({ sessionId: session.id, label: `${session.courseNo} 第 ${session.sequenceNo} 堂`, startAt, endAt, reason }); }}><label>新開始時間<input name="startAt" type="datetime-local" required /></label><label>新結束時間<input name="endAt" type="datetime-local" required /></label><label>改期原因<input name="reason" required maxLength={5000} /></label><button>申請改期</button></form>;
}
function CancellationForm({ session, onPrepare }: { session: SessionRow; onPrepare: (reason: string) => void }) { return <form onSubmit={(event) => { event.preventDefault(); onPrepare(String(new FormData(event.currentTarget).get("reason"))); }}><label>取消授課原因<input name="reason" required maxLength={5000} /></label><button disabled={session.status === "CANCEL_PENDING" || session.status === "CANCELLED"}>申請取消授課</button></form>; }
function ConfirmReschedule({ draft, onConfirm, onCancel }: { draft: RescheduleDraft; onConfirm: () => void; onCancel: () => void }) { return <section role="dialog" aria-label="確認改期申請"><h4>確認改期申請</h4><p>{draft.label}</p><p>{formatDate(draft.startAt)} ～ {formatDate(draft.endAt)}</p><p>原因：{draft.reason}</p><button onClick={onConfirm}>確認送出</button><button onClick={onCancel}>返回</button></section>; }
function ReviewButtons({ onChoose }: { onChoose: (decision: "APPROVE" | "REJECT") => void }) { return <><button onClick={() => onChoose("APPROVE")}>核准</button><button onClick={() => onChoose("REJECT")}>駁回</button></>; }
function formatDate(value: Date) { return value.toLocaleString("zh-TW", { timeZone: "Asia/Taipei" }); }
function operationError(caught: unknown, fallback: string) { return caught instanceof ApiClientError ? presentApiError(caught.code) : fallback; }
