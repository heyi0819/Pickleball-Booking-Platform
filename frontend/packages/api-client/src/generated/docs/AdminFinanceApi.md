# AdminFinanceApi

All URIs are relative to */api/v1*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**getAdminPayment**](AdminFinanceApi.md#getadminpayment) | **GET** /admin/payments/{id} | Read organization-scoped payments |
| [**getAdminReceivable**](AdminFinanceApi.md#getadminreceivable) | **GET** /admin/receivables/{id} | Read organization-scoped receivables |
| [**getAdminRefund**](AdminFinanceApi.md#getadminrefund) | **GET** /admin/refunds/{id} | Read organization-scoped refunds |
| [**listAdminPayments**](AdminFinanceApi.md#listadminpayments) | **GET** /admin/payments | List organization-scoped payments |
| [**listAdminReceivables**](AdminFinanceApi.md#listadminreceivables) | **GET** /admin/receivables | List organization-scoped receivables |
| [**listAdminRefunds**](AdminFinanceApi.md#listadminrefunds) | **GET** /admin/refunds | List organization-scoped refunds |



## getAdminPayment

> AdminFinancePaymentEnvelope getAdminPayment(organizationId, id)

Read organization-scoped payments

Read-only operational projection. COMMITTEE requires an active server-side assignment for the explicit organizationId. PLATFORM_ADMIN must also select an explicit organization. Unauthorized scope is 403; unknown organization or out-of-scope detail is 404. Monetary projections are informational; existing commands revalidate eligibility and balances.

### Example

```ts
import {
  Configuration,
  AdminFinanceApi,
} from '@pickleball/api-client-generated';
import type { GetAdminPaymentRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminFinanceApi(config);

  const body = {
    // string
    organizationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetAdminPaymentRequest;

  try {
    const data = await api.getAdminPayment(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |

### Return type

[**AdminFinancePaymentEnvelope**](AdminFinancePaymentEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Finance read projection |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getAdminReceivable

> AdminFinanceReceivableEnvelope getAdminReceivable(organizationId, id)

Read organization-scoped receivables

Read-only operational projection. COMMITTEE requires an active server-side assignment for the explicit organizationId. PLATFORM_ADMIN must also select an explicit organization. Unauthorized scope is 403; unknown organization or out-of-scope detail is 404. Monetary projections are informational; existing commands revalidate eligibility and balances.

### Example

```ts
import {
  Configuration,
  AdminFinanceApi,
} from '@pickleball/api-client-generated';
import type { GetAdminReceivableRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminFinanceApi(config);

  const body = {
    // string
    organizationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetAdminReceivableRequest;

  try {
    const data = await api.getAdminReceivable(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |

### Return type

[**AdminFinanceReceivableEnvelope**](AdminFinanceReceivableEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Finance read projection |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getAdminRefund

> AdminFinanceRefundEnvelope getAdminRefund(organizationId, id)

Read organization-scoped refunds

Read-only operational projection. COMMITTEE requires an active server-side assignment for the explicit organizationId. PLATFORM_ADMIN must also select an explicit organization. Unauthorized scope is 403; unknown organization or out-of-scope detail is 404. Monetary projections are informational; existing commands revalidate eligibility and balances.

### Example

```ts
import {
  Configuration,
  AdminFinanceApi,
} from '@pickleball/api-client-generated';
import type { GetAdminRefundRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminFinanceApi(config);

  const body = {
    // string
    organizationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetAdminRefundRequest;

  try {
    const data = await api.getAdminRefund(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |

### Return type

[**AdminFinanceRefundEnvelope**](AdminFinanceRefundEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Finance read projection |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listAdminPayments

> AdminFinancePaymentPageEnvelope listAdminPayments(organizationId, status, memberId, receivableId, page, size)

List organization-scoped payments

Read-only operational projection. COMMITTEE requires an active server-side assignment for the explicit organizationId. PLATFORM_ADMIN must also select an explicit organization. Unauthorized scope is 403; unknown organization or out-of-scope detail is 404. Monetary projections are informational; existing commands revalidate eligibility and balances. Stable descending paidAt, then id ordering; zero-based bounded pagination.

### Example

```ts
import {
  Configuration,
  AdminFinanceApi,
} from '@pickleball/api-client-generated';
import type { ListAdminPaymentsRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminFinanceApi(config);

  const body = {
    // string
    organizationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // 'COMPLETED' | 'PARTIALLY_REFUNDED' | 'REFUNDED' | 'VOIDED' (optional)
    status: status_example,
    // string (optional)
    memberId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string (optional)
    receivableId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // number (optional)
    page: 56,
    // number (optional)
    size: 56,
  } satisfies ListAdminPaymentsRequest;

  try {
    const data = await api.listAdminPayments(body);
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
| **status** | `COMPLETED`, `PARTIALLY_REFUNDED`, `REFUNDED`, `VOIDED` |  | [Optional] [Defaults to `undefined`] [Enum: COMPLETED, PARTIALLY_REFUNDED, REFUNDED, VOIDED] |
| **memberId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **receivableId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **page** | `number` |  | [Optional] [Defaults to `0`] |
| **size** | `number` |  | [Optional] [Defaults to `20`] |

### Return type

[**AdminFinancePaymentPageEnvelope**](AdminFinancePaymentPageEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Finance read projection |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listAdminReceivables

> AdminFinanceReceivablePageEnvelope listAdminReceivables(organizationId, status, memberId, courseId, page, size)

List organization-scoped receivables

Read-only operational projection. COMMITTEE requires an active server-side assignment for the explicit organizationId. PLATFORM_ADMIN must also select an explicit organization. Unauthorized scope is 403; unknown organization or out-of-scope detail is 404. Monetary projections are informational; existing commands revalidate eligibility and balances. Stable descending createdAt, then id ordering; zero-based bounded pagination.

### Example

```ts
import {
  Configuration,
  AdminFinanceApi,
} from '@pickleball/api-client-generated';
import type { ListAdminReceivablesRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminFinanceApi(config);

  const body = {
    // string
    organizationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // 'OPEN' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE' | 'CANCELLED' | 'REFUNDED' (optional)
    status: status_example,
    // string (optional)
    memberId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string (optional)
    courseId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // number (optional)
    page: 56,
    // number (optional)
    size: 56,
  } satisfies ListAdminReceivablesRequest;

  try {
    const data = await api.listAdminReceivables(body);
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
| **status** | `OPEN`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`, `CANCELLED`, `REFUNDED` |  | [Optional] [Defaults to `undefined`] [Enum: OPEN, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED, REFUNDED] |
| **memberId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **courseId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **page** | `number` |  | [Optional] [Defaults to `0`] |
| **size** | `number` |  | [Optional] [Defaults to `20`] |

### Return type

[**AdminFinanceReceivablePageEnvelope**](AdminFinanceReceivablePageEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Finance read projection |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listAdminRefunds

> AdminFinanceRefundPageEnvelope listAdminRefunds(organizationId, status, memberId, paymentId, page, size)

List organization-scoped refunds

Read-only operational projection. COMMITTEE requires an active server-side assignment for the explicit organizationId. PLATFORM_ADMIN must also select an explicit organization. Unauthorized scope is 403; unknown organization or out-of-scope detail is 404. Monetary projections are informational; existing commands revalidate eligibility and balances. Stable descending requestedAt, then id ordering; zero-based bounded pagination.

### Example

```ts
import {
  Configuration,
  AdminFinanceApi,
} from '@pickleball/api-client-generated';
import type { ListAdminRefundsRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminFinanceApi(config);

  const body = {
    // string
    organizationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'COMPLETED' | 'FAILED' | 'CANCELLED' (optional)
    status: status_example,
    // string (optional)
    memberId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string (optional)
    paymentId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // number (optional)
    page: 56,
    // number (optional)
    size: 56,
  } satisfies ListAdminRefundsRequest;

  try {
    const data = await api.listAdminRefunds(body);
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
| **status** | `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `COMPLETED`, `FAILED`, `CANCELLED` |  | [Optional] [Defaults to `undefined`] [Enum: PENDING_APPROVAL, APPROVED, REJECTED, COMPLETED, FAILED, CANCELLED] |
| **memberId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **paymentId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **page** | `number` |  | [Optional] [Defaults to `0`] |
| **size** | `number` |  | [Optional] [Defaults to `20`] |

### Return type

[**AdminFinanceRefundPageEnvelope**](AdminFinanceRefundPageEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Finance read projection |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
