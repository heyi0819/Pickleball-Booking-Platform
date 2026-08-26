
# FinanceRefundRequest


## Properties

Name | Type
------------ | -------------
`paymentId` | string
`amount` | string
`reason` | string

## Example

```typescript
import type { FinanceRefundRequest } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "paymentId": null,
  "amount": 1000.00,
  "reason": null,
} satisfies FinanceRefundRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FinanceRefundRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
