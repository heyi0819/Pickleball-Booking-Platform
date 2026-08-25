
# CourseOfferingSessionPlanRequest


## Properties

Name | Type
------------ | -------------
`sequenceNo` | number
`startAt` | Date
`endAt` | Date
`venueId` | string
`venueName` | string
`venueAddress` | string

## Example

```typescript
import type { CourseOfferingSessionPlanRequest } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "sequenceNo": null,
  "startAt": null,
  "endAt": null,
  "venueId": null,
  "venueName": null,
  "venueAddress": null,
} satisfies CourseOfferingSessionPlanRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseOfferingSessionPlanRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
