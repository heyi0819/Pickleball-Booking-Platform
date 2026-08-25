
# CourseDetailEnvelope


## Properties

Name | Type
------------ | -------------
`data` | [CourseDetail](CourseDetail.md)
`meta` | [Meta](Meta.md)

## Example

```typescript
import type { CourseDetailEnvelope } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "data": null,
  "meta": null,
} satisfies CourseDetailEnvelope

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseDetailEnvelope
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
