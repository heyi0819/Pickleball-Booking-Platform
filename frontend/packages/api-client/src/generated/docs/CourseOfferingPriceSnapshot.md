
# CourseOfferingPriceSnapshot


## Properties

Name | Type
------------ | -------------
`priceSnapshotId` | string
`offeringId` | string
`status` | string
`currency` | string
`pricePerParticipant` | string
`pricingFingerprint` | string
`confirmedBy` | string
`confirmedAt` | Date

## Example

```typescript
import type { CourseOfferingPriceSnapshot } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "priceSnapshotId": null,
  "offeringId": null,
  "status": null,
  "currency": null,
  "pricePerParticipant": null,
  "pricingFingerprint": null,
  "confirmedBy": null,
  "confirmedAt": null,
} satisfies CourseOfferingPriceSnapshot

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseOfferingPriceSnapshot
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
