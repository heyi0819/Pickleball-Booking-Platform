
# CourseOfferingPage


## Properties

Name | Type
------------ | -------------
`items` | [Array&lt;CourseOfferingSummary&gt;](CourseOfferingSummary.md)
`page` | number
`size` | number
`total` | number

## Example

```typescript
import type { CourseOfferingPage } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "items": null,
  "page": null,
  "size": null,
  "total": null,
} satisfies CourseOfferingPage

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseOfferingPage
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
