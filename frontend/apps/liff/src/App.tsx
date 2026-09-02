import {
  ApiClientError,
  createApiClient,
  type AvailabilityProposal,
  type CourseMatchInvitationSummary,
  type CourseOfferingDetail,
  type CourseOfferingSummary,
  type LessonRequest,
  type Me,
  type MyCourseOfferingRegistration,
  type RoleContext,
} from "@pickleball/api-client";
import liff from "@line/liff";
import { PageShell } from "@pickleball/ui";
import { formatTaipeiDateTime, formatTwd, platformName, presentApiError, roleLabel, statusLabel } from "@pickleball/shared";
import { useEffect, useState, type ReactNode } from "react";
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from "react-router";
import { CoachCourseOperations, CommitteeCourseOperations, StudentCourseOperations } from "./CourseOperations";

const api = createApiClient({ baseUrl: import.meta.env.VITE_API_BASE_URL ?? "/api/v1" });
const TOKEN_KEY = "platform.access-token";
export const BOOTSTRAP_TIMEOUT_MS = 15_000;
export const BACKEND_AUTHENTICATION_TIMEOUT_MS = 60_000;
const liffClient = import.meta.env.VITE_E2E_LIFF === "true"
  ? { init: async () => undefined, isLoggedIn: () => true, login: () => undefined, getIDToken: () => "e2e-line-id-token" }
  : liff;

type State = "loading" | "redirecting" | "roles" | "home" | "no-roles" | "error";
type BootstrapStage = "platform session" | "LIFF SDK initialization" | "LINE login state" | "LINE ID token retrieval" | "backend authentication" | "platform token storage" | "current-user retrieval" | "role/home rendering";

async function withinBootstrapTimeout<T>(stage: BootstrapStage, operation: Promise<T>): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timeoutMs = stage === "backend authentication" ? BACKEND_AUTHENTICATION_TIMEOUT_MS : BOOTSTRAP_TIMEOUT_MS;
    const timeout = globalThis.setTimeout(() => reject(new Error(`LINE 登入未能完成：${stage}`)), timeoutMs);
    operation.then((value) => { globalThis.clearTimeout(timeout); resolve(value); }, (error: unknown) => { globalThis.clearTimeout(timeout); reject(error); });
  });
}

function bootstrapError(stage: BootstrapStage) {
  return stage === "backend authentication" ? "無法完成平台登入，請稍後重試。" : "無法完成 LINE 登入，請稍後重試。";
}

export function App() {
  const [state, setState] = useState<State>("loading");
  const [me, setMe] = useState<Me | null>(null);
  const [selectedRole, setSelectedRole] = useState<RoleContext | null>(null);
  const [error, setError] = useState("");
  const loadMe = async (token: string, setStage: (stage: BootstrapStage) => void) => {
    setStage("current-user retrieval");
    const current = await withinBootstrapTimeout("current-user retrieval", api.me(token)); setMe(current);
    setStage("role/home rendering");
    if (current.roles.length === 0) setState("no-roles");
    else if (current.roles.length === 1) { setSelectedRole(current.roles[0]); setState("home"); }
    else setState("roles");
  };
  useEffect(() => { void bootstrap(); }, []);
  async function bootstrap() {
    let currentStage: BootstrapStage = "platform session";
    const setStage = (stage: BootstrapStage) => { currentStage = stage; };
    try {
      setError("");
      const savedToken = sessionStorage.getItem(TOKEN_KEY);
      if (savedToken) { await loadMe(savedToken, setStage); return; }
      const liffId = import.meta.env.VITE_LIFF_ID;
      if (!liffId) throw new Error("LIFF is not configured");
      setStage("LIFF SDK initialization");
      await withinBootstrapTimeout("LIFF SDK initialization", liffClient.init({ liffId }));
      setStage("LINE login state");
      if (!liffClient.isLoggedIn()) { liffClient.login(); setState("redirecting"); return; }
      setStage("LINE ID token retrieval");
      const idToken = liffClient.getIDToken(); if (!idToken) throw new Error("LINE did not provide an ID token");
      setStage("backend authentication");
      const login = await withinBootstrapTimeout("backend authentication", api.loginWithLine(idToken));
      setStage("platform token storage");
      sessionStorage.setItem(TOKEN_KEY, login.accessToken);
      await loadMe(login.accessToken, setStage);
  } catch { sessionStorage.removeItem(TOKEN_KEY); setError(bootstrapError(currentStage)); setState("error"); }
  }
  const token = sessionStorage.getItem(TOKEN_KEY) ?? "";
  return <PageShell><p className="liff-brand">{platformName}</p>
    {state === "loading" && <p aria-live="polite">正在連線至 LINE…</p>}
    {state === "redirecting" && <p aria-live="polite">正在前往 LINE 登入…</p>}
    {state === "error" && <><p role="alert">{error}</p><button onClick={() => { setState("loading"); void bootstrap(); }}>重試</button></>}
    {state === "roles" && <RoleSelector roles={me?.roles ?? []} onSelect={(role) => { setSelectedRole(role); setState("home"); }} />}
    {state === "no-roles" && <p role="alert">目前沒有可使用的角色，請聯絡管理員。</p>}
    {state === "home" && selectedRole && <BrowserRouter><LiffShell token={token} me={me} role={selectedRole} onRoleSwitch={(role) => setSelectedRole(role)} /></BrowserRouter>}
  </PageShell>;
}

