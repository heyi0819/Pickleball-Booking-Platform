
# CourseOfferingDetail


## Properties

Name | Type
------------ | -------------
`summary` | [CourseOfferingSummary](CourseOfferingSummary.md)
`description` | string
`sessionPlans` | [Array&lt;CourseOfferingSessionPlan&gt;](CourseOfferingSessionPlan.md)

## Example

```typescript
import type { CourseOfferingDetail } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "summary": null,
  "description": null,
  "sessionPlans": null,
} satisfies CourseOfferingDetail

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseOfferingDetail
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
