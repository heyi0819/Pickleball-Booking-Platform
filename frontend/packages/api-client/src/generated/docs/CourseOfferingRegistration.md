
# CourseOfferingRegistration


## Properties

Name | Type
------------ | -------------
`id` | string
`userId` | string
`displayName` | string
`status` | [CourseOfferingRegistrationStatus](CourseOfferingRegistrationStatus.md)
`registeredAt` | Date
`cancelledAt` | Date
`cancelReason` | string
`scheduleConflictIndicator` | boolean
`convertedCourseMembershipId` | string
`courseId` | string

## Example

```typescript
import type { CourseOfferingRegistration } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "userId": null,
  "displayName": null,
  "status": null,
  "registeredAt": null,
  "cancelledAt": null,
  "cancelReason": null,
  "scheduleConflictIndicator": null,
  "convertedCourseMembershipId": null,
  "courseId": null,
} satisfies CourseOfferingRegistration

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseOfferingRegistration
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
