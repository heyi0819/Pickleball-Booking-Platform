
# CourseMatchInvitationSummary


## Properties

Name | Type
------------ | -------------
`invitationId` | string
`courseMatchId` | string
`courseMatchSessionId` | string
`sessionIndex` | number
`startAt` | Date
`endAt` | Date
`venueName` | string
`coachProfileId` | string
`status` | string
`invitationSentAt` | Date
`respondedAt` | Date
`responseNote` | string

## Example

```typescript
import type { CourseMatchInvitationSummary } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "invitationId": null,
  "courseMatchId": null,
  "courseMatchSessionId": null,
  "sessionIndex": null,
  "startAt": null,
  "endAt": null,
  "venueName": null,
  "coachProfileId": null,
  "status": null,
  "invitationSentAt": null,
  "respondedAt": null,
  "responseNote": null,
} satisfies CourseMatchInvitationSummary

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseMatchInvitationSummary
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
