import { ApiClientError, createApiClient, type AvailabilityProposal, type LessonRequest, type Me, type RoleContext } from "@pickleball/api-client";
import liff from "@line/liff";
import { PageShell } from "@pickleball/ui";
import { platformName } from "@pickleball/shared";
import { useEffect, useState } from "react";

const api = createApiClient({ baseUrl: import.meta.env.VITE_API_BASE_URL ?? "/api/v1" });
const TOKEN_KEY = "platform.access-token";
const liffClient = import.meta.env.VITE_E2E_LIFF === "true"
  ? { init: async () => undefined, isLoggedIn: () => true, login: () => undefined, getIDToken: () => "e2e-line-id-token" }
  : liff;

type State = "loading" | "profile" | "roles" | "home" | "no-roles" | "error";

export function App() {
  const [state, setState] = useState<State>("loading");
  const [me, setMe] = useState<Me | null>(null);
  const [selectedRole, setSelectedRole] = useState<RoleContext | null>(null);
  const [error, setError] = useState("");
  const loadMe = async (token: string) => {
    const current = await api.me(token); setMe(current);
    if (!current.profileComplete) setState("profile");
    else if (current.roles.length === 0) setState("no-roles");
    else if (current.roles.length === 1) { setSelectedRole(current.roles[0]); setState("home"); }
    else setState("roles");
  };
  useEffect(() => { void bootstrap(); }, []);
  async function bootstrap() {
    try {
      const savedToken = sessionStorage.getItem(TOKEN_KEY);
      if (savedToken) { await loadMe(savedToken); return; }
      const liffId = import.meta.env.VITE_LIFF_ID;
      if (!liffId) throw new Error("LIFF is not configured");
      await liffClient.init({ liffId });
      if (!liffClient.isLoggedIn()) { liffClient.login(); return; }
      const idToken = liffClient.getIDToken(); if (!idToken) throw new Error("LINE did not provide an ID token");
      const login = await api.loginWithLine(idToken); sessionStorage.setItem(TOKEN_KEY, login.accessToken); await loadMe(login.accessToken);
    } catch (caught) { sessionStorage.removeItem(TOKEN_KEY); setError(caught instanceof Error ? caught.message : "Login failed"); setState("error"); }
  }
  async function submitProfile(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); const token = sessionStorage.getItem(TOKEN_KEY); if (!token) return;
    const form = new FormData(event.currentTarget);
    try { await api.updateProfile(token, { displayName: String(form.get("displayName")), phone: String(form.get("phone")) || null, email: String(form.get("email")) || null, locale: String(form.get("locale")) }); await loadMe(token); }
    catch { setError("Unable to update profile"); setState("error"); }
  }
  return <PageShell><h1>{platformName}</h1>
    {state === "loading" && <p>Signing in with LINE…</p>}
    {state === "error" && <><p role="alert">{error}</p><button onClick={() => { setState("loading"); void bootstrap(); }}>Retry</button></>}
    {state === "profile" && <form onSubmit={submitProfile}><h2>Complete your profile</h2><label>Name <input name="displayName" defaultValue={me?.displayName} required /></label><label>Phone <input name="phone" defaultValue={me?.phone ?? ""} /></label><label>Email <input name="email" type="email" defaultValue={me?.email ?? ""} /></label><input name="locale" defaultValue={me?.locale ?? "zh-TW"} hidden /><button>Save profile</button></form>}
    {state === "roles" && <><h2>Select your role</h2>{me?.roles.map((role) => <button key={`${role.roleCode}-${role.organizationId ?? "global"}`} onClick={() => { setSelectedRole(role); setState("home"); }}>{role.roleCode}</button>)}</>}
    {state === "no-roles" && <p>No active role is available. Please contact an administrator.</p>}
    {state === "home" && <><h2>{selectedRole?.roleCode} entry</h2><p>{selectedRole?.organizationName ?? "Platform-wide access"}</p>{selectedRole?.roleCode === "STUDENT" && <StudentLessonDemand token={sessionStorage.getItem(TOKEN_KEY) ?? ""} />}{selectedRole?.roleCode === "COACH" && <CoachSupply token={sessionStorage.getItem(TOKEN_KEY) ?? ""} />}</>}
  </PageShell>;
}

