
# CourseOfferingSummary


## Properties

Name | Type
------------ | -------------
`id` | string
`organizationId` | string
`title` | string
`status` | [CourseOfferingStatus](CourseOfferingStatus.md)
`coach` | [CourseOfferingCoachSummary](CourseOfferingCoachSummary.md)
`scheduleType` | [CourseOfferingScheduleType](CourseOfferingScheduleType.md)
`firstSessionAt` | Date
`registrationOpenAt` | Date
`registrationCloseAt` | Date
`minimumParticipants` | number
`maximumParticipants` | number
`registeredCount` | number
`remainingCapacity` | number
`billingMode` | [CourseOfferingBillingMode](CourseOfferingBillingMode.md)
`skillLevel` | string
`priceSnapshotId` | string
`pricePerParticipant` | number
`currency` | string
`registrationState` | string
`ownRegistrationId` | string
`ownRegistrationStatus` | string
`version` | number

## Example

```typescript
import type { CourseOfferingSummary } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "organizationId": null,
  "title": null,
  "status": null,
  "coach": null,
  "scheduleType": null,
  "firstSessionAt": null,
  "registrationOpenAt": null,
  "registrationCloseAt": null,
  "minimumParticipants": null,
  "maximumParticipants": null,
  "registeredCount": null,
  "remainingCapacity": null,
  "billingMode": null,
  "skillLevel": null,
  "priceSnapshotId": null,
  "pricePerParticipant": null,
  "currency": null,
  "registrationState": null,
  "ownRegistrationId": null,
  "ownRegistrationStatus": null,
  "version": null,
} satisfies CourseOfferingSummary

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseOfferingSummary
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
