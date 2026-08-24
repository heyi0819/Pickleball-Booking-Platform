import {
  ApiClientError,
  createApiClient,
  type AvailabilityProposal,
  type CoachApplication,
  type CourseMatch,
  type CourseMatchPricingPreview,
  type CourseMatchSummary,
  type LessonRequest,
  type Me,
} from "@pickleball/api-client";
import { platformName } from "@pickleball/shared";
import { PageShell } from "@pickleball/ui";
import { useEffect, useState } from "react";

const api = createApiClient({ baseUrl: import.meta.env.VITE_API_BASE_URL ?? "/api/v1" });

export function App() {
  const [me, setMe] = useState<Me | null>(null);
  const [state, setState] = useState<"loading" | "allowed" | "forbidden">("loading");
  useEffect(() => { const token = sessionStorage.getItem("platform.access-token"); if (!token) { setState("forbidden"); return; } void api.me(token).then((current) => { setMe(current); setState(current.roles.some((role) => role.roleCode === "COMMITTEE" || role.roleCode === "PLATFORM_ADMIN") ? "allowed" : "forbidden"); }).catch(() => { sessionStorage.removeItem("platform.access-token"); setState("forbidden"); }); }, []);
  const committeeRole = me?.roles.find((role) => role.roleCode === "COMMITTEE" && role.organizationId);
  return <PageShell><h1>{platformName} Admin</h1>{state === "loading" && <p>Checking access…</p>}{state === "forbidden" && <p role="alert">Forbidden: administrator access is required.</p>}{state === "allowed" && <><h2>Authorized admin entry</h2><p>Signed in as {me?.displayName}</p>{committeeRole?.organizationId ? <CommitteeReview token={sessionStorage.getItem("platform.access-token") ?? ""} organizationId={committeeRole.organizationId} /> : <p>A committee organization role is required to review organization work.</p>}</>}</PageShell>;
}

function CommitteeReview({ token, organizationId }: { token: string; organizationId: string }) {
  const [applications, setApplications] = useState<CoachApplication[]>([]); const [availability, setAvailability] = useState<AvailabilityProposal[]>([]); const [requests, setRequests] = useState<LessonRequest[]>([]); const [message, setMessage] = useState(""); const [detail, setDetail] = useState<Detail | null>(null);
  const refresh = async () => { const [app, slots, lessons] = await Promise.all([api.coachApplicationsForReview(token, organizationId), api.availabilityForReview(token, organizationId), api.lessonRequestsForReview(token, organizationId)]); setApplications(app); setAvailability(slots); setRequests(lessons); };
  useEffect(() => { void refresh().catch(() => setMessage("Unable to load review queues.")); }, [token, organizationId]);
  async function review(kind: "application" | "availability" | "lesson", id: string, decision: "APPROVE" | "REJECT") { try { const request = { decision, reviewNote: `${decision} via committee review` }; if (kind === "application") await api.reviewCoachApplication(token, id, request); else if (kind === "availability") await api.reviewAvailability(token, id, request); else await api.reviewLessonRequest(token, id, request); setMessage("Review saved."); setDetail(null); await refresh(); } catch { setMessage("Unable to save review."); } }
  const actions = (kind: "application" | "availability" | "lesson", id: string, status: string) => status === "SUBMITTED" ? <><button onClick={() => void review(kind, id, "APPROVE")}>Approve</button><button onClick={() => void review(kind, id, "REJECT")}>Reject</button></> : null;
  return <><section><h2>Committee review</h2>{message && <p role="status">{message}</p>}<h3>Coach applications</h3><ul>{applications.map((item) => <li key={item.id}><button onClick={() => setDetail({ kind: "application", item })}>View application</button> {item.status} {actions("application", item.id, item.status)}</li>)}</ul><h3>Availability proposals</h3><ul>{availability.map((item) => <li key={item.id}><button onClick={() => setDetail({ kind: "availability", item })}>View availability</button> {new Date(item.startAt).toLocaleString()} — {item.status} {actions("availability", item.id, item.status)}</li>)}</ul><h3>Lesson requests</h3><ul>{requests.map((item) => <li key={item.id}><button onClick={() => setDetail({ kind: "lesson", item })}>View lesson request</button> {item.status} {actions("lesson", item.id, item.status)}</li>)}</ul>{detail && <section aria-label="Review detail"><h3>{detail.kind === "application" ? "Coach application detail" : detail.kind === "availability" ? "Availability proposal detail" : "Lesson request detail"}</h3><dl>{Object.entries(detail.item).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{String(value ?? "")}</dd></div>)}</dl>{actions(detail.kind, detail.item.id, detail.item.status)}<button onClick={() => setDetail(null)}>Close detail</button></section>}</section><MatchWorkQueue token={token} organizationId={organizationId} /></>;
}

