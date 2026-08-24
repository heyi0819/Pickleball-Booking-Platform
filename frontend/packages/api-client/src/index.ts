import {
  AuthenticationApi,
  CoachApplicationsApi,
  CoachAvailabilityApi,
  Configuration,
  CourseMatchInvitationsApi,
  CourseMatchesApi,
  CurrentUserApi,
  LessonRequestsApi,
  ResponseError,
  type AvailabilityProposal,
  type AvailabilityProposalRequest,
  type CoachApplication,
  type CoachApplicationRequest,
  type CourseMatch,
  type CourseMatchConfirmation,
  type CourseMatchCreateRequest,
  type CourseMatchInvitationResponse,
  type CourseMatchInvitationResponseRequest,
  type CourseMatchInvitationSummary,
  type CourseMatchPatchRequest,
  type CourseMatchPriceSnapshot,
  type CourseMatchPricingConfirmationRequest,
  type CourseMatchPricingPreview,
  type CourseMatchSummary,
  type LessonRequest,
  type LessonRequestCreateRequest,
  type ReviewRequest,
  type LoginData,
  type Me,
  type ProfileUpdateRequest,
  type RoleCode,
  type RoleContext,
} from "./generated/src";

export type {
  AvailabilityProposal,
  AvailabilityProposalRequest,
  CoachApplication,
  CoachApplicationRequest,
  CourseMatch,
  CourseMatchConfirmation,
  CourseMatchCreateRequest,
  CourseMatchInvitationResponse,
  CourseMatchInvitationResponseRequest,
  CourseMatchInvitationSummary,
  CourseMatchPatchRequest,
  CourseMatchPriceSnapshot,
  CourseMatchPricingConfirmationRequest,
  CourseMatchPricingPreview,
  CourseMatchSummary,
  LessonRequest,
  LessonRequestCreateRequest,
  LoginData as Login,
  Me,
  ProfileUpdateRequest as ProfileUpdate,
  ReviewRequest,
  RoleCode,
  RoleContext,
};
export type ApiClientOptions = { baseUrl: string };

export class ApiClientError extends Error {
  constructor(public readonly status: number, public readonly code: string) { super(code); }
}

async function mapError(caught: unknown): Promise<never> {
  if (caught instanceof ResponseError) {
    const body = await caught.response.clone().json().catch(() => null) as { error?: { code?: string } } | null;
    throw new ApiClientError(caught.response.status, body?.error?.code ?? "REQUEST_FAILED");
  }
  throw caught;
}

