
# CourseSessionSummary


## Properties

Name | Type
------------ | -------------
`id` | string
`organizationId` | string
`courseId` | string
`sequenceNo` | number
`scheduledStartAt` | Date
`scheduledEndAt` | Date
`expectedParticipantCount` | number
`guestParticipantCount` | number
`actualParticipantCount` | number
`status` | string
`cancellationSource` | string
`cancellationNote` | string
`completedAt` | Date
`venueId` | string
`venueName` | string
`venueAddress` | string
`venueStatus` | string
`coachProfileId` | string
`coachDisplayName` | string
`ownEnrollmentId` | string
`ownEnrollmentStatus` | string

## Example

```typescript
import type { CourseSessionSummary } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "organizationId": null,
  "courseId": null,
  "sequenceNo": null,
  "scheduledStartAt": null,
  "scheduledEndAt": null,
  "expectedParticipantCount": null,
  "guestParticipantCount": null,
  "actualParticipantCount": null,
  "status": null,
  "cancellationSource": null,
  "cancellationNote": null,
  "completedAt": null,
  "venueId": null,
  "venueName": null,
  "venueAddress": null,
  "venueStatus": null,
  "coachProfileId": null,
  "coachDisplayName": null,
  "ownEnrollmentId": null,
  "ownEnrollmentStatus": null,
} satisfies CourseSessionSummary

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseSessionSummary
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
