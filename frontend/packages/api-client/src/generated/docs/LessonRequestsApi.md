# LessonRequestsApi

All URIs are relative to */api/v1*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**createLessonRequest**](LessonRequestsApi.md#createlessonrequest) | **POST** /lesson-requests |  |
| [**getLessonRequest**](LessonRequestsApi.md#getlessonrequest) | **GET** /lesson-requests/{id} |  |
| [**listLessonRequestsForReview**](LessonRequestsApi.md#listlessonrequestsforreview) | **GET** /lesson-requests |  |
| [**listMyLessonRequests**](LessonRequestsApi.md#listmylessonrequests) | **GET** /lesson-requests/mine |  |
| [**reviewLessonRequest**](LessonRequestsApi.md#reviewlessonrequest) | **POST** /lesson-requests/{id}/review |  |
| [**submitLessonRequest**](LessonRequestsApi.md#submitlessonrequest) | **POST** /lesson-requests/{id}/submission |  |
| [**updateLessonRequestSelectedAvailability**](LessonRequestsApi.md#updatelessonrequestselectedavailability) | **PATCH** /lesson-requests/{id}/selected-availability |  |



## createLessonRequest

> LessonRequestEnvelope createLessonRequest(lessonRequestCreateRequest)



### Example

```ts
import {
  Configuration,
  LessonRequestsApi,
} from '@pickleball/api-client-generated';
import type { CreateLessonRequestRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new LessonRequestsApi(config);

  const body = {
    // LessonRequestCreateRequest
    lessonRequestCreateRequest: ...,
  } satisfies CreateLessonRequestRequest;

  try {
    const data = await api.createLessonRequest(body);
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
| **lessonRequestCreateRequest** | [LessonRequestCreateRequest](LessonRequestCreateRequest.md) |  | |

### Return type

[**LessonRequestEnvelope**](LessonRequestEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Lesson request |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getLessonRequest

> LessonRequestDetailEnvelope getLessonRequest(id)



### Example

```ts
import {
  Configuration,
  LessonRequestsApi,
} from '@pickleball/api-client-generated';
import type { GetLessonRequestRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new LessonRequestsApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetLessonRequestRequest;

  try {
    const data = await api.getLessonRequest(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |

### Return type

[**LessonRequestDetailEnvelope**](LessonRequestDetailEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Lesson request detail |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listLessonRequestsForReview

> LessonRequestListEnvelope listLessonRequestsForReview(organizationId)



### Example

```ts
import {
  Configuration,
  LessonRequestsApi,
} from '@pickleball/api-client-generated';
import type { ListLessonRequestsForReviewRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new LessonRequestsApi(config);

  const body = {
    // string
    organizationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies ListLessonRequestsForReviewRequest;

  try {
    const data = await api.listLessonRequestsForReview(body);
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

[**LessonRequestListEnvelope**](LessonRequestListEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Lesson requests |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listMyLessonRequests

> LessonRequestListEnvelope listMyLessonRequests()



### Example

```ts
import {
  Configuration,
  LessonRequestsApi,
} from '@pickleball/api-client-generated';
import type { ListMyLessonRequestsRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new LessonRequestsApi(config);

  try {
    const data = await api.listMyLessonRequests();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

[**LessonRequestListEnvelope**](LessonRequestListEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | My lesson requests |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## reviewLessonRequest

> LessonRequestEnvelope reviewLessonRequest(id, reviewRequest)



### Example

```ts
import {
  Configuration,
  LessonRequestsApi,
} from '@pickleball/api-client-generated';
import type { ReviewLessonRequestRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new LessonRequestsApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // ReviewRequest
    reviewRequest: ...,
  } satisfies ReviewLessonRequestRequest;

  try {
    const data = await api.reviewLessonRequest(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |
| **reviewRequest** | [ReviewRequest](ReviewRequest.md) |  | |

### Return type

[**LessonRequestEnvelope**](LessonRequestEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Reviewed lesson request |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## submitLessonRequest

> LessonRequestEnvelope submitLessonRequest(id, idempotencyKey)



### Example

```ts
import {
  Configuration,
  LessonRequestsApi,
} from '@pickleball/api-client-generated';
import type { SubmitLessonRequestRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new LessonRequestsApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
  } satisfies SubmitLessonRequestRequest;

  try {
    const data = await api.submitLessonRequest(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |

### Return type

[**LessonRequestEnvelope**](LessonRequestEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Submitted lesson request |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## updateLessonRequestSelectedAvailability

> LessonRequestEnvelope updateLessonRequestSelectedAvailability(id, selectedAvailabilityRequest)



### Example

```ts
import {
  Configuration,
  LessonRequestsApi,
} from '@pickleball/api-client-generated';
import type { UpdateLessonRequestSelectedAvailabilityRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new LessonRequestsApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // SelectedAvailabilityRequest
    selectedAvailabilityRequest: ...,
  } satisfies UpdateLessonRequestSelectedAvailabilityRequest;

  try {
    const data = await api.updateLessonRequestSelectedAvailability(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |
| **selectedAvailabilityRequest** | [SelectedAvailabilityRequest](SelectedAvailabilityRequest.md) |  | |

### Return type

[**LessonRequestEnvelope**](LessonRequestEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Updated draft availability selection |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
