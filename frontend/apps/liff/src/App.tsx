import { PageShell } from "@pickleball/ui";
import { platformName } from "@pickleball/shared";

export function App() {
  return <PageShell><h1>{platformName}</h1><p>LIFF member app foundation</p></PageShell>;
}
