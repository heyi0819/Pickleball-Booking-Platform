
# FinanceRefundRequestResponse


## Properties

Name | Type
------------ | -------------
`refundId` | string
`paymentId` | string
`status` | string
`amount` | string
`currency` | string

## Example

```typescript
import type { FinanceRefundRequestResponse } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "refundId": null,
  "paymentId": null,
  "status": null,
  "amount": 1000.00,
  "currency": null,
} satisfies FinanceRefundRequestResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FinanceRefundRequestResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
