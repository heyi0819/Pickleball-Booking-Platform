
# PayoutBatchResponse


## Properties

Name | Type
------------ | -------------
`payoutBatchId` | string
`batchNo` | string
`status` | [PayoutBatchStatus](PayoutBatchStatus.md)
`payoutDate` | Date
`method` | [PayoutMethod](PayoutMethod.md)
`currency` | string
`totalAmount` | string
`itemCount` | number
`approvedAt` | Date
`completedAt` | Date
`items` | [Array&lt;PayoutBatchItemResponse&gt;](PayoutBatchItemResponse.md)

## Example

```typescript
import type { PayoutBatchResponse } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "payoutBatchId": null,
  "batchNo": null,
  "status": null,
  "payoutDate": null,
  "method": null,
  "currency": null,
  "totalAmount": 1500.00,
  "itemCount": null,
  "approvedAt": null,
  "completedAt": null,
  "items": null,
} satisfies PayoutBatchResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PayoutBatchResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
