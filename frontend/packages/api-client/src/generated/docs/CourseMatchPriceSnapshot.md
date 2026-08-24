
# CourseMatchPriceSnapshot


## Properties

Name | Type
------------ | -------------
`priceSnapshotId` | string
`courseMatchId` | string
`status` | string
`billingMode` | string
`totalAmount` | string
`currency` | string
`pricingFingerprint` | string
`confirmedBy` | string
`confirmedAt` | Date

## Example

```typescript
import type { CourseMatchPriceSnapshot } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "priceSnapshotId": null,
  "courseMatchId": null,
  "status": null,
  "billingMode": null,
  "totalAmount": null,
  "currency": null,
  "pricingFingerprint": null,
  "confirmedBy": null,
  "confirmedAt": null,
} satisfies CourseMatchPriceSnapshot

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseMatchPriceSnapshot
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
