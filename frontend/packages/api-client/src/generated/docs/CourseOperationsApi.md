# CourseOperationsApi

All URIs are relative to */api/v1*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**cancelSessionEnrollment**](CourseOperationsApi.md#cancelsessionenrollment) | **POST** /session-enrollments/{enrollmentId}/cancellation |  |
| [**createSessionChangeRequest**](CourseOperationsApi.md#createsessionchangerequest) | **POST** /course-sessions/{sessionId}/change-requests |  |
| [**getCourse**](CourseOperationsApi.md#getcourse) | **GET** /courses/{courseId} |  |
| [**getCourseSession**](CourseOperationsApi.md#getcoursesession) | **GET** /course-sessions/{sessionId} |  |
| [**listCourseSessions**](CourseOperationsApi.md#listcoursesessions) | **GET** /courses/{courseId}/sessions |  |
| [**listCourses**](CourseOperationsApi.md#listcourses) | **GET** /courses |  |
| [**requestCoachSessionCancellation**](CourseOperationsApi.md#requestcoachsessioncancellation) | **POST** /course-sessions/{sessionId}/coach-cancellation-requests |  |
| [**rescheduleCourseSession**](CourseOperationsApi.md#reschedulecoursesession) | **POST** /course-sessions/{sessionId}/reschedule |  |
| [**reviewCoachSessionCancellation**](CourseOperationsApi.md#reviewcoachsessioncancellation) | **POST** /coach-cancellation-requests/{requestId}/review |  |
| [**reviewSessionChangeRequest**](CourseOperationsApi.md#reviewsessionchangerequest) | **POST** /session-change-requests/{requestId}/review |  |



## cancelSessionEnrollment

> SessionEnrollmentCancellationEnvelope cancelSessionEnrollment(enrollmentId, sessionEnrollmentCancellationRequest)



### Example

```ts
import {
  Configuration,
  CourseOperationsApi,
} from '@pickleball/api-client-generated';
import type { CancelSessionEnrollmentRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOperationsApi(config);

  const body = {
    // string
    enrollmentId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // SessionEnrollmentCancellationRequest (optional)
    sessionEnrollmentCancellationRequest: ...,
  } satisfies CancelSessionEnrollmentRequest;

  try {
    const data = await api.cancelSessionEnrollment(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **enrollmentId** | `string` |  | [Defaults to `undefined`] |
| **sessionEnrollmentCancellationRequest** | [SessionEnrollmentCancellationRequest](SessionEnrollmentCancellationRequest.md) |  | [Optional] |

### Return type

[**SessionEnrollmentCancellationEnvelope**](SessionEnrollmentCancellationEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Enrollment cancelled while preserving the CourseSession |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## createSessionChangeRequest

> SessionChangeRequestEnvelope createSessionChangeRequest(sessionId, idempotencyKey, sessionRescheduleRequest)



### Example

```ts
import {
  Configuration,
  CourseOperationsApi,
} from '@pickleball/api-client-generated';
import type { CreateSessionChangeRequestRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOperationsApi(config);

  const body = {
    // string
    sessionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // SessionRescheduleRequest
    sessionRescheduleRequest: ...,
  } satisfies CreateSessionChangeRequestRequest;

  try {
    const data = await api.createSessionChangeRequest(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **sessionId** | `string` |  | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **sessionRescheduleRequest** | [SessionRescheduleRequest](SessionRescheduleRequest.md) |  | |

### Return type

[**SessionChangeRequestEnvelope**](SessionChangeRequestEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Pending reschedule request without mutating the Session |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getCourse

> CourseDetailEnvelope getCourse(courseId)



### Example

```ts
import {
  Configuration,
  CourseOperationsApi,
} from '@pickleball/api-client-generated';
import type { GetCourseRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOperationsApi(config);

  const body = {
    // string
    courseId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetCourseRequest;

  try {
    const data = await api.getCourse(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **courseId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**CourseDetailEnvelope**](CourseDetailEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Formal course detail |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getCourseSession

> CourseSessionEnvelope getCourseSession(sessionId)



### Example

```ts
import {
  Configuration,
  CourseOperationsApi,
} from '@pickleball/api-client-generated';
import type { GetCourseSessionRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOperationsApi(config);

  const body = {
    // string
    sessionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetCourseSessionRequest;

  try {
    const data = await api.getCourseSession(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **sessionId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**CourseSessionEnvelope**](CourseSessionEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Formal course session detail |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listCourseSessions

> CourseSessionListEnvelope listCourseSessions(courseId)



### Example

```ts
import {
  Configuration,
  CourseOperationsApi,
} from '@pickleball/api-client-generated';
import type { ListCourseSessionsRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOperationsApi(config);

  const body = {
    // string
    courseId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies ListCourseSessionsRequest;

  try {
    const data = await api.listCourseSessions(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **courseId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**CourseSessionListEnvelope**](CourseSessionListEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Sessions for a visible formal course |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listCourses

> CoursePageEnvelope listCourses(organizationId, status, from, to, coachProfileId, studentUserId, courseType, page, size, sort)



### Example

```ts
import {
  Configuration,
  CourseOperationsApi,
} from '@pickleball/api-client-generated';
import type { ListCoursesRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOperationsApi(config);

  const body = {
    // string (optional)
    organizationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED' (optional)
    status: status_example,
    // Date (optional)
    from: 2013-10-20T19:20:30+01:00,
    // Date (optional)
    to: 2013-10-20T19:20:30+01:00,
    // string (optional)
    coachProfileId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string (optional)
    studentUserId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // 'PRIVATE' | 'GROUP' (optional)
    courseType: courseType_example,
    // number (optional)
    page: 56,
    // number (optional)
    size: 56,
    // 'createdAt,desc' | 'createdAt,asc' | 'courseNo,asc' | 'courseNo,desc' | 'nextSessionAt,asc' | 'nextSessionAt,desc' (optional)
    sort: sort_example,
  } satisfies ListCoursesRequest;

  try {
    const data = await api.listCourses(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **organizationId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **status** | `DRAFT`, `ACTIVE`, `COMPLETED`, `CANCELLED` |  | [Optional] [Defaults to `undefined`] [Enum: DRAFT, ACTIVE, COMPLETED, CANCELLED] |
| **from** | `Date` |  | [Optional] [Defaults to `undefined`] |
| **to** | `Date` |  | [Optional] [Defaults to `undefined`] |
| **coachProfileId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **studentUserId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **courseType** | `PRIVATE`, `GROUP` |  | [Optional] [Defaults to `undefined`] [Enum: PRIVATE, GROUP] |
| **page** | `number` |  | [Optional] [Defaults to `0`] |
| **size** | `number` |  | [Optional] [Defaults to `20`] |
| **sort** | `createdAt,desc`, `createdAt,asc`, `courseNo,asc`, `courseNo,desc`, `nextSessionAt,asc`, `nextSessionAt,desc` |  | [Optional] [Defaults to `undefined`] [Enum: createdAt,desc, createdAt,asc, courseNo,asc, courseNo,desc, nextSessionAt,asc, nextSessionAt,desc] |

### Return type

[**CoursePageEnvelope**](CoursePageEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Role-scoped formal courses |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## requestCoachSessionCancellation

> CoachSessionCancellationEnvelope requestCoachSessionCancellation(sessionId, coachSessionCancellationRequest)



### Example

```ts
import {
  Configuration,
  CourseOperationsApi,
} from '@pickleball/api-client-generated';
import type { RequestCoachSessionCancellationRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOperationsApi(config);

  const body = {
    // string
    sessionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // CoachSessionCancellationRequest
    coachSessionCancellationRequest: ...,
  } satisfies RequestCoachSessionCancellationRequest;

  try {
    const data = await api.requestCoachSessionCancellation(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **sessionId** | `string` |  | [Defaults to `undefined`] |
| **coachSessionCancellationRequest** | [CoachSessionCancellationRequest](CoachSessionCancellationRequest.md) |  | |

### Return type

[**CoachSessionCancellationEnvelope**](CoachSessionCancellationEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Coach cancellation request in PENDING_REVIEW state |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## rescheduleCourseSession

> SessionRescheduleResultEnvelope rescheduleCourseSession(sessionId, idempotencyKey, directSessionRescheduleRequest)



### Example

```ts
import {
  Configuration,
  CourseOperationsApi,
} from '@pickleball/api-client-generated';
import type { RescheduleCourseSessionRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOperationsApi(config);

  const body = {
    // string
    sessionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // DirectSessionRescheduleRequest
    directSessionRescheduleRequest: ...,
  } satisfies RescheduleCourseSessionRequest;

  try {
    const data = await api.rescheduleCourseSession(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **sessionId** | `string` |  | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **directSessionRescheduleRequest** | [DirectSessionRescheduleRequest](DirectSessionRescheduleRequest.md) |  | |

### Return type

[**SessionRescheduleResultEnvelope**](SessionRescheduleResultEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Committee direct reschedule with APPROVED change history |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## reviewCoachSessionCancellation

> CoachSessionCancellationReviewEnvelope reviewCoachSessionCancellation(requestId, courseOperationReviewRequest)



### Example

```ts
import {
  Configuration,
  CourseOperationsApi,
} from '@pickleball/api-client-generated';
import type { ReviewCoachSessionCancellationRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOperationsApi(config);

  const body = {
    // string
    requestId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // CourseOperationReviewRequest
    courseOperationReviewRequest: ...,
  } satisfies ReviewCoachSessionCancellationRequest;

  try {
    const data = await api.reviewCoachSessionCancellation(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **requestId** | `string` |  | [Defaults to `undefined`] |
| **courseOperationReviewRequest** | [CourseOperationReviewRequest](CourseOperationReviewRequest.md) |  | |

### Return type

[**CoachSessionCancellationReviewEnvelope**](CoachSessionCancellationReviewEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Committee cancellation decision |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## reviewSessionChangeRequest

> SessionRescheduleResultEnvelope reviewSessionChangeRequest(requestId, idempotencyKey, courseOperationReviewRequest)



### Example

```ts
import {
  Configuration,
  CourseOperationsApi,
} from '@pickleball/api-client-generated';
import type { ReviewSessionChangeRequestRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOperationsApi(config);

  const body = {
    // string
    requestId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // CourseOperationReviewRequest
    courseOperationReviewRequest: ...,
  } satisfies ReviewSessionChangeRequestRequest;

  try {
    const data = await api.reviewSessionChangeRequest(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **requestId** | `string` |  | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **courseOperationReviewRequest** | [CourseOperationReviewRequest](CourseOperationReviewRequest.md) |  | |

### Return type

[**SessionRescheduleResultEnvelope**](SessionRescheduleResultEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Reschedule decision with current Session state |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
