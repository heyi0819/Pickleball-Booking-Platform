import { cpSync, existsSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { tmpdir } from "node:os";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import process from "node:process";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const openapi = resolve(root, "..", "backend", "src", "main", "resources", "openapi", "openapi.yaml");
const target = resolve(root, "packages", "api-client", "src", "generated");
const config = resolve(root, "packages", "api-client", "openapi-generator.config.json");
const binary = resolve(root, "node_modules", "@openapitools", "openapi-generator-cli", "main.js");
const staging = mkdtempSync(join(tmpdir(), "pickleball-openapi-"));
const checkOnly = process.argv.includes("--check");

function normalize(dir) {
  for (const relative of files(dir)) {
    const path = join(dir, relative);
    writeFileSync(path, normalizedContent(path), "utf8");
  }
}
function normalizedContent(path) {
  return readFileSync(path, "utf8").replace(/\r\n/g, "\n").replace(/[ \t]+$/gm, "").replace(/\n+$/g, "") + "\n";
}
function files(dir, prefix = "") {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const relative = join(prefix, entry.name);
    return entry.isDirectory() ? files(join(dir, entry.name), relative) : [relative];
  });
}
try {
  execFileSync(process.execPath, [binary, "generate", "-i", relative(root, openapi).replaceAll("\\", "/"), "-g", "typescript-fetch", "-o", staging, "-c", config, "--skip-validate-spec"], { cwd: root, stdio: "inherit" });
  normalize(staging);
  if (checkOnly) {
    const generated = files(staging);
    const changed = generated.some((relative) => !existsSync(join(target, relative)) || normalizedContent(join(target, relative)) !== normalizedContent(join(staging, relative)));
    const stale = files(target).some((relative) => !existsSync(join(staging, relative)));
    if (changed || stale) throw new Error("OpenAPI generated client is stale. Run npm run api:generate.");
  } else {
    cpSync(staging, target, { recursive: true, force: true });
  }
} finally {
  rmSync(staging, { recursive: true, force: true });
}
