import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import process from "node:process";

const reportPath = resolve(process.argv[2] ?? "test-results/slice9-playwright.json");
const report = JSON.parse(readFileSync(reportPath, "utf8"));
const requiredSpecs = [
  "admin.spec.ts",
  "slice1.spec.ts",
  "slice2.spec.ts",
  "slice2-coach.spec.ts",
  "slice3-matching.spec.ts",
  "slice4-open-enrollment.spec.ts",
  "slice5-course-operations.spec.ts",
  "slice6-finance.spec.ts",
  "s8-4-admin-operations.spec.ts",
];

const suites = flattenSuites(report.suites ?? []);
for (const file of requiredSpecs) {
  const specs = suites.filter((suite) => suite.file === file).flatMap((suite) => suite.specs ?? []);
  if (specs.length === 0) throw new Error(`Missing Slice 9 Playwright evidence for ${file}`);
  for (const spec of specs) {
    for (const test of spec.tests ?? []) {
      const result = test.results?.at(-1);
      if (!result || result.status !== "passed") {
        throw new Error(`Slice 9 Playwright evidence is not passing: ${file} > ${spec.title}`);
      }
    }
  }
  process.stdout.write(`PASS ${file}: ${specs.length} test(s)\n`);
}

if ((report.errors ?? []).length > 0 || report.stats?.unexpected !== 0 || report.stats?.flaky !== 0) {
  throw new Error("Playwright report contains unexpected, flaky, or reporter errors");
}

process.stdout.write("Slice 9 frontend acceptance evidence PASS\n");

function flattenSuites(suites) {
  return suites.flatMap((suite) => [suite, ...flattenSuites(suite.suites ?? [])]);
}