function StudentLessonDemand({ token }: { token: string }) {
  const [availability, setAvailability] = useState<AvailabilityProposal[]>([]);
  const [drafts, setDrafts] = useState<LessonRequest[]>([]);
  const [message, setMessage] = useState("");
  const refresh = async () => { const [slots, mine] = await Promise.all([api.approvedAvailability(token), api.myLessonRequests(token)]); setAvailability(slots); setDrafts(mine); };
  useEffect(() => { void refresh().catch(() => setMessage("Unable to load lesson availability.")); }, [token]);
  async function createDraft(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget); const slot = availability.find((item) => item.id === String(form.get("availabilityId")));
    if (!slot) { setMessage("Please select an available time."); return; }
    try { await api.createLessonRequest(token, { lessonType: "PRIVATE", scheduleType: "SINGLE", billingMode: "PER_SESSION", participantCount: 1, guestParticipantCount: 0, requestedSessionCount: 1, preferredCoachProfileId: slot.coachProfileId, selectedAvailabilityProposalId: slot.id, sessionPreferences: [{ sequenceNo: 1, startAt: slot.startAt, endAt: slot.endAt, preferredVenueId: slot.preferredVenueId ?? null, note: null }], notes: null }); setMessage("Draft saved with your selected availability."); await refresh(); }
    catch { setMessage("Unable to save the lesson request draft."); }
  }
  async function submitDraft(id: string) {
    try { await api.submitLessonRequest(token, id, `lesson-submit-${id}`); setMessage("Lesson request submitted."); await refresh(); }
    catch (error) { if (error instanceof ApiClientError && error.code === "AVAILABILITY_ALREADY_CLAIMED") { setMessage("該時段已被其他需求取得，請重新整理並選擇其他時段。"); await refresh(); return; } setMessage("Unable to submit the lesson request."); }
  }
  async function applyAsCoach() { try { await api.applyForCoach(token, { applicationNote: "Coach application from LIFF", skillLevel: null, bio: null }); setMessage("Coach application submitted for committee review."); } catch { setMessage("Unable to submit coach application."); } }
  return <section><h3>Find a coach time</h3>{message && <p role="status">{message}</p>}<button onClick={() => void applyAsCoach()}>Apply to become a coach</button><form onSubmit={createDraft}><label>Approved availability <select name="availabilityId" required defaultValue=""><option value="" disabled>Select a time</option>{availability.map((slot) => <option key={slot.id} value={slot.id}>{new Date(slot.startAt).toLocaleString()}</option>)}</select></label><button>Create lesson draft</button></form><h3>My lesson requests</h3>{drafts.length === 0 ? <p>No lesson drafts yet.</p> : <ul>{drafts.map((draft) => <li key={draft.id}>{draft.status} — {draft.selectedAvailabilityProposalId ?? "No selected time"}{draft.status === "DRAFT" && <button onClick={() => void submitDraft(draft.id)}>Submit</button>}</li>)}</ul>}</section>;
}

function CoachSupply({ token }: { token: string }) {
  const [proposals, setProposals] = useState<AvailabilityProposal[]>([]); const [message, setMessage] = useState("");
  const refresh = async () => setProposals(await api.myAvailability(token));
  useEffect(() => { void refresh().catch(() => setMessage("Unable to load your availability.")); }, [token]);
  async function create(event: React.FormEvent<HTMLFormElement>) { event.preventDefault(); const form = new FormData(event.currentTarget); try { await api.createAvailability(token, { startAt: new Date(String(form.get("startAt"))), endAt: new Date(String(form.get("endAt"))), preferredVenueId: null }); setMessage("Availability draft created."); await refresh(); } catch { setMessage("Unable to create availability."); } }
  async function submit(id: string) { try { await api.submitAvailability(token, id); setMessage("Availability submitted for review."); await refresh(); } catch { setMessage("Unable to submit availability."); } }
  return <section><h3>My availability</h3>{message && <p role="status">{message}</p>}<form onSubmit={create}><label>Start <input name="startAt" type="datetime-local" required /></label><label>End <input name="endAt" type="datetime-local" required /></label><button>Create availability draft</button></form><ul>{proposals.map((proposal) => <li key={proposal.id}>{new Date(proposal.startAt).toLocaleString()} — {proposal.status}{proposal.status === "DRAFT" && <button onClick={() => void submit(proposal.id)}>Submit for review</button>}</li>)}</ul></section>;
}
