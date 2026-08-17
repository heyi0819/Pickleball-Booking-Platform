import { createApiClient, type Me } from "@pickleball/api-client";
import { platformName } from "@pickleball/shared";
import { PageShell } from "@pickleball/ui";
import { useEffect, useState } from "react";

const api = createApiClient({ baseUrl: import.meta.env.VITE_API_BASE_URL ?? "/api/v1" });

export function App() {
  const [me, setMe] = useState<Me | null>(null);
  const [state, setState] = useState<"loading" | "allowed" | "forbidden">("loading");
  useEffect(() => { const token = sessionStorage.getItem("platform.access-token"); if (!token) { setState("forbidden"); return; } void api.me(token).then((current) => { setMe(current); setState(current.roles.some((role) => role.roleCode === "COMMITTEE" || role.roleCode === "PLATFORM_ADMIN") ? "allowed" : "forbidden"); }).catch(() => { sessionStorage.removeItem("platform.access-token"); setState("forbidden"); }); }, []);
  return <PageShell><h1>{platformName} Admin</h1>{state === "loading" && <p>Checking access…</p>}{state === "forbidden" && <p role="alert">Forbidden: administrator access is required.</p>}{state === "allowed" && <><h2>Authorized admin entry</h2><p>Signed in as {me?.displayName}</p></>}</PageShell>;
}