function RoleSelector({ roles, onSelect }: { roles: RoleContext[]; onSelect: (role: RoleContext) => void }) {
  return <section aria-label="選擇角色"><h2>選擇使用身分</h2><p>請選擇這次要使用的平台身分。</p>{roles.filter((role) => ["STUDENT", "COACH", "COMMITTEE"].includes(role.roleCode)).map((role) => <button key={`${role.roleCode}-${role.organizationId ?? "global"}`} onClick={() => onSelect(role)}>{roleLabel(role.roleCode)}</button>)}</section>;
}

type NavigationItem = { path: string; label: string };
function navigationFor(role: RoleContext): NavigationItem[] {
  if (role.roleCode === "STUDENT") return [{ path: "/", label: "首頁" }, { path: "/courses", label: "我的課程" }, { path: "/demand", label: "找課與需求" }, { path: "/profile", label: "我的" }];
  if (role.roleCode === "COACH") return [{ path: "/", label: "首頁" }, { path: "/courses", label: "我的授課" }, { path: "/availability", label: "可授課時段" }, { path: "/profile", label: "我的" }];
  return [{ path: "/", label: "首頁" }, { path: "/courses", label: "課程" }, { path: "/operations", label: "營運" }, { path: "/profile", label: "我的" }];
}

function LiffShell({ token, me, role, onRoleSwitch }: { token: string; me: Me | null; role: RoleContext; onRoleSwitch: (role: RoleContext) => void }) {
  const navigate = useNavigate(); const location = useLocation(); const navigation = navigationFor(role); const title = navigation.find((item) => item.path === location.pathname)?.label ?? "首頁";
  const switchableRoles = (me?.roles ?? []).filter((item) => ["STUDENT", "COACH", "COMMITTEE"].includes(item.roleCode));
  const home = <RoleHome role={role} navigate={navigate} />;
  return <div className="liff-shell"><header className="liff-shell__header"><div><p className="liff-shell__role">{roleLabel(role.roleCode)}{role.organizationName ? ` · ${role.organizationName}` : ""}</p><p className="liff-shell__title">{title}</p></div>{switchableRoles.length > 1 && <button className="liff-shell__switch" onClick={() => navigate("/profile")}>切換角色</button>}</header><div id="app-content" tabIndex={-1} className="liff-shell__content"><Routes>
    <Route path="/" element={home} />
    <Route path="/courses" element={role.roleCode === "STUDENT" ? <StudentCoursePage token={token} /> : role.roleCode === "COACH" ? <CoachCoursePage token={token} /> : role.organizationId ? <CommitteeCoursePage token={token} organizationId={role.organizationId} /> : home} />
    <Route path="/demand" element={role.roleCode === "STUDENT" ? <TaskPage title="找課與需求"><StudentOpenEnrollment token={token} /><StudentLessonDemand token={token} /></TaskPage> : <Navigate to="/" replace />} />
    <Route path="/availability" element={role.roleCode === "COACH" ? <TaskPage title="可授課時段"><CoachSupply token={token} /></TaskPage> : <Navigate to="/" replace />} />
    <Route path="/operations" element={role.roleCode === "COMMITTEE" && role.organizationId ? <TaskPage title="營運"><CommitteeOpenEnrollment token={token} organizationId={role.organizationId} /></TaskPage> : <Navigate to="/" replace />} />
    <Route path="/profile" element={<ProfilePage me={me} role={role} roles={switchableRoles} onRoleSwitch={(next) => { onRoleSwitch(next); navigate("/"); }} />} />
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes></div><nav className="liff-shell__nav" aria-label="主要導覽">{navigation.map((item) => <button key={item.path} className={location.pathname === item.path ? "is-active" : ""} aria-current={location.pathname === item.path ? "page" : undefined} onClick={() => navigate(item.path)}>{item.label}</button>)}</nav></div>;
}

