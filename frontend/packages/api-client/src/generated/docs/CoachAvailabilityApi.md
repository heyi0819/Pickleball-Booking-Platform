# CoachAvailabilityApi

All URIs are relative to */api/v1*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**closeAvailabilityProposal**](CoachAvailabilityApi.md#closeavailabilityproposal) | **POST** /coach-availability-proposals/{id}/close |  |
| [**createAvailabilityProposal**](CoachAvailabilityApi.md#createavailabilityproposal) | **POST** /coach-availability-proposals |  |
| [**listApprovedAvailability**](CoachAvailabilityApi.md#listapprovedavailability) | **GET** /coach-availability-proposals/available |  |
| [**listAvailabilityForReview**](CoachAvailabilityApi.md#listavailabilityforreview) | **GET** /coach-availability-proposals |  |
| [**listMyAvailabilityProposals**](CoachAvailabilityApi.md#listmyavailabilityproposals) | **GET** /coach-availability-proposals/mine |  |
| [**reviewAvailabilityProposal**](CoachAvailabilityApi.md#reviewavailabilityproposal) | **POST** /coach-availability-proposals/{id}/review |  |
| [**submitAvailabilityProposal**](CoachAvailabilityApi.md#submitavailabilityproposal) | **POST** /coach-availability-proposals/{id}/submission |  |



## closeAvailabilityProposal

> AvailabilityProposalEnvelope closeAvailabilityProposal(id)



### Example

```ts
import {
  Configuration,
  CoachAvailabilityApi,
} from '@pickleball/api-client-generated';
import type { CloseAvailabilityProposalRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CoachAvailabilityApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies CloseAvailabilityProposalRequest;

  try {
    const data = await api.closeAvailabilityProposal(body);
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

[**AvailabilityProposalEnvelope**](AvailabilityProposalEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Closed availability |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## createAvailabilityProposal

> AvailabilityProposalEnvelope createAvailabilityProposal(availabilityProposalRequest)



### Example

```ts
import {
  Configuration,
  CoachAvailabilityApi,
} from '@pickleball/api-client-generated';
import type { CreateAvailabilityProposalRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CoachAvailabilityApi(config);

  const body = {
    // AvailabilityProposalRequest
    availabilityProposalRequest: ...,
  } satisfies CreateAvailabilityProposalRequest;

  try {
    const data = await api.createAvailabilityProposal(body);
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
| **availabilityProposalRequest** | [AvailabilityProposalRequest](AvailabilityProposalRequest.md) |  | |

### Return type

[**AvailabilityProposalEnvelope**](AvailabilityProposalEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Availability proposal |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listApprovedAvailability

> AvailabilityProposalListEnvelope listApprovedAvailability()



### Example

```ts
import {
  Configuration,
  CoachAvailabilityApi,
} from '@pickleball/api-client-generated';
import type { ListApprovedAvailabilityRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CoachAvailabilityApi(config);

  try {
    const data = await api.listApprovedAvailability();
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

[**AvailabilityProposalListEnvelope**](AvailabilityProposalListEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Approved future availability for current student organization |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listAvailabilityForReview

> AvailabilityProposalListEnvelope listAvailabilityForReview(organizationId)



### Example

```ts
import {
  Configuration,
  CoachAvailabilityApi,
} from '@pickleball/api-client-generated';
import type { ListAvailabilityForReviewRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CoachAvailabilityApi(config);

  const body = {
    // string
    organizationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies ListAvailabilityForReviewRequest;

  try {
    const data = await api.listAvailabilityForReview(body);
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

[**AvailabilityProposalListEnvelope**](AvailabilityProposalListEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Availability proposals |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listMyAvailabilityProposals

> AvailabilityProposalListEnvelope listMyAvailabilityProposals()



### Example

```ts
import {
  Configuration,
  CoachAvailabilityApi,
} from '@pickleball/api-client-generated';
import type { ListMyAvailabilityProposalsRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CoachAvailabilityApi(config);

  try {
    const data = await api.listMyAvailabilityProposals();
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

[**AvailabilityProposalListEnvelope**](AvailabilityProposalListEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | My availability proposals |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## reviewAvailabilityProposal

> AvailabilityProposalEnvelope reviewAvailabilityProposal(id, reviewRequest)



### Example

```ts
import {
  Configuration,
  CoachAvailabilityApi,
} from '@pickleball/api-client-generated';
import type { ReviewAvailabilityProposalRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CoachAvailabilityApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // ReviewRequest
    reviewRequest: ...,
  } satisfies ReviewAvailabilityProposalRequest;

  try {
    const data = await api.reviewAvailabilityProposal(body);
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

[**AvailabilityProposalEnvelope**](AvailabilityProposalEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Reviewed availability |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## submitAvailabilityProposal

> AvailabilityProposalEnvelope submitAvailabilityProposal(id)



### Example

```ts
import {
  Configuration,
  CoachAvailabilityApi,
} from '@pickleball/api-client-generated';
import type { SubmitAvailabilityProposalRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CoachAvailabilityApi(config);

  const body = {
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies SubmitAvailabilityProposalRequest;

  try {
    const data = await api.submitAvailabilityProposal(body);
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

[**AvailabilityProposalEnvelope**](AvailabilityProposalEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Submitted availability |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
