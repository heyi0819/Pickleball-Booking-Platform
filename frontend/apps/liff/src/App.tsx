import { createApiClient, type Me, type RoleContext } from "@pickleball/api-client";
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
    {state === "home" && <><h2>{selectedRole?.roleCode} entry</h2><p>{selectedRole?.organizationName ?? "Platform-wide access"}</p></>}
  </PageShell>;
}
