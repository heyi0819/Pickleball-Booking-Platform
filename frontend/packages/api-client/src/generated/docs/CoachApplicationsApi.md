# CoachApplicationsApi

All URIs are relative to */api/v1*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**createCoachApplication**](CoachApplicationsApi.md#createcoachapplication) | **POST** /coach-applications |  |
| [**listCoachApplicationsForReview**](CoachApplicationsApi.md#listcoachapplicationsforreview) | **GET** /coach-applications |  |
| [**listMyCoachApplications**](CoachApplicationsApi.md#listmycoachapplications) | **GET** /coach-applications/mine |  |
| [**reviewCoachApplication**](CoachApplicationsApi.md#reviewcoachapplication) | **POST** /coach-applications/{id}/review |  |



## createCoachApplication

> CoachApplicationEnvelope createCoachApplication(coachApplicationRequest)



### Example

```ts
import {
  Configuration,
  CoachApplicationsApi,
} from '@pickleball/api-client-generated';
import type { CreateCoachApplicationRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CoachApplicationsApi(config);

  const body = {
    // CoachApplicationRequest
    coachApplicationRequest: ...,
  } satisfies CreateCoachApplicationRequest;

  try {
    const data = await api.createCoachApplication(body);
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
| **coachApplicationRequest** | [CoachApplicationRequest](CoachApplicationRequest.md) |  | |

### Return type

[**CoachApplicationEnvelope**](CoachApplicationEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Coach application |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listCoachApplicationsForReview

> CoachApplicationListEnvelope listCoachApplicationsForReview(organizationId)



### Example

```ts
import {
  Configuration,
  CoachApplicationsApi,
} from '@pickleball/api-client-generated';
import type { ListCoachApplicationsForReviewRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CoachApplicationsApi(config);

  const body = {
    // string
    organizationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies ListCoachApplicationsForReviewRequest;

  try {
    const data = await api.listCoachApplicationsForReview(body);
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

[**CoachApplicationListEnvelope**](CoachApplicationListEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Coach applications |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listMyCoachApplications

> CoachApplicationListEnvelope listMyCoachApplications()



### Example

```ts
import {
  Configuration,
  CoachApplicationsApi,
} from '@pickleball/api-client-generated';
import type { ListMyCoachApplicationsRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CoachApplicationsApi(config);

  try {
    const data = await api.listMyCoachApplications();
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

[**CoachApplicationListEnvelope**](CoachApplicationListEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | My coach applications |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## reviewCoachApplication

> CoachApplicationEnvelope reviewCoachApplication(id, reviewRequest)



### Example

```ts
import {
  Configuration,
  CoachApplicationsApi,
} from '@pickleball/api-client-generated';
import type { ReviewCoachApplicationRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CoachApplicationsApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // ReviewRequest
    reviewRequest: ...,
  } satisfies ReviewCoachApplicationRequest;

  try {
    const data = await api.reviewCoachApplication(body);
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

[**CoachApplicationEnvelope**](CoachApplicationEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Reviewed application |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
