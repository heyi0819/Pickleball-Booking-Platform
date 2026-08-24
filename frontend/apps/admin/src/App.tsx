import { createApiClient, type AvailabilityProposal, type CoachApplication, type LessonRequest, type Me } from "@pickleball/api-client";
import { platformName } from "@pickleball/shared";
import { PageShell } from "@pickleball/ui";
import { useEffect, useState } from "react";

const api = createApiClient({ baseUrl: import.meta.env.VITE_API_BASE_URL ?? "/api/v1" });

export function App() {
  const [me, setMe] = useState<Me | null>(null);
  const [state, setState] = useState<"loading" | "allowed" | "forbidden">("loading");
  useEffect(() => { const token = sessionStorage.getItem("platform.access-token"); if (!token) { setState("forbidden"); return; } void api.me(token).then((current) => { setMe(current); setState(current.roles.some((role) => role.roleCode === "COMMITTEE" || role.roleCode === "PLATFORM_ADMIN") ? "allowed" : "forbidden"); }).catch(() => { sessionStorage.removeItem("platform.access-token"); setState("forbidden"); }); }, []);
  const committeeRole = me?.roles.find((role) => role.roleCode === "COMMITTEE" && role.organizationId);
  return <PageShell><h1>{platformName} Admin</h1>{state === "loading" && <p>Checking access…</p>}{state === "forbidden" && <p role="alert">Forbidden: administrator access is required.</p>}{state === "allowed" && <><h2>Authorized admin entry</h2><p>Signed in as {me?.displayName}</p>{committeeRole?.organizationId ? <CommitteeReview token={sessionStorage.getItem("platform.access-token") ?? ""} organizationId={committeeRole.organizationId} /> : <p>A committee organization role is required to review Slice 2 work.</p>}</>}</PageShell>;
}

function CommitteeReview({ token, organizationId }: { token: string; organizationId: string }) {
  const [applications, setApplications] = useState<CoachApplication[]>([]); const [availability, setAvailability] = useState<AvailabilityProposal[]>([]); const [requests, setRequests] = useState<LessonRequest[]>([]); const [message, setMessage] = useState(""); const [detail, setDetail] = useState<Detail | null>(null);
  const refresh = async () => { const [app, slots, lessons] = await Promise.all([api.coachApplicationsForReview(token, organizationId), api.availabilityForReview(token, organizationId), api.lessonRequestsForReview(token, organizationId)]); setApplications(app); setAvailability(slots); setRequests(lessons); };
  useEffect(() => { void refresh().catch(() => setMessage("Unable to load review queues.")); }, [token, organizationId]);
  async function review(kind: "application" | "availability" | "lesson", id: string, decision: "APPROVE" | "REJECT") { try { const request = { decision, reviewNote: `${decision} via committee review` }; if (kind === "application") await api.reviewCoachApplication(token, id, request); else if (kind === "availability") await api.reviewAvailability(token, id, request); else await api.reviewLessonRequest(token, id, request); setMessage("Review saved."); setDetail(null); await refresh(); } catch { setMessage("Unable to save review."); } }
  const actions = (kind: "application" | "availability" | "lesson", id: string, status: string) => status === "SUBMITTED" ? <><button onClick={() => void review(kind, id, "APPROVE")}>Approve</button><button onClick={() => void review(kind, id, "REJECT")}>Reject</button></> : null;
  return <section><h2>Committee review</h2>{message && <p role="status">{message}</p>}<h3>Coach applications</h3><ul>{applications.map((item) => <li key={item.id}><button onClick={() => setDetail({ kind: "application", item })}>View application</button> {item.status} {actions("application", item.id, item.status)}</li>)}</ul><h3>Availability proposals</h3><ul>{availability.map((item) => <li key={item.id}><button onClick={() => setDetail({ kind: "availability", item })}>View availability</button> {new Date(item.startAt).toLocaleString()} — {item.status} {actions("availability", item.id, item.status)}</li>)}</ul><h3>Lesson requests</h3><ul>{requests.map((item) => <li key={item.id}><button onClick={() => setDetail({ kind: "lesson", item })}>View lesson request</button> {item.status} {actions("lesson", item.id, item.status)}</li>)}</ul>{detail && <section aria-label="Review detail"><h3>{detail.kind === "application" ? "Coach application detail" : detail.kind === "availability" ? "Availability proposal detail" : "Lesson request detail"}</h3><dl>{Object.entries(detail.item).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{String(value ?? "")}</dd></div>)}</dl>{actions(detail.kind, detail.item.id, detail.item.status)}<button onClick={() => setDetail(null)}>Close detail</button></section>}</section>;
}

type Detail = { kind: "application"; item: CoachApplication } | { kind: "availability"; item: AvailabilityProposal } | { kind: "lesson"; item: LessonRequest };
