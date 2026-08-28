# PayoutsApi

All URIs are relative to */api/v1*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**createPayoutBatch**](PayoutsApi.md#createpayoutbatch) | **POST** /payout-batches |  |
| [**executePayoutBatch**](PayoutsApi.md#executepayoutbatch) | **POST** /payout-batches/{batchId}/execution |  |
| [**getPayoutBatch**](PayoutsApi.md#getpayoutbatch) | **GET** /payout-batches/{batchId} |  |



## createPayoutBatch

> PayoutBatchCreateEnvelope createPayoutBatch(payoutBatchCreateRequest)



### Example

```ts
import {
  Configuration,
  PayoutsApi,
} from '@pickleball/api-client-generated';
import type { CreatePayoutBatchRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new PayoutsApi(config);

  const body = {
    // PayoutBatchCreateRequest
    payoutBatchCreateRequest: ...,
  } satisfies CreatePayoutBatchRequest;

  try {
    const data = await api.createPayoutBatch(body);
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
| **payoutBatchCreateRequest** | [PayoutBatchCreateRequest](PayoutBatchCreateRequest.md) |  | |

### Return type

[**PayoutBatchCreateEnvelope**](PayoutBatchCreateEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Draft manual payout batch |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## executePayoutBatch

> PayoutExecutionEnvelope executePayoutBatch(batchId, idempotencyKey, payoutExecutionRequest)



### Example

```ts
import {
  Configuration,
  PayoutsApi,
} from '@pickleball/api-client-generated';
import type { ExecutePayoutBatchRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new PayoutsApi(config);

  const body = {
    // string
    batchId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // PayoutExecutionRequest
    payoutExecutionRequest: ...,
  } satisfies ExecutePayoutBatchRequest;

  try {
    const data = await api.executePayoutBatch(body);
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
| **batchId** | `string` |  | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **payoutExecutionRequest** | [PayoutExecutionRequest](PayoutExecutionRequest.md) |  | |

### Return type

[**PayoutExecutionEnvelope**](PayoutExecutionEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Manual payout batch executed idempotently |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getPayoutBatch

> PayoutBatchEnvelope getPayoutBatch(batchId)



### Example

```ts
import {
  Configuration,
  PayoutsApi,
} from '@pickleball/api-client-generated';
import type { GetPayoutBatchRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new PayoutsApi(config);

  const body = {
    // string
    batchId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetPayoutBatchRequest;

  try {
    const data = await api.getPayoutBatch(body);
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
| **batchId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**PayoutBatchEnvelope**](PayoutBatchEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Payout batch detail |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
