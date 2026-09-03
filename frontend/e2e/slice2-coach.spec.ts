import { expect, test } from "@playwright/test";

const org = "00000000-0000-0000-0000-000000000010";
const applicant = "00000000-0000-0000-0000-000000000001";
const application = { id: "00000000-0000-0000-0000-000000000101", coachProfileId: "00000000-0000-0000-0000-000000000102", status: "SUBMITTED", applicationNote: "Coach application from LIFF", submittedAt: "2030-01-01T10:00:00Z", reviewedBy: null, reviewedAt: null, reviewNote: null };
const availability = { id: "00000000-0000-0000-0000-000000000201", coachProfileId: application.coachProfileId, startAt: "2030-01-02T10:00:00Z", endAt: "2030-01-02T11:00:00Z", preferredVenueId: null, status: "DRAFT", submittedAt: null, reviewedBy: null, reviewedAt: null, reviewNote: null };
const envelope = (data: unknown) => ({ data, meta: { requestId: "slice2-coach" } });

test("student submits a coach application and committee approves it", async ({ browser }) => {
  const student = await browser.newContext(); const page = await student.newPage(); let approved = false;
  await page.route("**/api/v1/auth/line/login", route => route.fulfill({ json: envelope({ accessToken: "student-token", tokenType: "Bearer", expiresIn: 1800, user: { id: applicant, displayName: "Applicant", roles: ["STUDENT"] } }) }));
  await page.route("**/api/v1/me", route => route.fulfill({ json: envelope({ id: applicant, displayName: "Applicant", email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "STUDENT", organizationId: org, organizationCode: "MVP", organizationName: "MVP" }] }) }));
  await page.route("**/api/v1/coach-availability-proposals/available", route => route.fulfill({ json: envelope([]) })); await page.route("**/api/v1/lesson-requests/mine", route => route.fulfill({ json: envelope([]) }));
  await page.route("**/api/v1/coach-applications", route => route.fulfill({ status: 201, json: envelope(application) }));
  await page.goto("/"); await page.getByRole("navigation", { name: "主要導覽" }).getByRole("button", { name: "找課與需求" }).click(); await page.getByRole("button", { name: "申請成為教練" }).click(); await expect(page.getByText("教練申請已送出，等待委員會審核。")).toBeVisible();
  const committee = await browser.newContext(); await committee.addInitScript(() => sessionStorage.setItem("platform.access-token", "committee-token")); const admin = await committee.newPage();
  await admin.route("**/api/v1/me", route => route.fulfill({ json: envelope({ id: "committee", displayName: "Committee", email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "COMMITTEE", organizationId: org, organizationCode: "MVP", organizationName: "MVP" }] }) }));
  await admin.route("**/api/v1/coach-applications?organizationId=" + org, route => route.fulfill({ json: envelope([{ ...application, status: approved ? "APPROVED" : "SUBMITTED" }]) }));
  await admin.route("**/api/v1/coach-availability-proposals?organizationId=" + org, route => route.fulfill({ json: envelope([]) })); await admin.route("**/api/v1/lesson-requests?organizationId=" + org, route => route.fulfill({ json: envelope([]) })); await admin.route("**/api/v1/course-matches?organizationId=" + org, route => route.fulfill({ json: envelope([]) }));
  await admin.route("**/api/v1/coach-applications/*/review", route => { approved = true; return route.fulfill({ json: envelope({ ...application, status: "APPROVED" }) }); });
  await admin.goto("http://127.0.0.1:4174"); await admin.getByRole("button", { name: "查看申請" }).click(); await expect(admin.getByRole("heading", { name: "教練申請詳情" })).toBeVisible(); await admin.getByRole("button", { name: "審核通過" }).last().click(); await expect(admin.getByText("審核結果已儲存。")).toBeVisible();
  await student.close(); await committee.close();
});

test("coach submits availability, committee approves it, and students can see it", async ({ browser }) => {
  const coach = await browser.newContext(); await coach.addInitScript(() => sessionStorage.setItem("platform.access-token", "coach-token")); const page = await coach.newPage(); let submitted = false;
  await page.route("**/api/v1/me", route => route.fulfill({ json: envelope({ id: applicant, displayName: "Coach", email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "COACH", organizationId: org, organizationCode: "MVP", organizationName: "MVP" }] }) }));
  await page.route("**/api/v1/coach-availability-proposals/mine", route => route.fulfill({ json: envelope([submitted ? { ...availability, status: "SUBMITTED" } : availability]) }));
  await page.route("**/api/v1/course-match-invitations/mine", route => route.fulfill({ json: envelope([]) }));
  await page.route("**/api/v1/coach-availability-proposals", route => route.fulfill({ status: 201, json: envelope(availability) })); await page.route("**/api/v1/coach-availability-proposals/*/submission", route => { submitted = true; return route.fulfill({ json: envelope({ ...availability, status: "SUBMITTED" }) }); });
  await page.goto("/"); await page.getByRole("navigation", { name: "主要導覽" }).getByRole("button", { name: "可授課時段" }).click(); await expect(page.getByRole("heading", { name: "我的可授課時段" })).toBeVisible(); await page.getByRole("button", { name: "送審" }).click(); await expect(page.getByText("可授課時段已送審。")).toBeVisible();
  await coach.close();
});
