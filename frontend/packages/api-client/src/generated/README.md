# @pickleball/api-client-generated@0.0.1

A TypeScript SDK client for the localhost API.

## Usage

First, install the SDK from npm.

```bash
npm install @pickleball/api-client-generated --save
```

Next, try it out.


```ts
import {
  Configuration,
  AuthenticationApi,
} from '@pickleball/api-client-generated';
import type { LoginWithLineRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const api = new AuthenticationApi();

  const body = {
    // LineLoginRequest
    lineLoginRequest: ...,
  } satisfies LoginWithLineRequest;

  try {
    const data = await api.loginWithLine(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```


## Documentation

### API Endpoints

All URIs are relative to */api/v1*

| Class | Method | HTTP request | Description
| ----- | ------ | ------------ | -------------
*AuthenticationApi* | [**loginWithLine**](docs/AuthenticationApi.md#loginwithline) | **POST** /auth/line/login |
*CoachApplicationsApi* | [**createCoachApplication**](docs/CoachApplicationsApi.md#createcoachapplication) | **POST** /coach-applications |
*CoachApplicationsApi* | [**listCoachApplicationsForReview**](docs/CoachApplicationsApi.md#listcoachapplicationsforreview) | **GET** /coach-applications |
*CoachApplicationsApi* | [**listMyCoachApplications**](docs/CoachApplicationsApi.md#listmycoachapplications) | **GET** /coach-applications/mine |
*CoachApplicationsApi* | [**reviewCoachApplication**](docs/CoachApplicationsApi.md#reviewcoachapplication) | **POST** /coach-applications/{id}/review |
*CoachAvailabilityApi* | [**closeAvailabilityProposal**](docs/CoachAvailabilityApi.md#closeavailabilityproposal) | **POST** /coach-availability-proposals/{id}/close |
*CoachAvailabilityApi* | [**createAvailabilityProposal**](docs/CoachAvailabilityApi.md#createavailabilityproposal) | **POST** /coach-availability-proposals |
*CoachAvailabilityApi* | [**listApprovedAvailability**](docs/CoachAvailabilityApi.md#listapprovedavailability) | **GET** /coach-availability-proposals/available |
*CoachAvailabilityApi* | [**listAvailabilityForReview**](docs/CoachAvailabilityApi.md#listavailabilityforreview) | **GET** /coach-availability-proposals |
*CoachAvailabilityApi* | [**listMyAvailabilityProposals**](docs/CoachAvailabilityApi.md#listmyavailabilityproposals) | **GET** /coach-availability-proposals/mine |
*CoachAvailabilityApi* | [**reviewAvailabilityProposal**](docs/CoachAvailabilityApi.md#reviewavailabilityproposal) | **POST** /coach-availability-proposals/{id}/review |
*CoachAvailabilityApi* | [**submitAvailabilityProposal**](docs/CoachAvailabilityApi.md#submitavailabilityproposal) | **POST** /coach-availability-proposals/{id}/submission |
*CourseMatchInvitationsApi* | [**listMyCourseMatchInvitations**](docs/CourseMatchInvitationsApi.md#listmycoursematchinvitations) | **GET** /course-match-invitations/mine |
*CourseMatchInvitationsApi* | [**respondCourseMatchInvitation**](docs/CourseMatchInvitationsApi.md#respondcoursematchinvitation) | **POST** /course-match-invitations/{invitationId}/response |
*CourseMatchesApi* | [**confirmCourseMatch**](docs/CourseMatchesApi.md#confirmcoursematch) | **POST** /course-matches/{courseMatchId}/confirmation |
*CourseMatchesApi* | [**confirmCourseMatchPricing**](docs/CourseMatchesApi.md#confirmcoursematchpricing) | **POST** /course-matches/{courseMatchId}/pricing-confirmation |
*CourseMatchesApi* | [**createCourseMatch**](docs/CourseMatchesApi.md#createcoursematch) | **POST** /course-matches |
*CourseMatchesApi* | [**getCourseMatch**](docs/CourseMatchesApi.md#getcoursematch) | **GET** /course-matches/{courseMatchId} |
*CourseMatchesApi* | [**listCourseMatches**](docs/CourseMatchesApi.md#listcoursematches) | **GET** /course-matches |
*CourseMatchesApi* | [**previewCourseMatchPricing**](docs/CourseMatchesApi.md#previewcoursematchpricing) | **POST** /course-matches/{courseMatchId}/pricing-preview |
*CourseMatchesApi* | [**updateCourseMatch**](docs/CourseMatchesApi.md#updatecoursematch) | **PATCH** /course-matches/{courseMatchId} |
*CourseOfferingRegistrationsApi* | [**cancelCourseOfferingRegistration**](docs/CourseOfferingRegistrationsApi.md#cancelcourseofferingregistration) | **POST** /course-offering-registrations/{registrationId}/cancellation |
*CourseOfferingRegistrationsApi* | [**listCourseOfferingRegistrations**](docs/CourseOfferingRegistrationsApi.md#listcourseofferingregistrations) | **GET** /course-offerings/{offeringId}/registrations |
*CourseOfferingRegistrationsApi* | [**listMyCourseOfferingRegistrations**](docs/CourseOfferingRegistrationsApi.md#listmycourseofferingregistrations) | **GET** /me/course-offering-registrations |
*CourseOfferingRegistrationsApi* | [**registerCourseOffering**](docs/CourseOfferingRegistrationsApi.md#registercourseoffering) | **POST** /course-offerings/{offeringId}/registrations |
*CourseOfferingsApi* | [**cancelCourseOffering**](docs/CourseOfferingsApi.md#cancelcourseoffering) | **POST** /course-offerings/{offeringId}/cancellation |
*CourseOfferingsApi* | [**closeCourseOffering**](docs/CourseOfferingsApi.md#closecourseoffering) | **POST** /course-offerings/{offeringId}/closure |
*CourseOfferingsApi* | [**confirmCourseOffering**](docs/CourseOfferingsApi.md#confirmcourseoffering) | **POST** /course-offerings/{offeringId}/confirmation |
*CourseOfferingsApi* | [**confirmCourseOfferingPricing**](docs/CourseOfferingsApi.md#confirmcourseofferingpricing) | **POST** /course-offerings/{offeringId}/pricing-confirmation |
*CourseOfferingsApi* | [**createCourseOffering**](docs/CourseOfferingsApi.md#createcourseoffering) | **POST** /course-offerings |
*CourseOfferingsApi* | [**getCourseOffering**](docs/CourseOfferingsApi.md#getcourseoffering) | **GET** /course-offerings/{offeringId} |
*CourseOfferingsApi* | [**listCourseOfferings**](docs/CourseOfferingsApi.md#listcourseofferings) | **GET** /course-offerings |
*CourseOfferingsApi* | [**previewCourseOfferingPricing**](docs/CourseOfferingsApi.md#previewcourseofferingpricing) | **POST** /course-offerings/{offeringId}/pricing-preview |
*CourseOfferingsApi* | [**publishCourseOffering**](docs/CourseOfferingsApi.md#publishcourseoffering) | **POST** /course-offerings/{offeringId}/publication |
*CourseOfferingsApi* | [**updateCourseOffering**](docs/CourseOfferingsApi.md#updatecourseoffering) | **PATCH** /course-offerings/{offeringId} |
*CourseOperationsApi* | [**cancelSessionEnrollment**](docs/CourseOperationsApi.md#cancelsessionenrollment) | **POST** /session-enrollments/{enrollmentId}/cancellation |
*CourseOperationsApi* | [**createSessionChangeRequest**](docs/CourseOperationsApi.md#createsessionchangerequest) | **POST** /course-sessions/{sessionId}/change-requests |
*CourseOperationsApi* | [**getCourse**](docs/CourseOperationsApi.md#getcourse) | **GET** /courses/{courseId} |
*CourseOperationsApi* | [**getCourseSession**](docs/CourseOperationsApi.md#getcoursesession) | **GET** /course-sessions/{sessionId} |
*CourseOperationsApi* | [**listCoachCancellationRequestsForReview**](docs/CourseOperationsApi.md#listcoachcancellationrequestsforreview) | **GET** /coach-cancellation-requests |
*CourseOperationsApi* | [**listCourseSessions**](docs/CourseOperationsApi.md#listcoursesessions) | **GET** /courses/{courseId}/sessions |
*CourseOperationsApi* | [**listCourses**](docs/CourseOperationsApi.md#listcourses) | **GET** /courses |
*CourseOperationsApi* | [**listSessionChangeRequestsForReview**](docs/CourseOperationsApi.md#listsessionchangerequestsforreview) | **GET** /session-change-requests |
*CourseOperationsApi* | [**requestCoachSessionCancellation**](docs/CourseOperationsApi.md#requestcoachsessioncancellation) | **POST** /course-sessions/{sessionId}/coach-cancellation-requests |
*CourseOperationsApi* | [**rescheduleCourseSession**](docs/CourseOperationsApi.md#reschedulecoursesession) | **POST** /course-sessions/{sessionId}/reschedule |
*CourseOperationsApi* | [**reviewCoachSessionCancellation**](docs/CourseOperationsApi.md#reviewcoachsessioncancellation) | **POST** /coach-cancellation-requests/{requestId}/review |
*CourseOperationsApi* | [**reviewSessionChangeRequest**](docs/CourseOperationsApi.md#reviewsessionchangerequest) | **POST** /session-change-requests/{requestId}/review |
*CurrentUserApi* | [**getCurrentUser**](docs/CurrentUserApi.md#getcurrentuser) | **GET** /me |
*CurrentUserApi* | [**getCurrentUserRoles**](docs/CurrentUserApi.md#getcurrentuserroles) | **GET** /me/roles |
*CurrentUserApi* | [**updateCurrentUserProfile**](docs/CurrentUserApi.md#updatecurrentuserprofile) | **PATCH** /me/profile |
*FinanceApi* | [**executeRefund**](docs/FinanceApi.md#executerefund) | **POST** /refunds/{refundId}/execution |
*FinanceApi* | [**recordReceivablePayment**](docs/FinanceApi.md#recordreceivablepayment) | **POST** /receivables/{receivableId}/payments |
*FinanceApi* | [**requestReceivableRefund**](docs/FinanceApi.md#requestreceivablerefund) | **POST** /receivables/{receivableId}/refunds |
*FinanceApi* | [**reviewRefund**](docs/FinanceApi.md#reviewrefund) | **POST** /refunds/{refundId}/review |
*LessonRequestsApi* | [**createLessonRequest**](docs/LessonRequestsApi.md#createlessonrequest) | **POST** /lesson-requests |
*LessonRequestsApi* | [**getLessonRequest**](docs/LessonRequestsApi.md#getlessonrequest) | **GET** /lesson-requests/{id} |
*LessonRequestsApi* | [**listLessonRequestsForReview**](docs/LessonRequestsApi.md#listlessonrequestsforreview) | **GET** /lesson-requests |
*LessonRequestsApi* | [**listMyLessonRequests**](docs/LessonRequestsApi.md#listmylessonrequests) | **GET** /lesson-requests/mine |
*LessonRequestsApi* | [**reviewLessonRequest**](docs/LessonRequestsApi.md#reviewlessonrequest) | **POST** /lesson-requests/{id}/review |
*LessonRequestsApi* | [**submitLessonRequest**](docs/LessonRequestsApi.md#submitlessonrequest) | **POST** /lesson-requests/{id}/submission |
*LessonRequestsApi* | [**updateLessonRequestSelectedAvailability**](docs/LessonRequestsApi.md#updatelessonrequestselectedavailability) | **PATCH** /lesson-requests/{id}/selected-availability |
*PayoutsApi* | [**createPayoutBatch**](docs/PayoutsApi.md#createpayoutbatch) | **POST** /payout-batches |
*PayoutsApi* | [**executePayoutBatch**](docs/PayoutsApi.md#executepayoutbatch) | **POST** /payout-batches/{batchId}/execution |
*PayoutsApi* | [**getPayoutBatch**](docs/PayoutsApi.md#getpayoutbatch) | **GET** /payout-batches/{batchId} |
*SettlementApi* | [**calculateCourseSessionSettlement**](docs/SettlementApi.md#calculatecoursesessionsettlement) | **POST** /course-sessions/{sessionId}/settlement-calculation |
*SettlementApi* | [**confirmSessionSettlement**](docs/SettlementApi.md#confirmsessionsettlement) | **POST** /session-settlements/{settlementId}/confirmation |
*SettlementApi* | [**getCourseSessionSettlement**](docs/SettlementApi.md#getcoursesessionsettlement) | **GET** /course-sessions/{sessionId}/settlement |
*SettlementApi* | [**listMyCoachSettlements**](docs/SettlementApi.md#listmycoachsettlements) | **GET** /me/coach-settlements |


### Models

- [AvailabilityProposal](docs/AvailabilityProposal.md)
- [AvailabilityProposalEnvelope](docs/AvailabilityProposalEnvelope.md)
- [AvailabilityProposalListEnvelope](docs/AvailabilityProposalListEnvelope.md)
- [AvailabilityProposalRequest](docs/AvailabilityProposalRequest.md)
- [CoachApplication](docs/CoachApplication.md)
- [CoachApplicationEnvelope](docs/CoachApplicationEnvelope.md)
- [CoachApplicationListEnvelope](docs/CoachApplicationListEnvelope.md)
- [CoachApplicationRequest](docs/CoachApplicationRequest.md)
- [CoachCancellationReviewQueueEnvelope](docs/CoachCancellationReviewQueueEnvelope.md)
- [CoachCancellationReviewQueueItem](docs/CoachCancellationReviewQueueItem.md)
- [CoachPayoutStatus](docs/CoachPayoutStatus.md)
- [CoachSessionCancellation](docs/CoachSessionCancellation.md)
- [CoachSessionCancellationEnvelope](docs/CoachSessionCancellationEnvelope.md)
- [CoachSessionCancellationRequest](docs/CoachSessionCancellationRequest.md)
- [CoachSessionCancellationReview](docs/CoachSessionCancellationReview.md)
- [CoachSessionCancellationReviewEnvelope](docs/CoachSessionCancellationReviewEnvelope.md)
- [CoachSettlementAllocationResponse](docs/CoachSettlementAllocationResponse.md)
- [CoachSettlementSelfItem](docs/CoachSettlementSelfItem.md)
- [CoachSettlementSelfList](docs/CoachSettlementSelfList.md)
- [CoachSettlementSelfListEnvelope](docs/CoachSettlementSelfListEnvelope.md)
- [CourseDetail](docs/CourseDetail.md)
- [CourseDetailEnvelope](docs/CourseDetailEnvelope.md)
- [CourseMatch](docs/CourseMatch.md)
- [CourseMatchCoachAssignmentRequest](docs/CourseMatchCoachAssignmentRequest.md)
- [CourseMatchConfirmation](docs/CourseMatchConfirmation.md)
- [CourseMatchConfirmationEnvelope](docs/CourseMatchConfirmationEnvelope.md)
- [CourseMatchConfirmationRequest](docs/CourseMatchConfirmationRequest.md)
- [CourseMatchCreateRequest](docs/CourseMatchCreateRequest.md)
- [CourseMatchEnvelope](docs/CourseMatchEnvelope.md)
- [CourseMatchInvitation](docs/CourseMatchInvitation.md)
- [CourseMatchInvitationResponse](docs/CourseMatchInvitationResponse.md)
- [CourseMatchInvitationResponseEnvelope](docs/CourseMatchInvitationResponseEnvelope.md)
- [CourseMatchInvitationResponseRequest](docs/CourseMatchInvitationResponseRequest.md)
- [CourseMatchInvitationSummary](docs/CourseMatchInvitationSummary.md)
- [CourseMatchInvitationSummaryListEnvelope](docs/CourseMatchInvitationSummaryListEnvelope.md)
- [CourseMatchPatchRequest](docs/CourseMatchPatchRequest.md)
- [CourseMatchPriceSnapshot](docs/CourseMatchPriceSnapshot.md)
- [CourseMatchPriceSnapshotEnvelope](docs/CourseMatchPriceSnapshotEnvelope.md)
- [CourseMatchPriceState](docs/CourseMatchPriceState.md)
- [CourseMatchPricingConfirmationRequest](docs/CourseMatchPricingConfirmationRequest.md)
- [CourseMatchPricingItem](docs/CourseMatchPricingItem.md)
- [CourseMatchPricingPreview](docs/CourseMatchPricingPreview.md)
- [CourseMatchPricingPreviewEnvelope](docs/CourseMatchPricingPreviewEnvelope.md)
- [CourseMatchReadiness](docs/CourseMatchReadiness.md)
- [CourseMatchSession](docs/CourseMatchSession.md)
- [CourseMatchSessionPlanRequest](docs/CourseMatchSessionPlanRequest.md)
- [CourseMatchSummary](docs/CourseMatchSummary.md)
- [CourseMatchSummaryListEnvelope](docs/CourseMatchSummaryListEnvelope.md)
- [CourseOfferingBillingMode](docs/CourseOfferingBillingMode.md)
- [CourseOfferingCancellationRequest](docs/CourseOfferingCancellationRequest.md)
- [CourseOfferingCoachSummary](docs/CourseOfferingCoachSummary.md)
- [CourseOfferingConfirmation](docs/CourseOfferingConfirmation.md)
- [CourseOfferingConfirmationEnvelope](docs/CourseOfferingConfirmationEnvelope.md)
- [CourseOfferingConfirmationRequest](docs/CourseOfferingConfirmationRequest.md)
- [CourseOfferingCreateRequest](docs/CourseOfferingCreateRequest.md)
- [CourseOfferingDetail](docs/CourseOfferingDetail.md)
- [CourseOfferingDetailEnvelope](docs/CourseOfferingDetailEnvelope.md)
- [CourseOfferingPage](docs/CourseOfferingPage.md)
- [CourseOfferingPageEnvelope](docs/CourseOfferingPageEnvelope.md)
- [CourseOfferingPriceSnapshot](docs/CourseOfferingPriceSnapshot.md)
- [CourseOfferingPriceSnapshotEnvelope](docs/CourseOfferingPriceSnapshotEnvelope.md)
- [CourseOfferingPricingConfirmationRequest](docs/CourseOfferingPricingConfirmationRequest.md)
- [CourseOfferingPricingPreview](docs/CourseOfferingPricingPreview.md)
- [CourseOfferingPricingPreviewEnvelope](docs/CourseOfferingPricingPreviewEnvelope.md)
- [CourseOfferingPricingPreviewRequest](docs/CourseOfferingPricingPreviewRequest.md)
- [CourseOfferingRegistration](docs/CourseOfferingRegistration.md)
- [CourseOfferingRegistrationCommand](docs/CourseOfferingRegistrationCommand.md)
- [CourseOfferingRegistrationCommandEnvelope](docs/CourseOfferingRegistrationCommandEnvelope.md)
- [CourseOfferingRegistrationPage](docs/CourseOfferingRegistrationPage.md)
- [CourseOfferingRegistrationPageEnvelope](docs/CourseOfferingRegistrationPageEnvelope.md)
- [CourseOfferingRegistrationStatus](docs/CourseOfferingRegistrationStatus.md)
- [CourseOfferingScheduleType](docs/CourseOfferingScheduleType.md)
- [CourseOfferingSessionPlan](docs/CourseOfferingSessionPlan.md)
- [CourseOfferingSessionPlanRequest](docs/CourseOfferingSessionPlanRequest.md)
- [CourseOfferingStatus](docs/CourseOfferingStatus.md)
- [CourseOfferingSummary](docs/CourseOfferingSummary.md)
- [CourseOfferingUpdateRequest](docs/CourseOfferingUpdateRequest.md)
- [CourseOperationReviewRequest](docs/CourseOperationReviewRequest.md)
- [CoursePage](docs/CoursePage.md)
- [CoursePageEnvelope](docs/CoursePageEnvelope.md)
- [CourseSessionEnvelope](docs/CourseSessionEnvelope.md)
- [CourseSessionListEnvelope](docs/CourseSessionListEnvelope.md)
- [CourseSessionSummary](docs/CourseSessionSummary.md)
- [CourseSummary](docs/CourseSummary.md)
- [DirectSessionRescheduleRequest](docs/DirectSessionRescheduleRequest.md)
- [ErrorBody](docs/ErrorBody.md)
- [ErrorEnvelope](docs/ErrorEnvelope.md)
- [FinancePaymentEnvelope](docs/FinancePaymentEnvelope.md)
- [FinancePaymentMethod](docs/FinancePaymentMethod.md)
- [FinancePaymentRequest](docs/FinancePaymentRequest.md)
- [FinancePaymentResponse](docs/FinancePaymentResponse.md)
- [FinanceRefundExecutionEnvelope](docs/FinanceRefundExecutionEnvelope.md)
- [FinanceRefundExecutionRequest](docs/FinanceRefundExecutionRequest.md)
- [FinanceRefundExecutionResponse](docs/FinanceRefundExecutionResponse.md)
- [FinanceRefundRequest](docs/FinanceRefundRequest.md)
- [FinanceRefundRequestEnvelope](docs/FinanceRefundRequestEnvelope.md)
- [FinanceRefundRequestResponse](docs/FinanceRefundRequestResponse.md)
- [FinanceRefundReviewDecision](docs/FinanceRefundReviewDecision.md)
- [FinanceRefundReviewEnvelope](docs/FinanceRefundReviewEnvelope.md)
- [FinanceRefundReviewRequest](docs/FinanceRefundReviewRequest.md)
- [FinanceRefundReviewResponse](docs/FinanceRefundReviewResponse.md)
- [LessonRequest](docs/LessonRequest.md)
- [LessonRequestCreateRequest](docs/LessonRequestCreateRequest.md)
- [LessonRequestDetail](docs/LessonRequestDetail.md)
- [LessonRequestDetailEnvelope](docs/LessonRequestDetailEnvelope.md)
- [LessonRequestEnvelope](docs/LessonRequestEnvelope.md)
- [LessonRequestListEnvelope](docs/LessonRequestListEnvelope.md)
- [LineLoginRequest](docs/LineLoginRequest.md)
- [LoginData](docs/LoginData.md)
- [LoginResponseEnvelope](docs/LoginResponseEnvelope.md)
- [LoginUser](docs/LoginUser.md)
- [Me](docs/Me.md)
- [MeResponseEnvelope](docs/MeResponseEnvelope.md)
- [Meta](docs/Meta.md)
- [MyCourseOfferingRegistration](docs/MyCourseOfferingRegistration.md)
- [MyCourseOfferingRegistrationPage](docs/MyCourseOfferingRegistrationPage.md)
- [MyCourseOfferingRegistrationPageEnvelope](docs/MyCourseOfferingRegistrationPageEnvelope.md)
- [PayoutBatchCreateEnvelope](docs/PayoutBatchCreateEnvelope.md)
- [PayoutBatchCreateItemRequest](docs/PayoutBatchCreateItemRequest.md)
- [PayoutBatchCreateRequest](docs/PayoutBatchCreateRequest.md)
- [PayoutBatchCreateResponse](docs/PayoutBatchCreateResponse.md)
- [PayoutBatchEnvelope](docs/PayoutBatchEnvelope.md)
- [PayoutBatchItemResponse](docs/PayoutBatchItemResponse.md)
- [PayoutBatchItemStatus](docs/PayoutBatchItemStatus.md)
- [PayoutBatchResponse](docs/PayoutBatchResponse.md)
- [PayoutBatchStatus](docs/PayoutBatchStatus.md)
- [PayoutExecutionEnvelope](docs/PayoutExecutionEnvelope.md)
- [PayoutExecutionRequest](docs/PayoutExecutionRequest.md)
- [PayoutExecutionResponse](docs/PayoutExecutionResponse.md)
- [PayoutMethod](docs/PayoutMethod.md)
- [ProfileUpdateRequest](docs/ProfileUpdateRequest.md)
- [ReviewRequest](docs/ReviewRequest.md)
- [RoleCode](docs/RoleCode.md)
- [RoleContext](docs/RoleContext.md)
- [RolesResponseEnvelope](docs/RolesResponseEnvelope.md)
- [SelectedAvailabilityRequest](docs/SelectedAvailabilityRequest.md)
- [SessionChangeRequest](docs/SessionChangeRequest.md)
- [SessionChangeRequestEnvelope](docs/SessionChangeRequestEnvelope.md)
- [SessionChangeReviewQueueEnvelope](docs/SessionChangeReviewQueueEnvelope.md)
- [SessionChangeReviewQueueItem](docs/SessionChangeReviewQueueItem.md)
- [SessionEnrollmentCancellation](docs/SessionEnrollmentCancellation.md)
- [SessionEnrollmentCancellationEnvelope](docs/SessionEnrollmentCancellationEnvelope.md)
- [SessionEnrollmentCancellationRequest](docs/SessionEnrollmentCancellationRequest.md)
- [SessionPreference](docs/SessionPreference.md)
- [SessionRescheduleRequest](docs/SessionRescheduleRequest.md)
- [SessionRescheduleResult](docs/SessionRescheduleResult.md)
- [SessionRescheduleResultEnvelope](docs/SessionRescheduleResultEnvelope.md)
- [SessionSettlementEnvelope](docs/SessionSettlementEnvelope.md)
- [SessionSettlementResponse](docs/SessionSettlementResponse.md)
- [SettlementAllocationType](docs/SettlementAllocationType.md)
- [SettlementCalculationEnvelope](docs/SettlementCalculationEnvelope.md)
- [SettlementCalculationRequest](docs/SettlementCalculationRequest.md)
- [SettlementCalculationResponse](docs/SettlementCalculationResponse.md)
- [SettlementCoachAllocationRequest](docs/SettlementCoachAllocationRequest.md)
- [SettlementConfirmationEnvelope](docs/SettlementConfirmationEnvelope.md)
- [SettlementConfirmationRequest](docs/SettlementConfirmationRequest.md)
- [SettlementConfirmationResponse](docs/SettlementConfirmationResponse.md)
- [SettlementStatus](docs/SettlementStatus.md)

### Authorization


Authentication schemes defined for the API:
<a id="bearerAuth"></a>
#### bearerAuth


- **Type**: HTTP Bearer Token authentication (JWT)

## About

This TypeScript SDK client supports the [Fetch API](https://fetch.spec.whatwg.org/)
and is automatically generated by the
[OpenAPI Generator](https://openapi-generator.tech) project:

- API version: `v1`
- Package version: `0.0.1`
- Generator version: `7.17.0`
- Build package: `org.openapitools.codegen.languages.TypeScriptFetchClientCodegen`

The generated npm module supports the following:

- Environments
  * Node.js
  * Webpack
  * Browserify
- Language levels
  * ES5 - you must have a Promises/A+ library installed
  * ES6
- Module systems
  * CommonJS
  * ES6 module system


## Development

### Building

To build the TypeScript source code, you need to have Node.js and npm installed.
After cloning the repository, navigate to the project directory and run:

```bash
npm install
npm run build
```

### Publishing

Once you've built the package, you can publish it to npm:

```bash
npm publish
```

## License

[]()
