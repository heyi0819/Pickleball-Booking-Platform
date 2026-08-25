# CourseOfferingsApi

All URIs are relative to */api/v1*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**cancelCourseOffering**](CourseOfferingsApi.md#cancelcourseoffering) | **POST** /course-offerings/{offeringId}/cancellation |  |
| [**closeCourseOffering**](CourseOfferingsApi.md#closecourseoffering) | **POST** /course-offerings/{offeringId}/closure |  |
| [**confirmCourseOffering**](CourseOfferingsApi.md#confirmcourseoffering) | **POST** /course-offerings/{offeringId}/confirmation |  |
| [**confirmCourseOfferingPricing**](CourseOfferingsApi.md#confirmcourseofferingpricing) | **POST** /course-offerings/{offeringId}/pricing-confirmation |  |
| [**createCourseOffering**](CourseOfferingsApi.md#createcourseoffering) | **POST** /course-offerings |  |
| [**getCourseOffering**](CourseOfferingsApi.md#getcourseoffering) | **GET** /course-offerings/{offeringId} |  |
| [**listCourseOfferings**](CourseOfferingsApi.md#listcourseofferings) | **GET** /course-offerings |  |
| [**previewCourseOfferingPricing**](CourseOfferingsApi.md#previewcourseofferingpricing) | **POST** /course-offerings/{offeringId}/pricing-preview |  |
| [**publishCourseOffering**](CourseOfferingsApi.md#publishcourseoffering) | **POST** /course-offerings/{offeringId}/publication |  |
| [**updateCourseOffering**](CourseOfferingsApi.md#updatecourseoffering) | **PATCH** /course-offerings/{offeringId} |  |



## cancelCourseOffering

> CourseOfferingDetailEnvelope cancelCourseOffering(offeringId, idempotencyKey, courseOfferingCancellationRequest)



### Example

```ts
import {
  Configuration,
  CourseOfferingsApi,
} from '@pickleball/api-client-generated';
import type { CancelCourseOfferingRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOfferingsApi(config);

  const body = {
    // string
    offeringId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // CourseOfferingCancellationRequest (optional)
    courseOfferingCancellationRequest: ...,
  } satisfies CancelCourseOfferingRequest;

  try {
    const data = await api.cancelCourseOffering(body);
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
| **courseOfferingCancellationRequest** | [CourseOfferingCancellationRequest](CourseOfferingCancellationRequest.md) |  | [Optional] |

### Return type

[**CourseOfferingDetailEnvelope**](CourseOfferingDetailEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Cancelled offering and active registrations |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## closeCourseOffering

> CourseOfferingDetailEnvelope closeCourseOffering(offeringId, idempotencyKey)



### Example

```ts
import {
  Configuration,
  CourseOfferingsApi,
} from '@pickleball/api-client-generated';
import type { CloseCourseOfferingRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOfferingsApi(config);

  const body = {
    // string
    offeringId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
  } satisfies CloseCourseOfferingRequest;

  try {
    const data = await api.closeCourseOffering(body);
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

[**CourseOfferingDetailEnvelope**](CourseOfferingDetailEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Closed offering |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## confirmCourseOffering

> CourseOfferingConfirmationEnvelope confirmCourseOffering(offeringId, idempotencyKey, courseOfferingConfirmationRequest)



### Example

```ts
import {
  Configuration,
  CourseOfferingsApi,
} from '@pickleball/api-client-generated';
import type { ConfirmCourseOfferingRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOfferingsApi(config);

  const body = {
    // string
    offeringId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // CourseOfferingConfirmationRequest
    courseOfferingConfirmationRequest: ...,
  } satisfies ConfirmCourseOfferingRequest;

  try {
    const data = await api.confirmCourseOffering(body);
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
| **courseOfferingConfirmationRequest** | [CourseOfferingConfirmationRequest](CourseOfferingConfirmationRequest.md) |  | |

### Return type

[**CourseOfferingConfirmationEnvelope**](CourseOfferingConfirmationEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Formal course created atomically from offering |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## confirmCourseOfferingPricing

> CourseOfferingPriceSnapshotEnvelope confirmCourseOfferingPricing(offeringId, idempotencyKey, courseOfferingPricingConfirmationRequest)



### Example

```ts
import {
  Configuration,
  CourseOfferingsApi,
} from '@pickleball/api-client-generated';
import type { ConfirmCourseOfferingPricingRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOfferingsApi(config);

  const body = {
    // string
    offeringId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // CourseOfferingPricingConfirmationRequest
    courseOfferingPricingConfirmationRequest: ...,
  } satisfies ConfirmCourseOfferingPricingRequest;

  try {
    const data = await api.confirmCourseOfferingPricing(body);
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
| **courseOfferingPricingConfirmationRequest** | [CourseOfferingPricingConfirmationRequest](CourseOfferingPricingConfirmationRequest.md) |  | |

### Return type

[**CourseOfferingPriceSnapshotEnvelope**](CourseOfferingPriceSnapshotEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Confirmed immutable offering price snapshot |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## createCourseOffering

> CourseOfferingDetailEnvelope createCourseOffering(courseOfferingCreateRequest)



### Example

```ts
import {
  Configuration,
  CourseOfferingsApi,
} from '@pickleball/api-client-generated';
import type { CreateCourseOfferingRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOfferingsApi(config);

  const body = {
    // CourseOfferingCreateRequest
    courseOfferingCreateRequest: ...,
  } satisfies CreateCourseOfferingRequest;

  try {
    const data = await api.createCourseOffering(body);
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
| **courseOfferingCreateRequest** | [CourseOfferingCreateRequest](CourseOfferingCreateRequest.md) |  | |

### Return type

[**CourseOfferingDetailEnvelope**](CourseOfferingDetailEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Draft course offering |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getCourseOffering

> CourseOfferingDetailEnvelope getCourseOffering(offeringId)



### Example

```ts
import {
  Configuration,
  CourseOfferingsApi,
} from '@pickleball/api-client-generated';
import type { GetCourseOfferingRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOfferingsApi(config);

  const body = {
    // string
    offeringId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetCourseOfferingRequest;

  try {
    const data = await api.getCourseOffering(body);
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

### Return type

[**CourseOfferingDetailEnvelope**](CourseOfferingDetailEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Course offering detail |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listCourseOfferings

> CourseOfferingPageEnvelope listCourseOfferings(organizationId, status, from, to, coachProfileId, skillLevel, page, size, sort)



### Example

```ts
import {
  Configuration,
  CourseOfferingsApi,
} from '@pickleball/api-client-generated';
import type { ListCourseOfferingsRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOfferingsApi(config);

  const body = {
    // string (optional)
    organizationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // CourseOfferingStatus (optional)
    status: ...,
    // Date (optional)
    from: 2013-10-20T19:20:30+01:00,
    // Date (optional)
    to: 2013-10-20T19:20:30+01:00,
    // string (optional)
    coachProfileId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string (optional)
    skillLevel: skillLevel_example,
    // number (optional)
    page: 56,
    // number (optional)
    size: 56,
    // string (optional)
    sort: sort_example,
  } satisfies ListCourseOfferingsRequest;

  try {
    const data = await api.listCourseOfferings(body);
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
| **status** | `CourseOfferingStatus` |  | [Optional] [Defaults to `undefined`] [Enum: DRAFT, OPEN, CLOSED, CONFIRMED, CANCELLED] |
| **from** | `Date` |  | [Optional] [Defaults to `undefined`] |
| **to** | `Date` |  | [Optional] [Defaults to `undefined`] |
| **coachProfileId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **skillLevel** | `string` |  | [Optional] [Defaults to `undefined`] |
| **page** | `number` |  | [Optional] [Defaults to `0`] |
| **size** | `number` |  | [Optional] [Defaults to `20`] |
| **sort** | `string` |  | [Optional] [Defaults to `undefined`] |

### Return type

[**CourseOfferingPageEnvelope**](CourseOfferingPageEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Visible course offerings |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## previewCourseOfferingPricing

> CourseOfferingPricingPreviewEnvelope previewCourseOfferingPricing(offeringId, courseOfferingPricingPreviewRequest)



### Example

```ts
import {
  Configuration,
  CourseOfferingsApi,
} from '@pickleball/api-client-generated';
import type { PreviewCourseOfferingPricingRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOfferingsApi(config);

  const body = {
    // string
    offeringId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // CourseOfferingPricingPreviewRequest
    courseOfferingPricingPreviewRequest: ...,
  } satisfies PreviewCourseOfferingPricingRequest;

  try {
    const data = await api.previewCourseOfferingPricing(body);
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
| **courseOfferingPricingPreviewRequest** | [CourseOfferingPricingPreviewRequest](CourseOfferingPricingPreviewRequest.md) |  | |

### Return type

[**CourseOfferingPricingPreviewEnvelope**](CourseOfferingPricingPreviewEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Manual per-participant pricing preview with deterministic fingerprint |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## publishCourseOffering

> CourseOfferingDetailEnvelope publishCourseOffering(offeringId, idempotencyKey)



### Example

```ts
import {
  Configuration,
  CourseOfferingsApi,
} from '@pickleball/api-client-generated';
import type { PublishCourseOfferingRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOfferingsApi(config);

  const body = {
    // string
    offeringId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
  } satisfies PublishCourseOfferingRequest;

  try {
    const data = await api.publishCourseOffering(body);
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

[**CourseOfferingDetailEnvelope**](CourseOfferingDetailEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Published offering |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## updateCourseOffering

> CourseOfferingDetailEnvelope updateCourseOffering(offeringId, courseOfferingUpdateRequest)



### Example

```ts
import {
  Configuration,
  CourseOfferingsApi,
} from '@pickleball/api-client-generated';
import type { UpdateCourseOfferingRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseOfferingsApi(config);

  const body = {
    // string
    offeringId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // CourseOfferingUpdateRequest
    courseOfferingUpdateRequest: ...,
  } satisfies UpdateCourseOfferingRequest;

  try {
    const data = await api.updateCourseOffering(body);
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
| **courseOfferingUpdateRequest** | [CourseOfferingUpdateRequest](CourseOfferingUpdateRequest.md) |  | |

### Return type

[**CourseOfferingDetailEnvelope**](CourseOfferingDetailEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Revised draft course offering; any confirmed offering price is superseded |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
