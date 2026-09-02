
# AdminFinanceRefund


## Properties

Name | Type
------------ | -------------
`id` | string
`refundNo` | string
`organizationId` | string
`organizationName` | string
`paymentId` | string
`paymentNo` | string
`memberId` | string
`memberName` | string
`amount` | string
`currency` | string
`status` | string
`reason` | string
`requestedAt` | Date
`approvedAt` | Date
`refundedAt` | Date
`refundableAmount` | string
`receivables` | [Array&lt;AdminFinanceReceivableReference&gt;](AdminFinanceReceivableReference.md)

## Example

```typescript
import type { AdminFinanceRefund } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "refundNo": null,
  "organizationId": null,
  "organizationName": null,
  "paymentId": null,
  "paymentNo": null,
  "memberId": null,
  "memberName": null,
  "amount": null,
  "currency": null,
  "status": null,
  "reason": null,
  "requestedAt": null,
  "approvedAt": null,
  "refundedAt": null,
  "refundableAmount": null,
  "receivables": null,
} satisfies AdminFinanceRefund

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AdminFinanceRefund
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