function TaskPage({ title, children }: { title: string; children: ReactNode }) { return <section><h1>{title}</h1>{children}</section>; }
function StudentCoursePage({ token }: { token: string }) { return <TaskPage title="我的課程"><StudentCourseOperations token={token} /><StudentOpenEnrollment token={token} /></TaskPage>; }
function CoachCoursePage({ token }: { token: string }) { return <TaskPage title="我的授課"><CoachCourseOperations token={token} /></TaskPage>; }
function CommitteeCoursePage({ token, organizationId }: { token: string; organizationId: string }) { return <TaskPage title="課程"><CommitteeCourseOperations token={token} organizationId={organizationId} /></TaskPage>; }
function RoleHome({ role, navigate }: { role: RoleContext; navigate: ReturnType<typeof useNavigate> }) { const primary = role.roleCode === "STUDENT" ? { label: "找課與需求", path: "/demand", message: "查看公開課程，或提出你的找教練需求。" } : role.roleCode === "COACH" ? { label: "管理可授課時段", path: "/availability", message: "管理可授課時段與媒合邀請。" } : { label: "查看營運待辦", path: "/operations", message: "查看公開招生與必要的營運操作。" }; return <section><h1>{roleLabel(role.roleCode)}首頁</h1><p>{primary.message}</p><button onClick={() => navigate(primary.path)}>{primary.label}</button><button onClick={() => navigate("/courses")}>{role.roleCode === "COACH" ? "查看我的授課" : "查看課程"}</button></section>; }
function ProfilePage({ me, role, roles, onRoleSwitch }: { me: Me | null; role: RoleContext; roles: RoleContext[]; onRoleSwitch: (role: RoleContext) => void }) { return <section><h1>我的</h1><p>目前身分：{roleLabel(role.roleCode)}</p><p>顯示名稱：{me?.displayName ?? "尚未提供"}</p><p>電子郵件：{me?.email ?? "未設定"}</p>{roles.length > 1 && <section aria-label="切換角色"><h2>切換角色</h2>{roles.filter((item) => item.roleCode !== role.roleCode || item.organizationId !== role.organizationId).map((item) => <button key={`${item.roleCode}-${item.organizationId ?? "global"}`} onClick={() => onRoleSwitch(item)}>{roleLabel(item.roleCode)}</button>)}</section>}</section>; }

