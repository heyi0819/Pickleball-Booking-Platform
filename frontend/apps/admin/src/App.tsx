import { createApiClient } from "@pickleball/api-client";
import { platformName } from "@pickleball/shared";
import { PageShell } from "@pickleball/ui";

const api = createApiClient({ baseUrl: import.meta.env.VITE_API_BASE_URL ?? "/api/v1" });

export function App() {
  return <PageShell><h1>{platformName} Admin</h1><p>Admin foundation · API: {api.baseUrl}</p></PageShell>;
}
