# SettlementApi

All URIs are relative to */api/v1*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**calculateCourseSessionSettlement**](SettlementApi.md#calculatecoursesessionsettlement) | **POST** /course-sessions/{sessionId}/settlement-calculation |  |
| [**confirmSessionSettlement**](SettlementApi.md#confirmsessionsettlement) | **POST** /session-settlements/{settlementId}/confirmation |  |
| [**getCourseSessionSettlement**](SettlementApi.md#getcoursesessionsettlement) | **GET** /course-sessions/{sessionId}/settlement |  |
| [**listMyCoachSettlements**](SettlementApi.md#listmycoachsettlements) | **GET** /me/coach-settlements |  |



## calculateCourseSessionSettlement

> SettlementCalculationEnvelope calculateCourseSessionSettlement(sessionId, settlementCalculationRequest)



### Example

```ts
import {
  Configuration,
  SettlementApi,
} from '@pickleball/api-client-generated';
import type { CalculateCourseSessionSettlementRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new SettlementApi(config);

  const body = {
    // string
    sessionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // SettlementCalculationRequest
    settlementCalculationRequest: ...,
  } satisfies CalculateCourseSessionSettlementRequest;

  try {
    const data = await api.calculateCourseSessionSettlement(body);
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
| **settlementCalculationRequest** | [SettlementCalculationRequest](SettlementCalculationRequest.md) |  | |

### Return type

[**SettlementCalculationEnvelope**](SettlementCalculationEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Settlement calculated from immutable session price and finance facts |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## confirmSessionSettlement

> SettlementConfirmationEnvelope confirmSessionSettlement(settlementId, idempotencyKey, settlementConfirmationRequest)



### Example

```ts
import {
  Configuration,
  SettlementApi,
} from '@pickleball/api-client-generated';
import type { ConfirmSessionSettlementRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new SettlementApi(config);

  const body = {
    // string
    settlementId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // SettlementConfirmationRequest
    settlementConfirmationRequest: ...,
  } satisfies ConfirmSessionSettlementRequest;

  try {
    const data = await api.confirmSessionSettlement(body);
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
| **settlementId** | `string` |  | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **settlementConfirmationRequest** | [SettlementConfirmationRequest](SettlementConfirmationRequest.md) |  | |

### Return type

[**SettlementConfirmationEnvelope**](SettlementConfirmationEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Settlement confirmed idempotently |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getCourseSessionSettlement

> SessionSettlementEnvelope getCourseSessionSettlement(sessionId)



### Example

```ts
import {
  Configuration,
  SettlementApi,
} from '@pickleball/api-client-generated';
import type { GetCourseSessionSettlementRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new SettlementApi(config);

  const body = {
    // string
    sessionId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetCourseSessionSettlementRequest;

  try {
    const data = await api.getCourseSessionSettlement(body);
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

[**SessionSettlementEnvelope**](SessionSettlementEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Session settlement detail |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listMyCoachSettlements

> CoachSettlementSelfListEnvelope listMyCoachSettlements()



### Example

```ts
import {
  Configuration,
  SettlementApi,
} from '@pickleball/api-client-generated';
import type { ListMyCoachSettlementsRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new SettlementApi(config);

  try {
    const data = await api.listMyCoachSettlements();
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

[**CoachSettlementSelfListEnvelope**](CoachSettlementSelfListEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Current coach payout-facing settlement read model |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
