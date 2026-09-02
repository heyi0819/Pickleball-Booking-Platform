# AuthenticationApi

All URIs are relative to */api/v1*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**exchangeAdminLineAuthorizationCode**](AuthenticationApi.md#exchangeadminlineauthorizationcode) | **POST** /auth/line/admin/exchange |  |
| [**loginWithLine**](AuthenticationApi.md#loginwithline) | **POST** /auth/line/login |  |



## exchangeAdminLineAuthorizationCode

> LoginResponseEnvelope exchangeAdminLineAuthorizationCode(adminLineExchangeRequest)



### Example

```ts
import {
  Configuration,
  AuthenticationApi,
} from '@pickleball/api-client-generated';
import type { ExchangeAdminLineAuthorizationCodeRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const api = new AuthenticationApi();

  const body = {
    // AdminLineExchangeRequest
    adminLineExchangeRequest: ...,
  } satisfies ExchangeAdminLineAuthorizationCodeRequest;

  try {
    const data = await api.exchangeAdminLineAuthorizationCode(body);
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
| **adminLineExchangeRequest** | [AdminLineExchangeRequest](AdminLineExchangeRequest.md) |  | |

### Return type

[**LoginResponseEnvelope**](LoginResponseEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Platform access token issued for an existing administrator |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## loginWithLine

> LoginResponseEnvelope loginWithLine(lineLoginRequest)



### Example

```ts
import {
  Configuration,
  AuthenticationApi,
} from '@pickleball/api-client-generated';
import type { LoginWithLineRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const api = new AuthenticationApi();

  const body = {
    // LineLoginRequest
    lineLoginRequest: ...,
  } satisfies LoginWithLineRequest;

  try {
    const data = await api.loginWithLine(body);
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
| **lineLoginRequest** | [LineLoginRequest](LineLoginRequest.md) |  | |

### Return type

[**LoginResponseEnvelope**](LoginResponseEnvelope.md)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Platform access token issued |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