function StudentOpenEnrollment({ token }: { token: string }) {
  const [offerings, setOfferings] = useState<CourseOfferingSummary[]>([]);
  const [registrations, setRegistrations] = useState<MyCourseOfferingRegistration[]>([]);
  const [selected, setSelected] = useState<CourseOfferingDetail | null>(null);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const refresh = async () => {
    const [openOfferings, mine] = await Promise.all([
      api.listCourseOfferings(token, { status: "OPEN", size: 100, sort: "firstSessionAt,asc" }),
      api.myCourseOfferingRegistrations(token),
    ]);
    setOfferings(openOfferings); setRegistrations(mine);
  };
  useEffect(() => { void refresh().catch(() => setMessage("無法載入公開課程，請稍後再試。")); }, [token]);
  async function openDetail(id: string) { try { setSelected(await api.courseOfferingDetail(token, id)); } catch { setMessage("無法載入課程詳情。"); } }
  async function register(offering: CourseOfferingSummary) {
    setBusy(offering.id);
    try {
      await api.registerCourseOffering(token, offering.id, `offering-register-${offering.id}-${offering.version}`);
      setMessage("報名成功，系統已保留你的課程時段。"); await refresh(); setSelected(await api.courseOfferingDetail(token, offering.id));
    } catch (caught) { setMessage(studentOfferingError(caught)); await refresh().catch(() => undefined); }
    finally { setBusy(null); }
  }
  async function cancel(registration: MyCourseOfferingRegistration) {
    setBusy(registration.id);
    try {
      await api.cancelCourseOfferingRegistration(token, registration.id, `offering-registration-cancel-${registration.id}`);
      setMessage("已取消報名並釋放保留時段。"); await refresh(); if (selected?.summary.id === registration.offeringId) setSelected(await api.courseOfferingDetail(token, registration.offeringId));
    } catch (caught) { setMessage(safeApiError(caught, "無法取消報名。")); }
    finally { setBusy(null); }
  }
  return <section aria-label="公開課程">
    <h3>公開課程 / 開放報名</h3>{message && <p role="status">{message}</p>}
    {offerings.length === 0 ? <p>目前沒有開放報名的公開課程。</p> : <ul>{offerings.map((offering) => <li key={offering.id}><strong>{offering.title}</strong> · {offering.coach.displayName} · {formatDate(offering.firstSessionAt)} · {formatPrice(offering)} · 剩餘 {offering.remainingCapacity} 名 · {registrationLabel(offering.registrationState)} <button onClick={() => void openDetail(offering.id)}>查看課程</button>{offering.registrationState === "OPEN" && <button disabled={busy === offering.id} onClick={() => void register(offering)}>立即報名</button>}</li>)}</ul>}
    {selected && <section aria-label="Open enrollment detail"><h4>{selected.summary.title}</h4><p>{selected.description || "無課程說明"}</p><p>教練：{selected.summary.coach.displayName}</p><p>招生：{formatDate(selected.summary.registrationOpenAt)} ～ {formatDate(selected.summary.registrationCloseAt)}</p><p>人數：{selected.summary.registeredCount}/{selected.summary.maximumParticipants}（最低 {selected.summary.minimumParticipants}）</p><p>費用：{formatPrice(selected.summary)}</p><h5>課程堂次</h5><ul>{selected.sessionPlans.map((session) => <li key={session.id}>第 {session.sequenceNo} 堂：{formatDate(session.startAt)} ～ {formatDate(session.endAt)} · {session.venueName}</li>)}</ul><button onClick={() => setSelected(null)}>關閉詳情</button></section>}
    <h3>我的公開課程報名</h3>{registrations.length === 0 ? <p>目前沒有公開課程報名紀錄。</p> : <ul>{registrations.map((registration) => <li key={registration.id}><strong>{registration.offeringTitle}</strong> · {statusLabel(registration.status)} · {formatDate(registration.registeredAt)}{registration.courseId && <> · 已成班</>}{registration.status === "ACTIVE" && <button disabled={busy === registration.id} aria-busy={busy === registration.id || undefined} onClick={() => void cancel(registration)}>{busy === registration.id ? "取消中…" : "取消報名"}</button>}</li>)}</ul>}
  </section>;
}

