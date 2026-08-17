# CurrentUserApi

All URIs are relative to */api/v1*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**getCurrentUser**](CurrentUserApi.md#getcurrentuser) | **GET** /me |  |
| [**getCurrentUserRoles**](CurrentUserApi.md#getcurrentuserroles) | **GET** /me/roles |  |
| [**updateCurrentUserProfile**](CurrentUserApi.md#updatecurrentuserprofile) | **PATCH** /me/profile |  |



## getCurrentUser

> MeResponseEnvelope getCurrentUser()



### Example

```ts
import {
  Configuration,
  CurrentUserApi,
} from '@pickleball/api-client-generated';
import type { GetCurrentUserRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CurrentUserApi(config);

  try {
    const data = await api.getCurrentUser();
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

[**MeResponseEnvelope**](MeResponseEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Current user |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getCurrentUserRoles

> RolesResponseEnvelope getCurrentUserRoles()



### Example

```ts
import {
  Configuration,
  CurrentUserApi,
} from '@pickleball/api-client-generated';
import type { GetCurrentUserRolesRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CurrentUserApi(config);

  try {
    const data = await api.getCurrentUserRoles();
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

[**RolesResponseEnvelope**](RolesResponseEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Active role assignments |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## updateCurrentUserProfile

> MeResponseEnvelope updateCurrentUserProfile(profileUpdateRequest)



### Example

```ts
import {
  Configuration,
  CurrentUserApi,
} from '@pickleball/api-client-generated';
import type { UpdateCurrentUserProfileRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CurrentUserApi(config);

  const body = {
    // ProfileUpdateRequest
    profileUpdateRequest: ...,
  } satisfies UpdateCurrentUserProfileRequest;

  try {
    const data = await api.updateCurrentUserProfile(body);
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
| **profileUpdateRequest** | [ProfileUpdateRequest](ProfileUpdateRequest.md) |  | |

### Return type

[**MeResponseEnvelope**](MeResponseEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Updated current user |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

