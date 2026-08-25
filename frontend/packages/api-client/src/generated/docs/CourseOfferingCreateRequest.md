
# CourseOfferingCreateRequest


## Properties

Name | Type
------------ | -------------
`organizationId` | string
`lessonType` | string
`coachProfileId` | string
`title` | string
`description` | string
`scheduleType` | [CourseOfferingScheduleType](CourseOfferingScheduleType.md)
`billingMode` | [CourseOfferingBillingMode](CourseOfferingBillingMode.md)
`skillLevel` | string
`minimumParticipants` | number
`maximumParticipants` | number
`registrationOpenAt` | Date
`registrationCloseAt` | Date
`sessionPlans` | [Array&lt;CourseOfferingSessionPlanRequest&gt;](CourseOfferingSessionPlanRequest.md)

## Example

```typescript
import type { CourseOfferingCreateRequest } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "organizationId": null,
  "lessonType": null,
  "coachProfileId": null,
  "title": null,
  "description": null,
  "scheduleType": null,
  "billingMode": null,
  "skillLevel": null,
  "minimumParticipants": null,
  "maximumParticipants": null,
  "registrationOpenAt": null,
  "registrationCloseAt": null,
  "sessionPlans": null,
} satisfies CourseOfferingCreateRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseOfferingCreateRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
