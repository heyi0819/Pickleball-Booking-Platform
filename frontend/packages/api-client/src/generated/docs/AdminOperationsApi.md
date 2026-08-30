# AdminOperationsApi

All URIs are relative to */api/v1*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**listAdminNotifications**](AdminOperationsApi.md#listadminnotifications) | **GET** /admin/notifications |  |
| [**listAdminOutboxEvents**](AdminOperationsApi.md#listadminoutboxevents) | **GET** /admin/outbox-events |  |
| [**retryAdminNotification**](AdminOperationsApi.md#retryadminnotification) | **POST** /admin/notifications/{notificationId}/retry |  |
| [**retryAdminOutboxEvent**](AdminOperationsApi.md#retryadminoutboxevent) | **POST** /admin/outbox-events/{eventId}/retry |  |



## listAdminNotifications

> AdminNotificationPageEnvelope listAdminNotifications(organizationId, status, retryDue, page, size)



### Example

```ts
import {
  Configuration,
  AdminOperationsApi,
} from '@pickleball/api-client-generated';
import type { ListAdminNotificationsRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminOperationsApi(config);

  const body = {
    // string
    organizationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // NotificationStatus (optional)
    status: ...,
    // boolean (optional)
    retryDue: true,
    // number (optional)
    page: 56,
    // number (optional)
    size: 56,
  } satisfies ListAdminNotificationsRequest;

  try {
    const data = await api.listAdminNotifications(body);
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
| **status** | `NotificationStatus` |  | [Optional] [Defaults to `undefined`] [Enum: PENDING, SENDING, SENT, FAILED, DEAD] |
| **retryDue** | `boolean` |  | [Optional] [Defaults to `false`] |
| **page** | `number` |  | [Optional] [Defaults to `0`] |
| **size** | `number` |  | [Optional] [Defaults to `50`] |

### Return type

[**AdminNotificationPageEnvelope**](AdminNotificationPageEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Organization-scoped notification operations page |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listAdminOutboxEvents

> AdminOutboxEventPageEnvelope listAdminOutboxEvents(organizationId, status, retryDue, page, size)



### Example

```ts
import {
  Configuration,
  AdminOperationsApi,
} from '@pickleball/api-client-generated';
import type { ListAdminOutboxEventsRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminOperationsApi(config);

  const body = {
    // string
    organizationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // OutboxEventStatus (optional)
    status: ...,
    // boolean (optional)
    retryDue: true,
    // number (optional)
    page: 56,
    // number (optional)
    size: 56,
  } satisfies ListAdminOutboxEventsRequest;

  try {
    const data = await api.listAdminOutboxEvents(body);
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
| **status** | `OutboxEventStatus` |  | [Optional] [Defaults to `undefined`] [Enum: PENDING, PROCESSING, PROCESSED, FAILED, DEAD] |
| **retryDue** | `boolean` |  | [Optional] [Defaults to `false`] |
| **page** | `number` |  | [Optional] [Defaults to `0`] |
| **size** | `number` |  | [Optional] [Defaults to `50`] |

### Return type

[**AdminOutboxEventPageEnvelope**](AdminOutboxEventPageEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Organization-scoped outbox operations page |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## retryAdminNotification

> AdminNotificationEnvelope retryAdminNotification(notificationId, idempotencyKey, adminRecoveryRequest)



### Example

```ts
import {
  Configuration,
  AdminOperationsApi,
} from '@pickleball/api-client-generated';
import type { RetryAdminNotificationRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminOperationsApi(config);

  const body = {
    // string
    notificationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // AdminRecoveryRequest
    adminRecoveryRequest: ...,
  } satisfies RetryAdminNotificationRequest;

  try {
    const data = await api.retryAdminNotification(body);
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
| **notificationId** | `string` |  | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **adminRecoveryRequest** | [AdminRecoveryRequest](AdminRecoveryRequest.md) |  | |

### Return type

[**AdminNotificationEnvelope**](AdminNotificationEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | FAILED notification retried or DEAD notification requeued |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## retryAdminOutboxEvent

> AdminOutboxEventEnvelope retryAdminOutboxEvent(eventId, idempotencyKey, adminRecoveryRequest)



### Example

```ts
import {
  Configuration,
  AdminOperationsApi,
} from '@pickleball/api-client-generated';
import type { RetryAdminOutboxEventRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminOperationsApi(config);

  const body = {
    // string
    eventId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    idempotencyKey: idempotencyKey_example,
    // AdminRecoveryRequest
    adminRecoveryRequest: ...,
  } satisfies RetryAdminOutboxEventRequest;

  try {
    const data = await api.retryAdminOutboxEvent(body);
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
| **eventId** | `string` |  | [Defaults to `undefined`] |
| **idempotencyKey** | `string` |  | [Defaults to `undefined`] |
| **adminRecoveryRequest** | [AdminRecoveryRequest](AdminRecoveryRequest.md) |  | |

### Return type

[**AdminOutboxEventEnvelope**](AdminOutboxEventEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | FAILED event retried or DEAD event requeued |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
