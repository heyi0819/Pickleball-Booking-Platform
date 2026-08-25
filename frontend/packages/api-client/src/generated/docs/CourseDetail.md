
# CourseDetail


## Properties

Name | Type
------------ | -------------
`id` | string
`organizationId` | string
`courseNo` | string
`sourceMatchId` | string
`sourceOfferingId` | string
`createdByUserId` | string
`courseType` | string
`scheduleType` | string
`billingMode` | string
`skillLevel` | string
`expectedParticipantCount` | number
`guestParticipantCount` | number
`minimumParticipants` | number
`maximumParticipants` | number
`totalSessionCount` | number
`status` | string
`activatedAt` | Date
`completedAt` | Date
`cancelledAt` | Date
`createdAt` | Date
`updatedAt` | Date
`nextSessionStartAt` | Date
`activeMembershipCount` | number

## Example

```typescript
import type { CourseDetail } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "organizationId": null,
  "courseNo": null,
  "sourceMatchId": null,
  "sourceOfferingId": null,
  "createdByUserId": null,
  "courseType": null,
  "scheduleType": null,
  "billingMode": null,
  "skillLevel": null,
  "expectedParticipantCount": null,
  "guestParticipantCount": null,
  "minimumParticipants": null,
  "maximumParticipants": null,
  "totalSessionCount": null,
  "status": null,
  "activatedAt": null,
  "completedAt": null,
  "cancelledAt": null,
  "createdAt": null,
  "updatedAt": null,
  "nextSessionStartAt": null,
  "activeMembershipCount": null,
} satisfies CourseDetail

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseDetail
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