function CommitteeOpenEnrollment({ token, organizationId }: { token: string; organizationId: string }) {
  const [offerings, setOfferings] = useState<CourseOfferingSummary[]>([]);
  const [selected, setSelected] = useState<CourseOfferingDetail | null>(null);
  const [registrationCount, setRegistrationCount] = useState(0);
  const [pending, setPending] = useState<{ action: "confirm" | "cancel"; offeringId: string } | null>(null);
  const [message, setMessage] = useState("");
  const refresh = async () => setOfferings(await api.listCourseOfferings(token, { organizationId, size: 100, sort: "firstSessionAt,asc" }));
  useEffect(() => { void refresh().catch(() => setMessage("無法載入公開招生管理。")); }, [token, organizationId]);
  async function open(id: string) {
    try { const [detail, registrations] = await Promise.all([api.courseOfferingDetail(token, id), api.listCourseOfferingRegistrations(token, id)]); setSelected(detail); setRegistrationCount(registrations.length); }
    catch { setMessage("無法載入招生詳情。"); }
  }
  async function command(action: "publish" | "close" | "confirm" | "cancel", offering: CourseOfferingSummary) {
    try {
      if (action === "publish") await api.publishCourseOffering(token, offering.id, `liff-offering-publish-${offering.id}-${offering.version}`);
      if (action === "close") await api.closeCourseOffering(token, offering.id, `liff-offering-close-${offering.id}-${offering.version}`);
      if (action === "confirm") { const result = await api.confirmCourseOffering(token, offering.id, `liff-offering-confirm-${offering.id}-${offering.version}`); setMessage(`成班完成：${result.courseId}`); }
      if (action === "cancel") await api.cancelCourseOffering(token, offering.id, `liff-offering-cancel-${offering.id}-${offering.version}`, { reason: "Cancelled via Committee LIFF" });
      if (action !== "confirm") setMessage(action === "publish" ? "招生已發布。" : action === "close" ? "招生已關閉。" : "課程已取消。");
      setPending(null); await refresh(); if (selected?.summary.id === offering.id) await open(offering.id);
    } catch (caught) { setMessage(safeApiError(caught, "操作失敗，請稍後再試。")); setPending(null); }
  }
  return <section aria-label="Committee open enrollment"><h3>公開招生快速管理</h3>{message && <p role="status">{message}</p>}<p>建立、編輯 Session 與價格確認請使用 Web Admin；LIFF 提供招生監看與必要快速操作。</p>{offerings.length === 0 ? <p>目前沒有公開課程。</p> : <ul>{offerings.map((offering) => <li key={offering.id}><strong>{offering.title}</strong> · {offering.status} · {offering.registeredCount}/{offering.maximumParticipants} <button onClick={() => void open(offering.id)}>查看招生</button></li>)}</ul>}{selected && <section aria-label="Committee offering detail"><h4>{selected.summary.title}</h4><p>狀態：{selected.summary.status}</p><p>報名人數：{registrationCount}；最低 {selected.summary.minimumParticipants}；最高 {selected.summary.maximumParticipants}</p><p>價格：{formatPrice(selected.summary)}</p>{selected.summary.status === "DRAFT" && selected.summary.priceSnapshotId && <button onClick={() => void command("publish", selected.summary)}>發布招生</button>}{selected.summary.status === "OPEN" && <button onClick={() => void command("close", selected.summary)}>關閉招生</button>}{selected.summary.status === "CLOSED" && <button onClick={() => setPending({ action: "confirm", offeringId: selected.summary.id })}>成班</button>}{["DRAFT", "OPEN", "CLOSED"].includes(selected.summary.status) && <button onClick={() => setPending({ action: "cancel", offeringId: selected.summary.id })}>取消課程</button>}<button onClick={() => setSelected(null)}>關閉詳情</button></section>}{pending && selected?.summary.id === pending.offeringId && <section role="dialog" aria-label={pending.action === "confirm" ? "Confirm offering formation" : "Confirm offering cancellation"}><h4>{pending.action === "confirm" ? "確認成班" : "確認取消課程"}</h4><p>{pending.action === "confirm" ? "系統會再次驗證最低/最高人數，並原子建立正式課程與應收。" : "系統會取消 ACTIVE 報名並釋放保留時段。"}</p><button onClick={() => void command(pending.action, selected.summary)}>確認</button><button onClick={() => setPending(null)}>返回</button></section>}</section>;
}

