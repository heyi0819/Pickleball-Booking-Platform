
# CourseMatchPatchRequest


## Properties

Name | Type
------------ | -------------
`participantCount` | number
`coachAssignments` | [Array&lt;CourseMatchCoachAssignmentRequest&gt;](CourseMatchCoachAssignmentRequest.md)
`sessionPlan` | [Array&lt;CourseMatchSessionPlanRequest&gt;](CourseMatchSessionPlanRequest.md)

## Example

```typescript
import type { CourseMatchPatchRequest } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "participantCount": null,
  "coachAssignments": null,
  "sessionPlan": null,
} satisfies CourseMatchPatchRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseMatchPatchRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
