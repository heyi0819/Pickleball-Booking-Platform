
# CourseMatchReadiness


## Properties

Name | Type
------------ | -------------
`lessonRequestApproved` | boolean
`coachesAccepted` | boolean
`sessionsFuture` | boolean
`scheduleConflictFree` | boolean
`venueReady` | boolean
`pricingConfirmed` | boolean
`participantCountValid` | boolean
`readyToConfirm` | boolean

## Example

```typescript
import type { CourseMatchReadiness } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "lessonRequestApproved": null,
  "coachesAccepted": null,
  "sessionsFuture": null,
  "scheduleConflictFree": null,
  "venueReady": null,
  "pricingConfirmed": null,
  "participantCountValid": null,
  "readyToConfirm": null,
} satisfies CourseMatchReadiness

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseMatchReadiness
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