function studentOfferingError(caught: unknown) {
  if (!(caught instanceof ApiClientError)) return "報名失敗，請稍後再試。";
  if (caught.code === "OFFERING_CAPACITY_FULL") return "課程名額已滿，已重新整理最新名額。";
  if (caught.code === "OFFERING_ALREADY_REGISTERED") return "你已經報名這門課程。";
  if (caught.code === "OFFERING_NOT_OPEN" || caught.code === "OFFERING_REGISTRATION_CLOSED") return "招生狀態已變更，請重新選擇課程。";
  if (caught.code.includes("SCHEDULE") || caught.code.includes("CONFLICT")) return "你的時段與既有行程衝突，請選擇其他課程。";
  return presentApiError(caught.code);
}
function safeApiError(caught: unknown, fallback: string) { return caught instanceof ApiClientError ? presentApiError(caught.code) : fallback; }
function formatDate(value?: Date | null) { return formatTaipeiDateTime(value); }
function formatPrice(offering: CourseOfferingSummary) { return offering.pricePerParticipant == null ? "價格待確認" : formatTwd(offering.pricePerParticipant); }
function registrationLabel(state: CourseOfferingSummary["registrationState"]) { return state === "OPEN" ? "可報名" : state === "REGISTERED" ? "已報名" : state === "FULL" ? "已額滿" : state === "NOT_OPEN" ? "尚未開放" : "已截止"; }

function StudentLessonDemand({ token }: { token: string }) {
  const [availability, setAvailability] = useState<AvailabilityProposal[]>([]);
  const [drafts, setDrafts] = useState<LessonRequest[]>([]);
  const [message, setMessage] = useState("");
  const refresh = async () => { const [slots, mine] = await Promise.all([api.approvedAvailability(token), api.myLessonRequests(token)]); setAvailability(slots); setDrafts(mine); };
  useEffect(() => { void refresh().catch(() => setMessage("無法載入可選時段，請稍後再試。")); }, [token]);
  async function createDraft(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget); const slot = availability.find((item) => item.id === String(form.get("availabilityId")));
    if (!slot) { setMessage("請選擇可授課時段。"); return; }
    try { await api.createLessonRequest(token, { lessonType: "PRIVATE", scheduleType: "SINGLE", billingMode: "PER_SESSION", participantCount: 1, guestParticipantCount: 0, requestedSessionCount: 1, preferredCoachProfileId: slot.coachProfileId, selectedAvailabilityProposalId: slot.id, sessionPreferences: [{ sequenceNo: 1, startAt: slot.startAt, endAt: slot.endAt, preferredVenueId: slot.preferredVenueId ?? null, note: null }], notes: null }); setMessage("需求草稿已儲存。送出後將交由委員會處理。"); await refresh(); }
    catch { setMessage("無法儲存需求草稿，請稍後再試。"); }
  }
  async function submitDraft(id: string) {
    try { await api.submitLessonRequest(token, id, `lesson-submit-${id}`); setMessage("Lesson request submitted."); await refresh(); }
    catch (error) { if (error instanceof ApiClientError && error.code === "AVAILABILITY_ALREADY_CLAIMED") { setMessage("該時段已被其他需求取得，請重新整理並選擇其他時段。"); await refresh(); return; } setMessage(safeApiError(error, "無法送出找教練需求。")); }
  }
  async function applyAsCoach() { try { await api.applyForCoach(token, { applicationNote: "Coach application from LIFF", skillLevel: null, bio: null }); setMessage("教練申請已送出，等待委員會審核。"); } catch { setMessage("無法送出教練申請。"); } }
  return <section><h3>找教練時段</h3>{message && <p role="status">{message}</p>}<button onClick={() => void applyAsCoach()}>申請成為教練</button><form onSubmit={createDraft}><label>可選時段 <select name="availabilityId" required defaultValue=""><option value="" disabled>請選擇時段</option>{availability.map((slot) => <option key={slot.id} value={slot.id}>{formatDate(slot.startAt)}</option>)}</select></label><button>建立需求草稿</button></form><h3>我的找教練需求</h3>{drafts.length === 0 ? <p>目前沒有需求草稿。</p> : <ul>{drafts.map((draft) => <li key={draft.id}>{statusLabel(draft.status)} — {draft.selectedAvailabilityProposalId ? "已選擇時段" : "尚未選擇時段"}{draft.status === "DRAFT" && <button onClick={() => void submitDraft(draft.id)}>送出需求</button>}</li>)}</ul>}</section>;
}

