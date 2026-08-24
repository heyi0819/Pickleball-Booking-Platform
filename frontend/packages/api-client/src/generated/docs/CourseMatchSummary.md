
# CourseMatchSummary


## Properties

Name | Type
------------ | -------------
`id` | string
`lessonRequestId` | string
`status` | string
`participantCount` | number
`version` | number
`createdAt` | Date
`readiness` | [CourseMatchReadiness](CourseMatchReadiness.md)
`pricing` | [CourseMatchPriceState](CourseMatchPriceState.md)

## Example

```typescript
import type { CourseMatchSummary } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "lessonRequestId": null,
  "status": null,
  "participantCount": null,
  "version": null,
  "createdAt": null,
  "readiness": null,
  "pricing": null,
} satisfies CourseMatchSummary

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseMatchSummary
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
