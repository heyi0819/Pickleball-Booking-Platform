
# CourseMatchCoachAssignmentRequest


## Properties

Name | Type
------------ | -------------
`coachProfileId` | string
`sessionIndexes` | Array&lt;number&gt;

## Example

```typescript
import type { CourseMatchCoachAssignmentRequest } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "coachProfileId": null,
  "sessionIndexes": null,
} satisfies CourseMatchCoachAssignmentRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseMatchCoachAssignmentRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