/** Handwritten adapter over the OpenAPI-generated client. Apps never construct URLs themselves. */
export function createApiClient({ baseUrl }: ApiClientOptions) {
  const anonymous = new AuthenticationApi(new Configuration({ basePath: baseUrl }));
  const authenticated = (token: string) => new CurrentUserApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  const coachApplications = (token: string) => new CoachApplicationsApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  const coachAvailability = (token: string) => new CoachAvailabilityApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  const lessonRequests = (token: string) => new LessonRequestsApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  const courseMatches = (token: string) => new CourseMatchesApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  const courseMatchInvitations = (token: string) => new CourseMatchInvitationsApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  return {
    baseUrl,
    async loginWithLine(idToken: string): Promise<LoginData> { try { return (await anonymous.loginWithLine({ lineLoginRequest: { idToken } })).data; } catch (caught) { return mapError(caught); } },
    async me(token: string): Promise<Me> { try { return (await authenticated(token).getCurrentUser()).data; } catch (caught) { return mapError(caught); } },
    async roles(token: string): Promise<RoleContext[]> { try { return (await authenticated(token).getCurrentUserRoles()).data; } catch (caught) { return mapError(caught); } },
    async updateProfile(token: string, profile: ProfileUpdateRequest): Promise<Me> { try { return (await authenticated(token).updateCurrentUserProfile({ profileUpdateRequest: profile })).data; } catch (caught) { return mapError(caught); } },
    async applyForCoach(token: string, request: CoachApplicationRequest): Promise<CoachApplication> { try { return (await coachApplications(token).createCoachApplication({ coachApplicationRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async myCoachApplications(token: string): Promise<CoachApplication[]> { try { return (await coachApplications(token).listMyCoachApplications()).data; } catch (caught) { return mapError(caught); } },
    async createAvailability(token: string, request: AvailabilityProposalRequest): Promise<AvailabilityProposal> { try { return (await coachAvailability(token).createAvailabilityProposal({ availabilityProposalRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async submitAvailability(token: string, id: string): Promise<AvailabilityProposal> { try { return (await coachAvailability(token).submitAvailabilityProposal({ id })).data; } catch (caught) { return mapError(caught); } },
    async myAvailability(token: string): Promise<AvailabilityProposal[]> { try { return (await coachAvailability(token).listMyAvailabilityProposals()).data; } catch (caught) { return mapError(caught); } },
    async approvedAvailability(token: string): Promise<AvailabilityProposal[]> { try { return (await coachAvailability(token).listApprovedAvailability()).data; } catch (caught) { return mapError(caught); } },
    async reviewCoachApplication(token: string, id: string, request: ReviewRequest): Promise<CoachApplication> { try { return (await coachApplications(token).reviewCoachApplication({ id, reviewRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async reviewAvailability(token: string, id: string, request: ReviewRequest): Promise<AvailabilityProposal> { try { return (await coachAvailability(token).reviewAvailabilityProposal({ id, reviewRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async coachApplicationsForReview(token: string, organizationId: string): Promise<CoachApplication[]> { try { return (await coachApplications(token).listCoachApplicationsForReview({ organizationId })).data; } catch (caught) { return mapError(caught); } },
    async availabilityForReview(token: string, organizationId: string): Promise<AvailabilityProposal[]> { try { return (await coachAvailability(token).listAvailabilityForReview({ organizationId })).data; } catch (caught) { return mapError(caught); } },
    async createLessonRequest(token: string, request: LessonRequestCreateRequest): Promise<LessonRequest> { try { return (await lessonRequests(token).createLessonRequest({ lessonRequestCreateRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async selectLessonRequestAvailability(token: string, id: string, selectedAvailabilityProposalId: string | null): Promise<LessonRequest> { try { return (await lessonRequests(token).updateLessonRequestSelectedAvailability({ id, selectedAvailabilityRequest: { selectedAvailabilityProposalId } })).data; } catch (caught) { return mapError(caught); } },
    async submitLessonRequest(token: string, id: string, idempotencyKey: string): Promise<LessonRequest> { try { return (await lessonRequests(token).submitLessonRequest({ id, idempotencyKey })).data; } catch (caught) { return mapError(caught); } },
    async myLessonRequests(token: string): Promise<LessonRequest[]> { try { return (await lessonRequests(token).listMyLessonRequests()).data; } catch (caught) { return mapError(caught); } },
    async lessonRequestsForReview(token: string, organizationId: string): Promise<LessonRequest[]> { try { return (await lessonRequests(token).listLessonRequestsForReview({ organizationId })).data; } catch (caught) { return mapError(caught); } },
    async reviewLessonRequest(token: string, id: string, request: ReviewRequest): Promise<LessonRequest> { try { return (await lessonRequests(token).reviewLessonRequest({ id, reviewRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async createCourseMatch(token: string, request: CourseMatchCreateRequest): Promise<CourseMatch> { try { return (await courseMatches(token).createCourseMatch({ courseMatchCreateRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async courseMatchesForReview(token: string, organizationId: string): Promise<CourseMatchSummary[]> { try { return (await courseMatches(token).listCourseMatches({ organizationId })).data; } catch (caught) { return mapError(caught); } },
    async courseMatchDetail(token: string, courseMatchId: string): Promise<CourseMatch> { try { return (await courseMatches(token).getCourseMatch({ courseMatchId })).data; } catch (caught) { return mapError(caught); } },
    async updateCourseMatch(token: string, courseMatchId: string, request: CourseMatchPatchRequest): Promise<CourseMatch> { try { return (await courseMatches(token).updateCourseMatch({ courseMatchId, courseMatchPatchRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async previewCourseMatchPricing(token: string, courseMatchId: string): Promise<CourseMatchPricingPreview> { try { return (await courseMatches(token).previewCourseMatchPricing({ courseMatchId })).data; } catch (caught) { return mapError(caught); } },
    async confirmCourseMatchPricing(token: string, courseMatchId: string, idempotencyKey: string, request: CourseMatchPricingConfirmationRequest): Promise<CourseMatchPriceSnapshot> { try { return (await courseMatches(token).confirmCourseMatchPricing({ courseMatchId, idempotencyKey, courseMatchPricingConfirmationRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async confirmCourseMatch(token: string, courseMatchId: string, idempotencyKey: string): Promise<CourseMatchConfirmation> { try { return (await courseMatches(token).confirmCourseMatch({ courseMatchId, idempotencyKey, courseMatchConfirmationRequest: { confirm: true } })).data; } catch (caught) { return mapError(caught); } },
    async myCourseMatchInvitations(token: string): Promise<CourseMatchInvitationSummary[]> { try { return (await courseMatchInvitations(token).listMyCourseMatchInvitations()).data; } catch (caught) { return mapError(caught); } },
    async respondCourseMatchInvitation(token: string, invitationId: string, request: CourseMatchInvitationResponseRequest): Promise<CourseMatchInvitationResponse> { try { return (await courseMatchInvitations(token).respondCourseMatchInvitation({ invitationId, courseMatchInvitationResponseRequest: request })).data; } catch (caught) { return mapError(caught); } },
  };
}
