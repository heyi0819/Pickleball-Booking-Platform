
# PayoutBatchCreateResponse


## Properties

Name | Type
------------ | -------------
`payoutBatchId` | string
`batchNo` | string
`status` | string
`method` | [PayoutMethod](PayoutMethod.md)
`totalAmount` | string
`itemCount` | number

## Example

```typescript
import type { PayoutBatchCreateResponse } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "payoutBatchId": null,
  "batchNo": null,
  "status": null,
  "method": null,
  "totalAmount": 1500.00,
  "itemCount": null,
} satisfies PayoutBatchCreateResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PayoutBatchCreateResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
