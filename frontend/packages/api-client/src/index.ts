import {
  AdminFinanceApi,
  type AdminFinanceReceivable,
  type AdminFinanceReceivablePage,
  type ListAdminReceivablesRequest,
  type AdminFinancePayment,
  type AdminFinancePaymentPage,
  type ListAdminPaymentsRequest,
  type AdminFinanceRefund,
  type AdminFinanceRefundPage,
  type ListAdminRefundsRequest,
  AdminOperationsApi,
  AdminRoleDelegationApi,
  AuthenticationApi,
  CoachApplicationsApi,
  CoachAvailabilityApi,
  Configuration,
  CourseMatchInvitationsApi,
  CourseMatchesApi,
  CourseOfferingRegistrationsApi,
  CourseOfferingsApi,
  CourseOperationsApi,
  CurrentUserApi,
  FinanceApi,
  LessonRequestsApi,
  ResponseError,
  type AdminNotification,
  type AdminOrganization,
  type AdminUser,
  type CommitteeMembership,
  type AdminNotificationPage,
  type AdminOutboxEvent,
  type AdminOutboxEventPage,
  type AvailabilityProposal,
  type AvailabilityProposalRequest,
  type CoachApplication,
  type CoachApplicationRequest,
  type CoachCancellationReviewQueueItem,
  type CoachSessionCancellation,
  type CoachSessionCancellationReview,
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
  type CourseDetail,
  type CourseOperationReviewRequest,
  type CourseSessionSummary,
  type CourseSummary,
  type CourseOfferingCancellationRequest,
  type CourseOfferingConfirmation,
  type CourseOfferingCreateRequest,
  type CourseOfferingDetail,
  type CourseOfferingPriceSnapshot,
  type CourseOfferingPricingConfirmationRequest,
  type CourseOfferingPricingPreview,
  type CourseOfferingPricingPreviewRequest,
  type CourseOfferingRegistration,
  type CourseOfferingRegistrationCommand,
  type CourseOfferingRegistrationStatus,
  type CourseOfferingStatus,
  type CourseOfferingSummary,
  type CourseOfferingUpdateRequest,
  type FinancePaymentRequest,
  type FinancePaymentResponse,
  type FinanceRefundExecutionRequest,
  type FinanceRefundExecutionResponse,
  type FinanceRefundRequest,
  type FinanceRefundRequestResponse,
  type FinanceRefundReviewRequest,
  type FinanceRefundReviewResponse,
  type LessonRequest,
  type LessonRequestCreateRequest,
  type ReviewRequest,
  type LoginData,
  type Me,
  type MyCourseOfferingRegistration,
  type ProfileUpdateRequest,
  type RoleCode,
  type RoleContext,
  type SessionChangeRequest,
  type SessionChangeReviewQueueItem,
  type SessionEnrollmentCancellation,
  type SessionRescheduleResult,
} from "./generated/src";

