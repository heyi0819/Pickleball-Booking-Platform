import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  use: { baseURL: "http://127.0.0.1:4173" },
  webServer: [
    { command: "npm run dev --workspace @pickleball/liff -- --host 127.0.0.1 --port 4173", url: "http://127.0.0.1:4173", reuseExistingServer: !process.env.CI, env: { ...process.env, VITE_E2E_LIFF: "true", VITE_LIFF_ID: "test-liff" } },
    { command: "npm run dev --workspace @pickleball/admin -- --host 127.0.0.1 --port 4174", url: "http://127.0.0.1:4174", reuseExistingServer: !process.env.CI },
  ],
});
