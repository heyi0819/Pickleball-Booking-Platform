
# CoachSettlementSelfItem


## Properties

Name | Type
------------ | -------------
`coachSettlementId` | string
`courseSessionId` | string
`payableAmount` | string
`paidAmount` | string
`outstandingAmount` | string
`payoutStatus` | [CoachPayoutStatus](CoachPayoutStatus.md)

## Example

```typescript
import type { CoachSettlementSelfItem } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "coachSettlementId": null,
  "courseSessionId": null,
  "payableAmount": 1500.00,
  "paidAmount": 1500.00,
  "outstandingAmount": 1500.00,
  "payoutStatus": null,
} satisfies CoachSettlementSelfItem

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CoachSettlementSelfItem
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
