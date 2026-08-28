
# PayoutBatchCreateRequest


## Properties

Name | Type
------------ | -------------
`method` | [PayoutMethod](PayoutMethod.md)
`payoutDate` | Date
`items` | [Array&lt;PayoutBatchCreateItemRequest&gt;](PayoutBatchCreateItemRequest.md)

## Example

```typescript
import type { PayoutBatchCreateRequest } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "method": null,
  "payoutDate": null,
  "items": null,
} satisfies PayoutBatchCreateRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PayoutBatchCreateRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
