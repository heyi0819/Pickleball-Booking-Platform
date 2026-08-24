
# CourseMatchPricingPreview


## Properties

Name | Type
------------ | -------------
`courseMatchId` | string
`currency` | string
`billingMode` | string
`totalAmount` | string
`breakdown` | [Array&lt;CourseMatchPricingItem&gt;](CourseMatchPricingItem.md)
`pricingFingerprint` | string

## Example

```typescript
import type { CourseMatchPricingPreview } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "courseMatchId": null,
  "currency": null,
  "billingMode": null,
  "totalAmount": null,
  "breakdown": null,
  "pricingFingerprint": null,
} satisfies CourseMatchPricingPreview

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseMatchPricingPreview
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
