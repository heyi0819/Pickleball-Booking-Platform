
# SettlementCalculationResponse


## Properties

Name | Type
------------ | -------------
`sessionSettlementId` | string
`grossReceivable` | string
`venueCost` | string
`otherAdjustment` | string
`distributableAmount` | string
`coachPayableTotal` | string
`status` | string

## Example

```typescript
import type { SettlementCalculationResponse } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "sessionSettlementId": null,
  "grossReceivable": 1500.00,
  "venueCost": 1500.00,
  "otherAdjustment": 0.00,
  "distributableAmount": 1500.00,
  "coachPayableTotal": 1500.00,
  "status": null,
} satisfies SettlementCalculationResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SettlementCalculationResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
