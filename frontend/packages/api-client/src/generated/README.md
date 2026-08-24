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
*CurrentUserApi* | [**getCurrentUser**](docs/CurrentUserApi.md#getcurrentuser) | **GET** /me |
*CurrentUserApi* | [**getCurrentUserRoles**](docs/CurrentUserApi.md#getcurrentuserroles) | **GET** /me/roles |
*CurrentUserApi* | [**updateCurrentUserProfile**](docs/CurrentUserApi.md#updatecurrentuserprofile) | **PATCH** /me/profile |
*LessonRequestsApi* | [**createLessonRequest**](docs/LessonRequestsApi.md#createlessonrequest) | **POST** /lesson-requests |
*LessonRequestsApi* | [**getLessonRequest**](docs/LessonRequestsApi.md#getlessonrequest) | **GET** /lesson-requests/{id} |
*LessonRequestsApi* | [**listLessonRequestsForReview**](docs/LessonRequestsApi.md#listlessonrequestsforreview) | **GET** /lesson-requests |
*LessonRequestsApi* | [**listMyLessonRequests**](docs/LessonRequestsApi.md#listmylessonrequests) | **GET** /lesson-requests/mine |
*LessonRequestsApi* | [**reviewLessonRequest**](docs/LessonRequestsApi.md#reviewlessonrequest) | **POST** /lesson-requests/{id}/review |
*LessonRequestsApi* | [**submitLessonRequest**](docs/LessonRequestsApi.md#submitlessonrequest) | **POST** /lesson-requests/{id}/submission |
*LessonRequestsApi* | [**updateLessonRequestSelectedAvailability**](docs/LessonRequestsApi.md#updatelessonrequestselectedavailability) | **PATCH** /lesson-requests/{id}/selected-availability |


### Models

- [AvailabilityProposal](docs/AvailabilityProposal.md)
- [AvailabilityProposalEnvelope](docs/AvailabilityProposalEnvelope.md)
- [AvailabilityProposalListEnvelope](docs/AvailabilityProposalListEnvelope.md)
- [AvailabilityProposalRequest](docs/AvailabilityProposalRequest.md)
- [CoachApplication](docs/CoachApplication.md)
- [CoachApplicationEnvelope](docs/CoachApplicationEnvelope.md)
- [CoachApplicationListEnvelope](docs/CoachApplicationListEnvelope.md)
- [CoachApplicationRequest](docs/CoachApplicationRequest.md)
- [ErrorBody](docs/ErrorBody.md)
- [ErrorEnvelope](docs/ErrorEnvelope.md)
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
- [ProfileUpdateRequest](docs/ProfileUpdateRequest.md)
- [ReviewRequest](docs/ReviewRequest.md)
- [RoleCode](docs/RoleCode.md)
- [RoleContext](docs/RoleContext.md)
- [RolesResponseEnvelope](docs/RolesResponseEnvelope.md)
- [SelectedAvailabilityRequest](docs/SelectedAvailabilityRequest.md)
- [SessionPreference](docs/SessionPreference.md)

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
