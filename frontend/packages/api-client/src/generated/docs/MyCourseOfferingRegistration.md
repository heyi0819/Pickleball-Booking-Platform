
# MyCourseOfferingRegistration


## Properties

Name | Type
------------ | -------------
`id` | string
`offeringId` | string
`offeringTitle` | string
`offeringStatus` | [CourseOfferingStatus](CourseOfferingStatus.md)
`status` | [CourseOfferingRegistrationStatus](CourseOfferingRegistrationStatus.md)
`registeredAt` | Date
`cancelledAt` | Date
`cancelReason` | string
`convertedCourseMembershipId` | string
`courseId` | string

## Example

```typescript
import type { MyCourseOfferingRegistration } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "offeringId": null,
  "offeringTitle": null,
  "offeringStatus": null,
  "status": null,
  "registeredAt": null,
  "cancelledAt": null,
  "cancelReason": null,
  "convertedCourseMembershipId": null,
  "courseId": null,
} satisfies MyCourseOfferingRegistration

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as MyCourseOfferingRegistration
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
