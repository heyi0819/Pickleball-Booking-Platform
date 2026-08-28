
# SessionSettlementResponse


## Properties

Name | Type
------------ | -------------
`sessionSettlementId` | string
`courseSessionId` | string
`status` | [SettlementStatus](SettlementStatus.md)
`grossReceivable` | string
`venueCost` | string
`otherAdjustment` | string
`distributableAmount` | string
`coachSettlements` | [Array&lt;CoachSettlementAllocationResponse&gt;](CoachSettlementAllocationResponse.md)
`version` | number

## Example

```typescript
import type { SessionSettlementResponse } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "sessionSettlementId": null,
  "courseSessionId": null,
  "status": null,
  "grossReceivable": 1500.00,
  "venueCost": 1500.00,
  "otherAdjustment": 0.00,
  "distributableAmount": 1500.00,
  "coachSettlements": null,
  "version": null,
} satisfies SessionSettlementResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SessionSettlementResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
