
# CourseOfferingPricingPreview


## Properties

Name | Type
------------ | -------------
`offeringId` | string
`currency` | string
`pricePerParticipant` | string
`billingMode` | [CourseOfferingBillingMode](CourseOfferingBillingMode.md)
`sessionCount` | number
`pricingFingerprint` | string

## Example

```typescript
import type { CourseOfferingPricingPreview } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "offeringId": null,
  "currency": null,
  "pricePerParticipant": null,
  "billingMode": null,
  "sessionCount": null,
  "pricingFingerprint": null,
} satisfies CourseOfferingPricingPreview

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseOfferingPricingPreview
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
