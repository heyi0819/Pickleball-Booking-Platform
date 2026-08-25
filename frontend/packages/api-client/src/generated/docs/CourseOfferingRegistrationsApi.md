# CourseOfferingRegistrationsApi

All URIs are relative to */api/v1*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**cancelCourseOfferingRegistration**](CourseOfferingRegistrationsApi.md#cancelcourseofferingregistration) | **POST** /course-offering-registrations/{registrationId}/cancellation |  |
| [**listCourseOfferingRegistrations**](CourseOfferingRegistrationsApi.md#listcourseofferingregistrations) | **GET** /course-offerings/{offeringId}/registrations |  |
| [**listMyCourseOfferingRegistrations**](CourseOfferingRegistrationsApi.md#listmycourseofferingregistrations) | **GET** /me/course-offering-registrations |  |
| [**registerCourseOffering**](CourseOfferingRegistrationsApi.md#registercourseoffering) | **POST** /course-offerings/{offeringId}/registrations |  |



## cancelCourseOfferingRegistration

> CourseOfferingRegistrationCommandEnvelope cancelCourseOfferingRegistration(registrationId, idempotencyKey, courseOfferingCancellationRequest)



### Example

```ts
import {
  Configuration,
  CourseOfferingRegistrationsApi,
} from '@pickleball/api-client-generated';
import type { CancelCourseOfferingRegistrationRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOfferingRegistrationsApi(config);

  const body = {
    // string
    registrationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // CourseOfferingCancellationRequest (optional)
    courseOfferingCancellationRequest: ...,
  } satisfies CancelCourseOfferingRegistrationRequest;

  try {
    const data = await api.cancelCourseOfferingRegistration(body);
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
| **registrationId** | `string` |  | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **courseOfferingCancellationRequest** | [CourseOfferingCancellationRequest](CourseOfferingCancellationRequest.md) |  | [Optional] |

### Return type

[**CourseOfferingRegistrationCommandEnvelope**](CourseOfferingRegistrationCommandEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Cancelled own offering registration |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listCourseOfferingRegistrations

> CourseOfferingRegistrationPageEnvelope listCourseOfferingRegistrations(offeringId, status, page, size)



### Example

```ts
import {
  Configuration,
  CourseOfferingRegistrationsApi,
} from '@pickleball/api-client-generated';
import type { ListCourseOfferingRegistrationsRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOfferingRegistrationsApi(config);

  const body = {
    // string
    offeringId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // CourseOfferingRegistrationStatus (optional)
    status: ...,
    // number (optional)
    page: 56,
    // number (optional)
    size: 56,
  } satisfies ListCourseOfferingRegistrationsRequest;

  try {
    const data = await api.listCourseOfferingRegistrations(body);
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
| **offeringId** | `string` |  | [Defaults to `undefined`] |
| **status** | `CourseOfferingRegistrationStatus` |  | [Optional] [Defaults to `undefined`] [Enum: ACTIVE, CANCELLED, CONVERTED] |
| **page** | `number` |  | [Optional] [Defaults to `0`] |
| **size** | `number` |  | [Optional] [Defaults to `20`] |

### Return type

[**CourseOfferingRegistrationPageEnvelope**](CourseOfferingRegistrationPageEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Offering registrations for committee |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listMyCourseOfferingRegistrations

> MyCourseOfferingRegistrationPageEnvelope listMyCourseOfferingRegistrations(page, size)



### Example

```ts
import {
  Configuration,
  CourseOfferingRegistrationsApi,
} from '@pickleball/api-client-generated';
import type { ListMyCourseOfferingRegistrationsRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOfferingRegistrationsApi(config);

  const body = {
    // number (optional)
    page: 56,
    // number (optional)
    size: 56,
  } satisfies ListMyCourseOfferingRegistrationsRequest;

  try {
    const data = await api.listMyCourseOfferingRegistrations(body);
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
| **page** | `number` |  | [Optional] [Defaults to `0`] |
| **size** | `number` |  | [Optional] [Defaults to `20`] |

### Return type

[**MyCourseOfferingRegistrationPageEnvelope**](MyCourseOfferingRegistrationPageEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Current student\&#39;s offering registration history |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## registerCourseOffering

> CourseOfferingRegistrationCommandEnvelope registerCourseOffering(offeringId, idempotencyKey)



### Example

```ts
import {
  Configuration,
  CourseOfferingRegistrationsApi,
} from '@pickleball/api-client-generated';
import type { RegisterCourseOfferingRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOfferingRegistrationsApi(config);

  const body = {
    // string
    offeringId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
  } satisfies RegisterCourseOfferingRequest;

  try {
    const data = await api.registerCourseOffering(body);
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
| **offeringId** | `string` |  | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |

### Return type

[**CourseOfferingRegistrationCommandEnvelope**](CourseOfferingRegistrationCommandEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Active offering registration |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
