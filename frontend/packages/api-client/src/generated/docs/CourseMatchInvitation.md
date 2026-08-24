
# CourseMatchInvitation


## Properties

Name | Type
------------ | -------------
`invitationId` | string
`courseMatchSessionId` | string
`sessionIndex` | number
`coachProfileId` | string
`assignmentOrder` | number
`status` | string
`invitationSentAt` | Date
`respondedAt` | Date
`responseNote` | string

## Example

```typescript
import type { CourseMatchInvitation } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "invitationId": null,
  "courseMatchSessionId": null,
  "sessionIndex": null,
  "coachProfileId": null,
  "assignmentOrder": null,
  "status": null,
  "invitationSentAt": null,
  "respondedAt": null,
  "responseNote": null,
} satisfies CourseMatchInvitation

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseMatchInvitation
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
