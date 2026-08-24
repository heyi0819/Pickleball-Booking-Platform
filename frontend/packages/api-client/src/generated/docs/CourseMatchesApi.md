# CourseMatchesApi

All URIs are relative to */api/v1*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**confirmCourseMatch**](CourseMatchesApi.md#confirmcoursematch) | **POST** /course-matches/{courseMatchId}/confirmation |  |
| [**confirmCourseMatchPricing**](CourseMatchesApi.md#confirmcoursematchpricing) | **POST** /course-matches/{courseMatchId}/pricing-confirmation |  |
| [**createCourseMatch**](CourseMatchesApi.md#createcoursematch) | **POST** /course-matches |  |
| [**getCourseMatch**](CourseMatchesApi.md#getcoursematch) | **GET** /course-matches/{courseMatchId} |  |
| [**listCourseMatches**](CourseMatchesApi.md#listcoursematches) | **GET** /course-matches |  |
| [**previewCourseMatchPricing**](CourseMatchesApi.md#previewcoursematchpricing) | **POST** /course-matches/{courseMatchId}/pricing-preview |  |
| [**updateCourseMatch**](CourseMatchesApi.md#updatecoursematch) | **PATCH** /course-matches/{courseMatchId} |  |



## confirmCourseMatch

> CourseMatchConfirmationEnvelope confirmCourseMatch(courseMatchId, idempotencyKey, courseMatchConfirmationRequest)



### Example

```ts
import {
  Configuration,
  CourseMatchesApi,
} from '@pickleball/api-client-generated';
import type { ConfirmCourseMatchRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseMatchesApi(config);

  const body = {
    // string
    courseMatchId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // CourseMatchConfirmationRequest
    courseMatchConfirmationRequest: ...,
  } satisfies ConfirmCourseMatchRequest;

  try {
    const data = await api.confirmCourseMatch(body);
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
| **courseMatchId** | `string` |  | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **courseMatchConfirmationRequest** | [CourseMatchConfirmationRequest](CourseMatchConfirmationRequest.md) |  | |

### Return type

[**CourseMatchConfirmationEnvelope**](CourseMatchConfirmationEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Formal active course created atomically |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## confirmCourseMatchPricing

> CourseMatchPriceSnapshotEnvelope confirmCourseMatchPricing(courseMatchId, idempotencyKey, courseMatchPricingConfirmationRequest)



### Example

```ts
import {
  Configuration,
  CourseMatchesApi,
} from '@pickleball/api-client-generated';
import type { ConfirmCourseMatchPricingRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseMatchesApi(config);

  const body = {
    // string
    courseMatchId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // CourseMatchPricingConfirmationRequest
    courseMatchPricingConfirmationRequest: ...,
  } satisfies ConfirmCourseMatchPricingRequest;

  try {
    const data = await api.confirmCourseMatchPricing(body);
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
| **courseMatchId** | `string` |  | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **courseMatchPricingConfirmationRequest** | [CourseMatchPricingConfirmationRequest](CourseMatchPricingConfirmationRequest.md) |  | |

### Return type

[**CourseMatchPriceSnapshotEnvelope**](CourseMatchPriceSnapshotEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Confirmed immutable match price snapshot |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## createCourseMatch

> CourseMatchEnvelope createCourseMatch(courseMatchCreateRequest)



### Example

```ts
import {
  Configuration,
  CourseMatchesApi,
} from '@pickleball/api-client-generated';
import type { CreateCourseMatchRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseMatchesApi(config);

  const body = {
    // CourseMatchCreateRequest
    courseMatchCreateRequest: ...,
  } satisfies CreateCourseMatchRequest;

  try {
    const data = await api.createCourseMatch(body);
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
| **courseMatchCreateRequest** | [CourseMatchCreateRequest](CourseMatchCreateRequest.md) |  | |

### Return type

[**CourseMatchEnvelope**](CourseMatchEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Draft course match |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getCourseMatch

> CourseMatchEnvelope getCourseMatch(courseMatchId)



### Example

```ts
import {
  Configuration,
  CourseMatchesApi,
} from '@pickleball/api-client-generated';
import type { GetCourseMatchRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseMatchesApi(config);

  const body = {
    // string
    courseMatchId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetCourseMatchRequest;

  try {
    const data = await api.getCourseMatch(body);
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
| **courseMatchId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**CourseMatchEnvelope**](CourseMatchEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Course match detail |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listCourseMatches

> CourseMatchSummaryListEnvelope listCourseMatches(organizationId)



### Example

```ts
import {
  Configuration,
  CourseMatchesApi,
} from '@pickleball/api-client-generated';
import type { ListCourseMatchesRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseMatchesApi(config);

  const body = {
    // string
    organizationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies ListCourseMatchesRequest;

  try {
    const data = await api.listCourseMatches(body);
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
| **organizationId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**CourseMatchSummaryListEnvelope**](CourseMatchSummaryListEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Organization course matches |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## previewCourseMatchPricing

> CourseMatchPricingPreviewEnvelope previewCourseMatchPricing(courseMatchId)



### Example

```ts
import {
  Configuration,
  CourseMatchesApi,
} from '@pickleball/api-client-generated';
import type { PreviewCourseMatchPricingRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseMatchesApi(config);

  const body = {
    // string
    courseMatchId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies PreviewCourseMatchPricingRequest;

  try {
    const data = await api.previewCourseMatchPricing(body);
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
| **courseMatchId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**CourseMatchPricingPreviewEnvelope**](CourseMatchPricingPreviewEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Current pricing preview |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## updateCourseMatch

> CourseMatchEnvelope updateCourseMatch(courseMatchId, courseMatchPatchRequest)



### Example

```ts
import {
  Configuration,
  CourseMatchesApi,
} from '@pickleball/api-client-generated';
import type { UpdateCourseMatchRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseMatchesApi(config);

  const body = {
    // string
    courseMatchId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // CourseMatchPatchRequest
    courseMatchPatchRequest: ...,
  } satisfies UpdateCourseMatchRequest;

  try {
    const data = await api.updateCourseMatch(body);
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
| **courseMatchId** | `string` |  | [Defaults to `undefined`] |
| **courseMatchPatchRequest** | [CourseMatchPatchRequest](CourseMatchPatchRequest.md) |  | |

### Return type

[**CourseMatchEnvelope**](CourseMatchEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Updated draft course match |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
