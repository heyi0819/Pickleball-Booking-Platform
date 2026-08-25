
# CourseOfferingConfirmationEnvelope


## Properties

Name | Type
------------ | -------------
`data` | [CourseOfferingConfirmation](CourseOfferingConfirmation.md)
`meta` | [Meta](Meta.md)

## Example

```typescript
import type { CourseOfferingConfirmationEnvelope } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "data": null,
  "meta": null,
} satisfies CourseOfferingConfirmationEnvelope

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseOfferingConfirmationEnvelope
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
