
# FinanceRefundExecutionRequest


## Properties

Name | Type
------------ | -------------
`method` | [FinancePaymentMethod](FinancePaymentMethod.md)
`refundedAt` | Date
`reference` | string

## Example

```typescript
import type { FinanceRefundExecutionRequest } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "method": null,
  "refundedAt": null,
  "reference": null,
} satisfies FinanceRefundExecutionRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FinanceRefundExecutionRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
