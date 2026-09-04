# AdminRoleDelegationApi

All URIs are relative to */api/v1*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**grantCommitteeMember**](AdminRoleDelegationApi.md#grantcommitteemember) | **POST** /admin/organizations/{organizationId}/committee-members/{userId} | Grant an organization-scoped COMMITTEE role |
| [**listAdminOrganizations**](AdminRoleDelegationApi.md#listadminorganizations) | **GET** /admin/organizations | List active organizations for global administration |
| [**revokeCommitteeMember**](AdminRoleDelegationApi.md#revokecommitteemember) | **DELETE** /admin/organizations/{organizationId}/committee-members/{userId} | Revoke an organization-scoped COMMITTEE role |
| [**searchAdminUsers**](AdminRoleDelegationApi.md#searchadminusers) | **GET** /admin/users | Search active users for committee delegation |



## grantCommitteeMember

> CommitteeMembershipEnvelope grantCommitteeMember(organizationId, userId)

Grant an organization-scoped COMMITTEE role

### Example

```ts
import {
  Configuration,
  AdminRoleDelegationApi,
} from '@pickleball/api-client-generated';
import type { GrantCommitteeMemberRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminRoleDelegationApi(config);

  const body = {
    // string
    organizationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    userId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GrantCommitteeMemberRequest;

  try {
    const data = await api.grantCommitteeMember(body);
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
| **userId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**CommitteeMembershipEnvelope**](CommitteeMembershipEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Committee assignment |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listAdminOrganizations

> AdminOrganizationListEnvelope listAdminOrganizations()

List active organizations for global administration

PLATFORM_ADMIN only. The returned list is used to choose an explicit organization context.

### Example

```ts
import {
  Configuration,
  AdminRoleDelegationApi,
} from '@pickleball/api-client-generated';
import type { ListAdminOrganizationsRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminRoleDelegationApi(config);

  try {
    const data = await api.listAdminOrganizations();
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

[**AdminOrganizationListEnvelope**](AdminOrganizationListEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Active organizations |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## revokeCommitteeMember

> revokeCommitteeMember(organizationId, userId)

Revoke an organization-scoped COMMITTEE role

### Example

```ts
import {
  Configuration,
  AdminRoleDelegationApi,
} from '@pickleball/api-client-generated';
import type { RevokeCommitteeMemberRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminRoleDelegationApi(config);

  const body = {
    // string
    organizationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    userId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies RevokeCommitteeMemberRequest;

  try {
    const data = await api.revokeCommitteeMember(body);
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
| **userId** | `string` |  | [Defaults to `undefined`] |

### Return type

`void` (Empty response body)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **204** | Committee assignment revoked |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State, concurrency, schedule, or idempotency conflict |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## searchAdminUsers

> AdminUserListEnvelope searchAdminUsers(query)

Search active users for committee delegation

PLATFORM_ADMIN only. Search has a two-character minimum and never changes roles.

### Example

```ts
import {
  Configuration,
  AdminRoleDelegationApi,
} from '@pickleball/api-client-generated';
import type { SearchAdminUsersRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminRoleDelegationApi(config);

  const body = {
    // string
    query: query_example,
  } satisfies SearchAdminUsersRequest;

  try {
    const data = await api.searchAdminUsers(body);
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
| **query** | `string` |  | [Defaults to `undefined`] |

### Return type

[**AdminUserListEnvelope**](AdminUserListEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Matching active users |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
