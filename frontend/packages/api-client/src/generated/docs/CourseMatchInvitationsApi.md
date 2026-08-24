# CourseMatchInvitationsApi

All URIs are relative to */api/v1*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**listMyCourseMatchInvitations**](CourseMatchInvitationsApi.md#listmycoursematchinvitations) | **GET** /course-match-invitations/mine |  |
| [**respondCourseMatchInvitation**](CourseMatchInvitationsApi.md#respondcoursematchinvitation) | **POST** /course-match-invitations/{invitationId}/response |  |



## listMyCourseMatchInvitations

> CourseMatchInvitationSummaryListEnvelope listMyCourseMatchInvitations()



### Example

```ts
import {
  Configuration,
  CourseMatchInvitationsApi,
} from '@pickleball/api-client-generated';
import type { ListMyCourseMatchInvitationsRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseMatchInvitationsApi(config);

  try {
    const data = await api.listMyCourseMatchInvitations();
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

[**CourseMatchInvitationSummaryListEnvelope**](CourseMatchInvitationSummaryListEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Current coach invitation inbox and response history |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## respondCourseMatchInvitation

> CourseMatchInvitationResponseEnvelope respondCourseMatchInvitation(invitationId, courseMatchInvitationResponseRequest)



### Example

```ts
import {
  Configuration,
  CourseMatchInvitationsApi,
} from '@pickleball/api-client-generated';
import type { RespondCourseMatchInvitationRequest } from '@pickleball/api-client-generated';

async function example() {
  console.log("🚀 Testing @pickleball/api-client-generated SDK...");
  const config = new Configuration({
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CourseMatchInvitationsApi(config);

  const body = {
    // string
    invitationId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // CourseMatchInvitationResponseRequest
    courseMatchInvitationResponseRequest: ...,
  } satisfies RespondCourseMatchInvitationRequest;

  try {
    const data = await api.respondCourseMatchInvitation(body);
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
| **invitationId** | `string` |  | [Defaults to `undefined`] |
| **courseMatchInvitationResponseRequest** | [CourseMatchInvitationResponseRequest](CourseMatchInvitationResponseRequest.md) |  | |

### Return type

[**CourseMatchInvitationResponseEnvelope**](CourseMatchInvitationResponseEnvelope.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Invitation response |  -  |
| **400** | Invalid request |  -  |
| **401** | Invalid or missing token |  -  |
| **403** | Access forbidden |  -  |
| **404** | Resource not found |  -  |
| **409** | State |  -  |
| **422** | Business validation failed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
