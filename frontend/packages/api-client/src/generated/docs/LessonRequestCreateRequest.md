
# LessonRequestCreateRequest


## Properties

Name | Type
------------ | -------------
`lessonType` | string
`scheduleType` | string
`billingMode` | string
`skillLevel` | string
`participantCount` | number
`guestParticipantCount` | number
`minimumParticipants` | number
`maximumParticipants` | number
`requestedSessionCount` | number
`preferredCoachProfileId` | string
`selectedAvailabilityProposalId` | string
`sessionPreferences` | [Array&lt;SessionPreference&gt;](SessionPreference.md)
`notes` | string

## Example

```typescript
import type { LessonRequestCreateRequest } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "lessonType": null,
  "scheduleType": null,
  "billingMode": null,
  "skillLevel": null,
  "participantCount": null,
  "guestParticipantCount": null,
  "minimumParticipants": null,
  "maximumParticipants": null,
  "requestedSessionCount": null,
  "preferredCoachProfileId": null,
  "selectedAvailabilityProposalId": null,
  "sessionPreferences": null,
  "notes": null,
} satisfies LessonRequestCreateRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as LessonRequestCreateRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
