
# FinancePaymentResponse


## Properties

Name | Type
------------ | -------------
`paymentId` | string
`receivableId` | string
`amount` | string
`method` | [FinancePaymentMethod](FinancePaymentMethod.md)
`paymentStatus` | string
`paidTotal` | string
`outstandingAmount` | string

## Example

```typescript
import type { FinancePaymentResponse } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "paymentId": null,
  "receivableId": null,
  "amount": 1000.00,
  "method": null,
  "paymentStatus": null,
  "paidTotal": 1000.00,
  "outstandingAmount": 1000.00,
} satisfies FinancePaymentResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FinancePaymentResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
