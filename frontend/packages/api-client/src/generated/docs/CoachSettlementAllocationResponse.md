
# CoachSettlementAllocationResponse


## Properties

Name | Type
------------ | -------------
`coachSettlementId` | string
`coachProfileId` | string
`payableAmount` | string
`paidAmount` | string
`payoutStatus` | [CoachPayoutStatus](CoachPayoutStatus.md)
`version` | number

## Example

```typescript
import type { CoachSettlementAllocationResponse } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "coachSettlementId": null,
  "coachProfileId": null,
  "payableAmount": 1500.00,
  "paidAmount": 1500.00,
  "payoutStatus": null,
  "version": null,
} satisfies CoachSettlementAllocationResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CoachSettlementAllocationResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
