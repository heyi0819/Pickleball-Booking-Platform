import { expect, test } from "@playwright/test";

const organizationId = "00000000-0000-0000-0000-000000000010";
const availability = { id: "00000000-0000-0000-0000-000000000100", coachProfileId: "00000000-0000-0000-0000-000000000200", startAt: "2030-01-01T10:00:00Z", endAt: "2030-01-01T11:00:00Z", preferredVenueId: null, status: "APPROVED", submittedAt: null, reviewedBy: null, reviewedAt: null, reviewNote: null };
const draft = { id: "00000000-0000-0000-0000-000000000300", requesterUserId: "00000000-0000-0000-0000-000000000001", preferredCoachProfileId: availability.coachProfileId, selectedAvailabilityProposalId: availability.id, lessonType: "PRIVATE", scheduleType: "SINGLE", billingMode: "PER_SESSION", skillLevel: null, participantCount: 1, guestParticipantCount: 0, minimumParticipants: null, maximumParticipants: null, requestedSessionCount: 1, status: "DRAFT", notes: null, submittedAt: null, reviewedBy: null, reviewedAt: null, reviewNote: null };

test("student creates a selected draft and sees the claimed-availability recovery message", async ({ page }) => {
  let created = false;
  const envelope = (data: unknown) => ({ data, meta: { requestId: "slice2" } });
  await page.route("**/api/v1/auth/line/login", (route) => route.fulfill({ json: envelope({ accessToken: "test-token", tokenType: "Bearer", expiresIn: 1800, user: { id: draft.requesterUserId, displayName: "Student", roles: ["STUDENT"] } }) }));
  await page.route("**/api/v1/me", (route) => route.fulfill({ json: envelope({ id: draft.requesterUserId, displayName: "Student", email: null, locale: "zh-TW", profileComplete: true, roles: [{ roleCode: "STUDENT", organizationId, organizationCode: "MVP", organizationName: "MVP" }] }) }));
  await page.route("**/api/v1/coach-availability-proposals/available", (route) => route.fulfill({ json: envelope([availability]) }));
  await page.route("**/api/v1/lesson-requests/mine", (route) => route.fulfill({ json: envelope(created ? [draft] : []) }));
  await page.route("**/api/v1/lesson-requests", (route) => { created = true; return route.fulfill({ status: 201, json: envelope(draft) }); });
  await page.route("**/api/v1/lesson-requests/*/submission", (route) => route.fulfill({ status: 409, json: { error: { code: "AVAILABILITY_ALREADY_CLAIMED" } } }));
  await page.goto("/");
  await page.getByRole("navigation", { name: "主要導覽" }).getByRole("button", { name: "找課與需求" }).click();
  await expect(page.getByRole("heading", { name: "找教練時段" })).toBeVisible();
  await page.getByRole("combobox", { name: "Approved availability" }).selectOption(availability.id);
  await page.getByRole("button", { name: "Create lesson draft" }).click();
  await expect(page.getByText("需求草稿已儲存。送出後將交由委員會處理。")).toBeVisible();
  await page.getByRole("button", { name: "Submit" }).click();
  await expect(page.getByText("該時段已被其他需求取得，請重新整理並選擇其他時段。")).toBeVisible();
});
