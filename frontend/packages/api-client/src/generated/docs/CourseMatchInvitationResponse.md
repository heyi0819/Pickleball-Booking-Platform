
# CourseMatchInvitationResponse


## Properties

Name | Type
------------ | -------------
`invitationId` | string
`courseMatchId` | string
`courseMatchSessionId` | string
`coachProfileId` | string
`status` | string
`respondedAt` | Date
`responseNote` | string

## Example

```typescript
import type { CourseMatchInvitationResponse } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "invitationId": null,
  "courseMatchId": null,
  "courseMatchSessionId": null,
  "coachProfileId": null,
  "status": null,
  "respondedAt": null,
  "responseNote": null,
} satisfies CourseMatchInvitationResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseMatchInvitationResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
