
# CourseMatchCreateRequest


## Properties

Name | Type
------------ | -------------
`lessonRequestId` | string
`coachAssignments` | [Array&lt;CourseMatchCoachAssignmentRequest&gt;](CourseMatchCoachAssignmentRequest.md)
`sessionPlan` | [Array&lt;CourseMatchSessionPlanRequest&gt;](CourseMatchSessionPlanRequest.md)
`participantCount` | number

## Example

```typescript
import type { CourseMatchCreateRequest } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "lessonRequestId": null,
  "coachAssignments": null,
  "sessionPlan": null,
  "participantCount": null,
} satisfies CourseMatchCreateRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseMatchCreateRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