function CoachSupply({ token }: { token: string }) {
  const [proposals, setProposals] = useState<AvailabilityProposal[]>([]);
  const [invitations, setInvitations] = useState<CourseMatchInvitationSummary[]>([]);
  const [message, setMessage] = useState("");
  const refresh = async () => { const [availability, matchInvitations] = await Promise.all([api.myAvailability(token), api.myCourseMatchInvitations(token)]); setProposals(availability); setInvitations(matchInvitations); };
  useEffect(() => { void refresh().catch(() => setMessage("無法載入教練工作內容，請稍後再試。")); }, [token]);
  async function create(event: React.FormEvent<HTMLFormElement>) { event.preventDefault(); const form = new FormData(event.currentTarget); try { await api.createAvailability(token, { startAt: new Date(String(form.get("startAt"))), endAt: new Date(String(form.get("endAt"))), preferredVenueId: null }); setMessage("可授課時段草稿已建立。"); await refresh(); } catch { setMessage("無法建立可授課時段。"); } }
  async function submit(id: string) { try { await api.submitAvailability(token, id); setMessage("可授課時段已送審。"); await refresh(); } catch { setMessage("無法送出可授課時段。"); } }
  async function respond(invitationId: string, status: "ACCEPTED" | "REJECTED") { try { await api.respondCourseMatchInvitation(token, invitationId, { status, responseNote: status === "ACCEPTED" ? "Accepted via Coach LIFF" : "Rejected via Coach LIFF" }); setMessage(status === "ACCEPTED" ? "已接受媒合邀請。" : "已婉拒媒合邀請。"); await refresh(); } catch (error) { setMessage(safeApiError(error, "無法回覆媒合邀請。")); } }
  return <section><h3>媒合邀請</h3>{message && <p role="status">{message}</p>}{invitations.length === 0 ? <p>目前沒有待回覆的媒合邀請。</p> : <ul>{invitations.map((invitation) => <li key={invitation.invitationId}><strong>第 {invitation.sessionIndex} 堂</strong> · {formatDate(invitation.startAt)} · {invitation.venueName || "場地待確認"} · {statusLabel(invitation.status)}{invitation.status === "INVITED" && <><button onClick={() => void respond(invitation.invitationId, "ACCEPTED")}>接受媒合</button><button onClick={() => void respond(invitation.invitationId, "REJECTED")}>婉拒媒合</button></>}{invitation.respondedAt && <span> · 已於 {formatDate(invitation.respondedAt)} 回覆</span>}</li>)}</ul>}<h3>我的可授課時段</h3><form onSubmit={create}><label>開始時間 <input name="startAt" type="datetime-local" required /></label><label>結束時間 <input name="endAt" type="datetime-local" required /></label><button>建立時段草稿</button></form>{proposals.length === 0 ? <p>尚未建立可授課時段。</p> : <ul>{proposals.map((proposal) => <li key={proposal.id}>{formatDate(proposal.startAt)} — {statusLabel(proposal.status)}{proposal.status === "DRAFT" && <button onClick={() => void submit(proposal.id)}>送審</button>}</li>)}</ul>}</section>;
}
