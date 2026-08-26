# FinanceApi

All URIs are relative to */api/v1*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**executeRefund**](FinanceApi.md#executerefund) | **POST** /refunds/{refundId}/execution |  |
| [**recordReceivablePayment**](FinanceApi.md#recordreceivablepayment) | **POST** /receivables/{receivableId}/payments |  |
| [**requestReceivableRefund**](FinanceApi.md#requestreceivablerefund) | **POST** /receivables/{receivableId}/refunds |  |
| [**reviewRefund**](FinanceApi.md#reviewrefund) | **POST** /refunds/{refundId}/review |  |



## executeRefund

> FinanceRefundExecutionEnvelope executeRefund(refundId, idempotencyKey, financeRefundExecutionRequest)



### Example

```ts
import {
  Configuration,
  FinanceApi,
} from '@pickleball/api-client-generated';
import type { ExecuteRefundRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new FinanceApi(config);

  const body = {
    // string
    refundId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // FinanceRefundExecutionRequest
    financeRefundExecutionRequest: ...,
  } satisfies ExecuteRefundRequest;

  try {
    const data = await api.executeRefund(body);
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
| **refundId** | `string` |  | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **financeRefundExecutionRequest** | [FinanceRefundExecutionRequest](FinanceRefundExecutionRequest.md) |  | |

### Return type

[**FinanceRefundExecutionEnvelope**](FinanceRefundExecutionEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Approved refund executed |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## recordReceivablePayment

> FinancePaymentEnvelope recordReceivablePayment(receivableId, idempotencyKey, financePaymentRequest)



### Example

```ts
import {
  Configuration,
  FinanceApi,
} from '@pickleball/api-client-generated';
import type { RecordReceivablePaymentRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new FinanceApi(config);

  const body = {
    // string
    receivableId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // FinancePaymentRequest
    financePaymentRequest: ...,
  } satisfies RecordReceivablePaymentRequest;

  try {
    const data = await api.recordReceivablePayment(body);
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
| **receivableId** | `string` |  | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **financePaymentRequest** | [FinancePaymentRequest](FinancePaymentRequest.md) |  | |

### Return type

[**FinancePaymentEnvelope**](FinancePaymentEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Payment recorded |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## requestReceivableRefund

> FinanceRefundRequestEnvelope requestReceivableRefund(receivableId, idempotencyKey, financeRefundRequest)



Canonical Slice 6 refund request: one Refund references one Payment.

### Example

```ts
import {
  Configuration,
  FinanceApi,
} from '@pickleball/api-client-generated';
import type { RequestReceivableRefundRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new FinanceApi(config);

  const body = {
    // string
    receivableId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // FinanceRefundRequest
    financeRefundRequest: ...,
  } satisfies RequestReceivableRefundRequest;

  try {
    const data = await api.requestReceivableRefund(body);
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
| **receivableId** | `string` |  | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **financeRefundRequest** | [FinanceRefundRequest](FinanceRefundRequest.md) |  | |

### Return type

[**FinanceRefundRequestEnvelope**](FinanceRefundRequestEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Refund request pending approval |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## reviewRefund

> FinanceRefundReviewEnvelope reviewRefund(refundId, idempotencyKey, financeRefundReviewRequest)



### Example

```ts
import {
  Configuration,
  FinanceApi,
} from '@pickleball/api-client-generated';
import type { ReviewRefundRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new FinanceApi(config);

  const body = {
    // string
    refundId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // FinanceRefundReviewRequest
    financeRefundReviewRequest: ...,
  } satisfies ReviewRefundRequest;

  try {
    const data = await api.reviewRefund(body);
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
| **refundId** | `string` |  | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **financeRefundReviewRequest** | [FinanceRefundReviewRequest](FinanceRefundReviewRequest.md) |  | |

### Return type

[**FinanceRefundReviewEnvelope**](FinanceRefundReviewEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Refund approval decision |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
