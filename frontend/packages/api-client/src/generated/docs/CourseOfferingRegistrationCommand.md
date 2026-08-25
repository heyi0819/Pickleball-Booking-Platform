
# CourseOfferingRegistrationCommand


## Properties

Name | Type
------------ | -------------
`id` | string
`offeringId` | string
`status` | [CourseOfferingRegistrationStatus](CourseOfferingRegistrationStatus.md)
`registeredAt` | Date
`cancelledAt` | Date
`cancelReason` | string
`convertedCourseMembershipId` | string

## Example

```typescript
import type { CourseOfferingRegistrationCommand } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "offeringId": null,
  "status": null,
  "registeredAt": null,
  "cancelledAt": null,
  "cancelReason": null,
  "convertedCourseMembershipId": null,
} satisfies CourseOfferingRegistrationCommand

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseOfferingRegistrationCommand
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
