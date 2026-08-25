
# CourseOfferingDetailEnvelope


## Properties

Name | Type
------------ | -------------
`data` | [CourseOfferingDetail](CourseOfferingDetail.md)
`meta` | [Meta](Meta.md)

## Example

```typescript
import type { CourseOfferingDetailEnvelope } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "data": null,
  "meta": null,
} satisfies CourseOfferingDetailEnvelope

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseOfferingDetailEnvelope
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
