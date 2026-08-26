
# FinanceRefundReviewResponse


## Properties

Name | Type
------------ | -------------
`refundId` | string
`status` | string
`approvedBy` | string
`approvedAt` | Date

## Example

```typescript
import type { FinanceRefundReviewResponse } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "refundId": null,
  "status": null,
  "approvedBy": null,
  "approvedAt": null,
} satisfies FinanceRefundReviewResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FinanceRefundReviewResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
