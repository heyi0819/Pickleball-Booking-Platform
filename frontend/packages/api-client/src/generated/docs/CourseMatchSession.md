
# CourseMatchSession


## Properties

Name | Type
------------ | -------------
`id` | string
`sequenceNo` | number
`startAt` | Date
`endAt` | Date
`venueType` | string
`venueId` | string
`venueName` | string
`venueAddress` | string

## Example

```typescript
import type { CourseMatchSession } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "sequenceNo": null,
  "startAt": null,
  "endAt": null,
  "venueType": null,
  "venueId": null,
  "venueName": null,
  "venueAddress": null,
} satisfies CourseMatchSession

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseMatchSession
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