function MatchWorkQueue({ token, organizationId }: { token: string; organizationId: string }) {
  const [matches, setMatches] = useState<CourseMatchSummary[]>([]);
  const [selected, setSelected] = useState<CourseMatch | null>(null);
  const [preview, setPreview] = useState<CourseMatchPricingPreview | null>(null);
  const [confirmingFormation, setConfirmingFormation] = useState(false);
  const [message, setMessage] = useState("");
  const refreshMatches = async () => setMatches(await api.courseMatchesForReview(token, organizationId));
  useEffect(() => { void refreshMatches().catch(() => setMessage("Unable to load course matches.")); }, [token, organizationId]);
  async function openMatch(id: string) { try { setSelected(await api.courseMatchDetail(token, id)); setPreview(null); setConfirmingFormation(false); } catch { setMessage("Unable to load match detail."); } }
  async function refreshSelected() { if (!selected) return; setSelected(await api.courseMatchDetail(token, selected.id)); await refreshMatches(); }
  async function previewPricing() { if (!selected) return; try { setPreview(await api.previewCourseMatchPricing(token, selected.id)); setMessage("Pricing preview calculated. Review the breakdown before confirming."); } catch { setMessage("Unable to preview pricing."); } }
  async function confirmPricing() { if (!selected || !preview) return; try { await api.confirmCourseMatchPricing(token, selected.id, `match-price-${selected.id}-${preview.pricingFingerprint}`, { acceptedTotalAmount: preview.totalAmount, currency: preview.currency, pricingFingerprint: preview.pricingFingerprint, confirmationNote: "Confirmed via Committee Admin" }); setMessage("Pricing confirmed."); setPreview(null); await refreshSelected(); } catch (error) { setMessage(error instanceof ApiClientError && error.code === "PRICE_CHANGED_RECALC_REQUIRED" ? "Pricing inputs changed. Recalculate the preview before confirming." : "Unable to confirm pricing."); } }
  async function confirmFormation() { if (!selected) return; try { const result = await api.confirmCourseMatch(token, selected.id, `match-formation-${selected.id}-${selected.version}`); setMessage(`Course formed: ${result.courseId}`); setConfirmingFormation(false); await refreshSelected(); } catch (error) { setMessage(error instanceof ApiClientError ? `Unable to form course: ${error.code}` : "Unable to form course."); } }
  const readiness = selected?.readiness;
  return <section aria-label="Course matching"><h2>Course matching</h2>{message && <p role="status">{message}</p>}<h3>Match queue</h3>{matches.length === 0 ? <p>No course matches yet.</p> : <ul>{matches.map((match) => <li key={match.id}><button onClick={() => void openMatch(match.id)}>Open match</button> {match.status} · pricing {match.pricing.status} · {match.readiness.readyToConfirm ? "ready" : "not ready"}</li>)}</ul>}{selected && <section aria-label="Course match detail"><h3>Match detail</h3><p>Match {selected.id}</p><p>Status: {selected.status}</p><p>Participants: {selected.participantCount}</p><h4>Readiness</h4><ul><ReadinessItem label="Lesson request approved" ready={readiness?.lessonRequestApproved ?? false} /><ReadinessItem label="Coaches accepted" ready={readiness?.coachesAccepted ?? false} /><ReadinessItem label="Sessions in future" ready={readiness?.sessionsFuture ?? false} /><ReadinessItem label="Schedule conflict free" ready={readiness?.scheduleConflictFree ?? false} /><ReadinessItem label="Venue ready" ready={readiness?.venueReady ?? false} /><ReadinessItem label="Pricing confirmed" ready={readiness?.pricingConfirmed ?? false} /><ReadinessItem label="Participant count valid" ready={readiness?.participantCountValid ?? false} /></ul><h4>Sessions</h4><ul>{selected.sessions.map((session) => <li key={session.id}>{new Date(session.startAt).toLocaleString()} — {session.venueName ?? "Venue pending"}</li>)}</ul><h4>Coach invitations</h4><ul>{selected.coachInvitations.map((invitation) => <li key={invitation.invitationId}>Session {invitation.sessionIndex}: {invitation.status}</li>)}</ul>{selected.status === "DRAFT" && <><button onClick={() => void previewPricing()}>Preview pricing</button>{preview && <section aria-label="Pricing preview"><h4>Pricing preview</h4><p>{preview.currency} {preview.totalAmount}</p><ul>{preview.breakdown.map((item, index) => <li key={`${item.itemType}-${index}`}>{item.description ?? item.itemType}: {item.lineAmount}</li>)}</ul><button onClick={() => void confirmPricing()}>Confirm this price</button></section>}<button disabled={!selected.readiness.readyToConfirm} onClick={() => setConfirmingFormation(true)}>Form course</button></>}{confirmingFormation && <section role="dialog" aria-label="Confirm course formation"><h4>Confirm course formation</h4><p>This creates the formal course, sessions, reservations, pricing snapshots and receivables in one transaction.</p><button onClick={() => void confirmFormation()}>Confirm formation</button><button onClick={() => setConfirmingFormation(false)}>Cancel</button></section>}<button onClick={() => { setSelected(null); setPreview(null); setConfirmingFormation(false); }}>Close match</button></section>}</section>;
}

function ReadinessItem({ label, ready }: { label: string; ready: boolean }) { return <li>{ready ? "✓" : "✗"} {label}</li>; }

type Detail = { kind: "application"; item: CoachApplication } | { kind: "availability"; item: AvailabilityProposal } | { kind: "lesson"; item: LessonRequest };
