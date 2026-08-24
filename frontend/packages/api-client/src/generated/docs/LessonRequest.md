
# LessonRequest


## Properties

Name | Type
------------ | -------------
`id` | string
`requesterUserId` | string
`preferredCoachProfileId` | string
`selectedAvailabilityProposalId` | string
`lessonType` | string
`scheduleType` | string
`billingMode` | string
`skillLevel` | string
`participantCount` | number
`guestParticipantCount` | number
`minimumParticipants` | number
`maximumParticipants` | number
`requestedSessionCount` | number
`status` | string
`notes` | string
`submittedAt` | Date
`reviewedBy` | string
`reviewedAt` | Date
`reviewNote` | string

## Example

```typescript
import type { LessonRequest } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "requesterUserId": null,
  "preferredCoachProfileId": null,
  "selectedAvailabilityProposalId": null,
  "lessonType": null,
  "scheduleType": null,
  "billingMode": null,
  "skillLevel": null,
  "participantCount": null,
  "guestParticipantCount": null,
  "minimumParticipants": null,
  "maximumParticipants": null,
  "requestedSessionCount": null,
  "status": null,
  "notes": null,
  "submittedAt": null,
  "reviewedBy": null,
  "reviewedAt": null,
  "reviewNote": null,
} satisfies LessonRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as LessonRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
