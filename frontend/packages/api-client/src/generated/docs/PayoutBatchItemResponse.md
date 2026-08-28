
# PayoutBatchItemResponse


## Properties

Name | Type
------------ | -------------
`payoutBatchItemId` | string
`coachSettlementId` | string
`coachProfileId` | string
`amount` | string
`status` | [PayoutBatchItemStatus](PayoutBatchItemStatus.md)
`paidAt` | Date
`referenceNo` | string
`failureReason` | string

## Example

```typescript
import type { PayoutBatchItemResponse } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "payoutBatchItemId": null,
  "coachSettlementId": null,
  "coachProfileId": null,
  "amount": 1500.00,
  "status": null,
  "paidAt": null,
  "referenceNo": null,
  "failureReason": null,
} satisfies PayoutBatchItemResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PayoutBatchItemResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
