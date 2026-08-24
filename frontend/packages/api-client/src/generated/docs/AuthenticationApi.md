# AuthenticationApi

All URIs are relative to */api/v1*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**loginWithLine**](AuthenticationApi.md#loginwithline) | **POST** /auth/line/login |  |



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