export type {
  AdminFinanceReceivable,
  AdminFinanceReceivablePage,
  ListAdminReceivablesRequest,
  AdminFinancePayment,
  AdminFinancePaymentPage,
  ListAdminPaymentsRequest,
  AdminFinanceRefund,
  AdminFinanceRefundPage,
  ListAdminRefundsRequest,
  AdminNotification,
  AdminOrganization,
  AdminUser,
  CommitteeMembership,
  AdminNotificationPage,
  AdminOutboxEvent,
  AdminOutboxEventPage,
  AvailabilityProposal,
  AvailabilityProposalRequest,
  CoachApplication,
  CoachApplicationRequest,
  CoachCancellationReviewQueueItem,
  CoachSessionCancellation,
  CoachSessionCancellationReview,
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
  CourseDetail,
  CourseOperationReviewRequest,
  CourseSessionSummary,
  CourseSummary,
  CourseOfferingCancellationRequest,
  CourseOfferingConfirmation,
  CourseOfferingCreateRequest,
  CourseOfferingDetail,
  CourseOfferingPriceSnapshot,
  CourseOfferingPricingConfirmationRequest,
  CourseOfferingPricingPreview,
  CourseOfferingPricingPreviewRequest,
  CourseOfferingRegistration,
  CourseOfferingRegistrationCommand,
  CourseOfferingRegistrationStatus,
  CourseOfferingStatus,
  CourseOfferingSummary,
  CourseOfferingUpdateRequest,
  FinancePaymentRequest,
  FinancePaymentResponse,
  FinanceRefundExecutionRequest,
  FinanceRefundExecutionResponse,
  FinanceRefundRequest,
  FinanceRefundRequestResponse,
  FinanceRefundReviewRequest,
  FinanceRefundReviewResponse,
  LessonRequest,
  LessonRequestCreateRequest,
  LoginData as Login,
  Me,
  MyCourseOfferingRegistration,
  ProfileUpdateRequest as ProfileUpdate,
  ReviewRequest,
  RoleCode,
  RoleContext,
  SessionChangeRequest,
  SessionChangeReviewQueueItem,
  SessionEnrollmentCancellation,
  SessionRescheduleResult,
};
export type ApiClientOptions = { baseUrl: string };
export type CourseOfferingQuery = {
  organizationId?: string;
  status?: CourseOfferingStatus;
  from?: Date;
  to?: Date;
  coachProfileId?: string;
  skillLevel?: string;
  page?: number;
  size?: number;
  sort?: string;
};
export type CourseOperationsQuery = {
  organizationId?: string;
  status?: "DRAFT" | "ACTIVE" | "COMPLETED" | "CANCELLED";
  from?: Date;
  to?: Date;
  coachProfileId?: string;
  studentUserId?: string;
  courseType?: "PRIVATE" | "GROUP";
  page?: number;
  size?: number;
  sort?: "createdAt,desc" | "createdAt,asc" | "courseNo,asc" | "courseNo,desc" | "nextSessionAt,asc" | "nextSessionAt,desc";
};
export type AdminOperationsQuery = {
  organizationId: string;
  status?: "PENDING" | "PROCESSING" | "PROCESSED" | "SENDING" | "SENT" | "FAILED" | "DEAD";
  retryDue?: boolean;
  page?: number;
  size?: number;
};

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
  const adminFinance = (token: string) => new AdminFinanceApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  const authenticated = (token: string) => new CurrentUserApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  const coachApplications = (token: string) => new CoachApplicationsApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  const coachAvailability = (token: string) => new CoachAvailabilityApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  const lessonRequests = (token: string) => new LessonRequestsApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  const courseMatches = (token: string) => new CourseMatchesApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  const courseMatchInvitations = (token: string) => new CourseMatchInvitationsApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  const courseOfferings = (token: string) => new CourseOfferingsApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  const courseOfferingRegistrations = (token: string) => new CourseOfferingRegistrationsApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  const courseOperations = (token: string) => new CourseOperationsApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  const finance = (token: string) => new FinanceApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  const adminOperations = (token: string) => new AdminOperationsApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  const roleDelegation = (token: string) => new AdminRoleDelegationApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  return {
    async exchangeAdminLineAuthorizationCode(authorizationCode: string, codeVerifier: string, nonce: string): Promise<LoginData> {
      try { return (await anonymous.exchangeAdminLineAuthorizationCode({ adminLineExchangeRequest: { authorizationCode, codeVerifier, nonce } })).data; }
      catch (caught) { return mapError(caught); }
    },
    async listAdminReceivables(token: string, query: ListAdminReceivablesRequest): Promise<AdminFinanceReceivablePage> {
      try { return (await adminFinance(token).listAdminReceivables(query)).data; }
      catch (caught) { return mapError(caught); }
    },
    async getAdminReceivable(token: string, organizationId: string, id: string): Promise<AdminFinanceReceivable> {
      try { return (await adminFinance(token).getAdminReceivable({ organizationId, id })).data; }
      catch (caught) { return mapError(caught); }
    },
    async listAdminPayments(token: string, query: ListAdminPaymentsRequest): Promise<AdminFinancePaymentPage> {
      try { return (await adminFinance(token).listAdminPayments(query)).data; }
      catch (caught) { return mapError(caught); }
    },
    async getAdminPayment(token: string, organizationId: string, id: string): Promise<AdminFinancePayment> {
      try { return (await adminFinance(token).getAdminPayment({ organizationId, id })).data; }
      catch (caught) { return mapError(caught); }
    },
    async listAdminRefunds(token: string, query: ListAdminRefundsRequest): Promise<AdminFinanceRefundPage> {
      try { return (await adminFinance(token).listAdminRefunds(query)).data; }
      catch (caught) { return mapError(caught); }
    },
    async getAdminRefund(token: string, organizationId: string, id: string): Promise<AdminFinanceRefund> {
      try { return (await adminFinance(token).getAdminRefund({ organizationId, id })).data; }
      catch (caught) { return mapError(caught); }
    },
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
    async listCourseOfferings(token: string, query: CourseOfferingQuery = {}): Promise<CourseOfferingSummary[]> { try { return (await courseOfferings(token).listCourseOfferings(query)).data.items; } catch (caught) { return mapError(caught); } },
    async courseOfferingDetail(token: string, offeringId: string): Promise<CourseOfferingDetail> { try { return (await courseOfferings(token).getCourseOffering({ offeringId })).data; } catch (caught) { return mapError(caught); } },
    async createCourseOffering(token: string, request: CourseOfferingCreateRequest): Promise<CourseOfferingDetail> { try { return (await courseOfferings(token).createCourseOffering({ courseOfferingCreateRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async updateCourseOffering(token: string, offeringId: string, request: CourseOfferingUpdateRequest): Promise<CourseOfferingDetail> { try { return (await courseOfferings(token).updateCourseOffering({ offeringId, courseOfferingUpdateRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async previewCourseOfferingPricing(token: string, offeringId: string, request: CourseOfferingPricingPreviewRequest): Promise<CourseOfferingPricingPreview> { try { return (await courseOfferings(token).previewCourseOfferingPricing({ offeringId, courseOfferingPricingPreviewRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async confirmCourseOfferingPricing(token: string, offeringId: string, idempotencyKey: string, request: CourseOfferingPricingConfirmationRequest): Promise<CourseOfferingPriceSnapshot> { try { return (await courseOfferings(token).confirmCourseOfferingPricing({ offeringId, idempotencyKey, courseOfferingPricingConfirmationRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async publishCourseOffering(token: string, offeringId: string, idempotencyKey: string): Promise<CourseOfferingDetail> { try { return (await courseOfferings(token).publishCourseOffering({ offeringId, idempotencyKey })).data; } catch (caught) { return mapError(caught); } },
    async closeCourseOffering(token: string, offeringId: string, idempotencyKey: string): Promise<CourseOfferingDetail> { try { return (await courseOfferings(token).closeCourseOffering({ offeringId, idempotencyKey })).data; } catch (caught) { return mapError(caught); } },
    async confirmCourseOffering(token: string, offeringId: string, idempotencyKey: string): Promise<CourseOfferingConfirmation> { try { return (await courseOfferings(token).confirmCourseOffering({ offeringId, idempotencyKey, courseOfferingConfirmationRequest: { confirm: true } })).data; } catch (caught) { return mapError(caught); } },
    async cancelCourseOffering(token: string, offeringId: string, idempotencyKey: string, request?: CourseOfferingCancellationRequest): Promise<CourseOfferingDetail> { try { return (await courseOfferings(token).cancelCourseOffering({ offeringId, idempotencyKey, courseOfferingCancellationRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async listCourseOfferingRegistrations(token: string, offeringId: string, status?: CourseOfferingRegistrationStatus): Promise<CourseOfferingRegistration[]> { try { return (await courseOfferingRegistrations(token).listCourseOfferingRegistrations({ offeringId, status, size: 100 })).data.items; } catch (caught) { return mapError(caught); } },
    async myCourseOfferingRegistrations(token: string): Promise<MyCourseOfferingRegistration[]> { try { return (await courseOfferingRegistrations(token).listMyCourseOfferingRegistrations({ size: 100 })).data.items; } catch (caught) { return mapError(caught); } },
    async registerCourseOffering(token: string, offeringId: string, idempotencyKey: string): Promise<CourseOfferingRegistrationCommand> { try { return (await courseOfferingRegistrations(token).registerCourseOffering({ offeringId, idempotencyKey })).data; } catch (caught) { return mapError(caught); } },
    async cancelCourseOfferingRegistration(token: string, registrationId: string, idempotencyKey: string, request?: CourseOfferingCancellationRequest): Promise<CourseOfferingRegistrationCommand> { try { return (await courseOfferingRegistrations(token).cancelCourseOfferingRegistration({ registrationId, idempotencyKey, courseOfferingCancellationRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async listCourses(token: string, query: CourseOperationsQuery = {}): Promise<CourseSummary[]> { try { return (await courseOperations(token).listCourses({ ...query, size: query.size ?? 100 })).data.items; } catch (caught) { return mapError(caught); } },
    async courseDetail(token: string, courseId: string): Promise<CourseDetail> { try { return (await courseOperations(token).getCourse({ courseId })).data; } catch (caught) { return mapError(caught); } },
    async listCourseSessions(token: string, courseId: string): Promise<CourseSessionSummary[]> { try { return (await courseOperations(token).listCourseSessions({ courseId })).data; } catch (caught) { return mapError(caught); } },
    async cancelSessionEnrollment(token: string, enrollmentId: string, reason?: string | null): Promise<SessionEnrollmentCancellation> { try { return (await courseOperations(token).cancelSessionEnrollment({ enrollmentId, sessionEnrollmentCancellationRequest: reason === undefined ? undefined : { reason } })).data; } catch (caught) { return mapError(caught); } },
    async requestCoachSessionCancellation(token: string, sessionId: string, reason: string): Promise<CoachSessionCancellation> { try { return (await courseOperations(token).requestCoachSessionCancellation({ sessionId, coachSessionCancellationRequest: { reason } })).data; } catch (caught) { return mapError(caught); } },
    async createSessionRescheduleRequest(token: string, sessionId: string, idempotencyKey: string, proposedStartAt: Date, proposedEndAt: Date, reason: string): Promise<SessionChangeRequest> { try { return (await courseOperations(token).createSessionChangeRequest({ sessionId, idempotencyKey, sessionRescheduleRequest: { requestType: "RESCHEDULE", proposedStartAt, proposedEndAt, reason } })).data; } catch (caught) { return mapError(caught); } },
    async sessionChangeRequestsForReview(token: string, organizationId: string): Promise<SessionChangeReviewQueueItem[]> { try { return (await courseOperations(token).listSessionChangeRequestsForReview({ organizationId })).data; } catch (caught) { return mapError(caught); } },
    async coachCancellationRequestsForReview(token: string, organizationId: string): Promise<CoachCancellationReviewQueueItem[]> { try { return (await courseOperations(token).listCoachCancellationRequestsForReview({ organizationId })).data; } catch (caught) { return mapError(caught); } },
    async reviewSessionChangeRequest(token: string, requestId: string, idempotencyKey: string, request: CourseOperationReviewRequest): Promise<SessionRescheduleResult> { try { return (await courseOperations(token).reviewSessionChangeRequest({ requestId, idempotencyKey, courseOperationReviewRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async reviewCoachSessionCancellation(token: string, requestId: string, request: CourseOperationReviewRequest): Promise<CoachSessionCancellationReview> { try { return (await courseOperations(token).reviewCoachSessionCancellation({ requestId, courseOperationReviewRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async rescheduleCourseSession(token: string, sessionId: string, idempotencyKey: string, startAt: Date, endAt: Date, reason: string): Promise<SessionRescheduleResult> { try { return (await courseOperations(token).rescheduleCourseSession({ sessionId, idempotencyKey, directSessionRescheduleRequest: { startAt, endAt, reason } })).data; } catch (caught) { return mapError(caught); } },
    async recordReceivablePayment(token: string, receivableId: string, idempotencyKey: string, request: FinancePaymentRequest): Promise<FinancePaymentResponse> { try { return (await finance(token).recordReceivablePayment({ receivableId, idempotencyKey, financePaymentRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async requestReceivableRefund(token: string, receivableId: string, idempotencyKey: string, request: FinanceRefundRequest): Promise<FinanceRefundRequestResponse> { try { return (await finance(token).requestReceivableRefund({ receivableId, idempotencyKey, financeRefundRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async reviewRefund(token: string, refundId: string, idempotencyKey: string, request: FinanceRefundReviewRequest): Promise<FinanceRefundReviewResponse> { try { return (await finance(token).reviewRefund({ refundId, idempotencyKey, financeRefundReviewRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async executeRefund(token: string, refundId: string, idempotencyKey: string, request: FinanceRefundExecutionRequest): Promise<FinanceRefundExecutionResponse> { try { return (await finance(token).executeRefund({ refundId, idempotencyKey, financeRefundExecutionRequest: request })).data; } catch (caught) { return mapError(caught); } },
    async listAdminOutboxEvents(token: string, query: AdminOperationsQuery): Promise<AdminOutboxEventPage> { try { return (await adminOperations(token).listAdminOutboxEvents({ ...query, status: query.status as "PENDING" | "PROCESSING" | "PROCESSED" | "FAILED" | "DEAD" | undefined })).data; } catch (caught) { return mapError(caught); } },
    async retryAdminOutboxEvent(token: string, eventId: string, idempotencyKey: string, reason: string): Promise<AdminOutboxEvent> { try { return (await adminOperations(token).retryAdminOutboxEvent({ eventId, idempotencyKey, adminRecoveryRequest: { reason } })).data; } catch (caught) { return mapError(caught); } },
    async listAdminNotifications(token: string, query: AdminOperationsQuery): Promise<AdminNotificationPage> { try { return (await adminOperations(token).listAdminNotifications({ ...query, status: query.status as "PENDING" | "SENDING" | "SENT" | "FAILED" | "DEAD" | undefined })).data; } catch (caught) { return mapError(caught); } },
    async retryAdminNotification(token: string, notificationId: string, idempotencyKey: string, reason: string): Promise<AdminNotification> { try { return (await adminOperations(token).retryAdminNotification({ notificationId, idempotencyKey, adminRecoveryRequest: { reason } })).data; } catch (caught) { return mapError(caught); } },
    async listAdminOrganizations(token: string): Promise<AdminOrganization[]> { try { return (await roleDelegation(token).listAdminOrganizations()).data; } catch (caught) { return mapError(caught); } },
    async searchAdminUsers(token: string, query: string): Promise<AdminUser[]> { try { return (await roleDelegation(token).searchAdminUsers({ query })).data; } catch (caught) { return mapError(caught); } },
    async grantCommitteeMember(token: string, organizationId: string, userId: string): Promise<CommitteeMembership> { try { return (await roleDelegation(token).grantCommitteeMember({ organizationId, userId })).data; } catch (caught) { return mapError(caught); } },
    async revokeCommitteeMember(token: string, organizationId: string, userId: string): Promise<void> { try { await roleDelegation(token).revokeCommitteeMember({ organizationId, userId }); } catch (caught) { return mapError(caught); } },
  };
}
