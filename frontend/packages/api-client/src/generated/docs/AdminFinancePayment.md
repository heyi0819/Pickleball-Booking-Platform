
# AdminFinancePayment


## Properties

Name | Type
------------ | -------------
`id` | string
`paymentNo` | string
`organizationId` | string
`organizationName` | string
`memberId` | string
`memberName` | string
`amount` | string
`currency` | string
`status` | string
`method` | [FinancePaymentMethod](FinancePaymentMethod.md)
`paidAt` | Date
`recordedAt` | Date
`refundableAmount` | string
`receivables` | [Array&lt;AdminFinanceReceivableReference&gt;](AdminFinanceReceivableReference.md)

## Example

```typescript
import type { AdminFinancePayment } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "paymentNo": null,
  "organizationId": null,
  "organizationName": null,
  "memberId": null,
  "memberName": null,
  "amount": null,
  "currency": null,
  "status": null,
  "method": null,
  "paidAt": null,
  "recordedAt": null,
  "refundableAmount": null,
  "receivables": null,
} satisfies AdminFinancePayment

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AdminFinancePayment
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
