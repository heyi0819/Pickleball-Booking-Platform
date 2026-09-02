
# AdminFinanceReceivable


## Properties

Name | Type
------------ | -------------
`id` | string
`receivableNo` | string
`organizationId` | string
`organizationName` | string
`memberId` | string
`memberName` | string
`courseId` | string
`courseNo` | string
`currency` | string
`totalAmount` | string
`adjustedAmount` | string
`paidAmount` | string
`refundedAmount` | string
`outstandingAmount` | string
`status` | string
`createdAt` | Date
`dueAt` | Date

## Example

```typescript
import type { AdminFinanceReceivable } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "receivableNo": null,
  "organizationId": null,
  "organizationName": null,
  "memberId": null,
  "memberName": null,
  "courseId": null,
  "courseNo": null,
  "currency": null,
  "totalAmount": null,
  "adjustedAmount": null,
  "paidAmount": null,
  "refundedAmount": null,
  "outstandingAmount": null,
  "status": null,
  "createdAt": null,
  "dueAt": null,
} satisfies AdminFinanceReceivable

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AdminFinanceReceivable
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
