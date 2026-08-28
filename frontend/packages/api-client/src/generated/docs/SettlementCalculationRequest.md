
# SettlementCalculationRequest


## Properties

Name | Type
------------ | -------------
`otherAdjustment` | string
`coachAllocations` | [Array&lt;SettlementCoachAllocationRequest&gt;](SettlementCoachAllocationRequest.md)

## Example

```typescript
import type { SettlementCalculationRequest } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "otherAdjustment": 0.00,
  "coachAllocations": null,
} satisfies SettlementCalculationRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SettlementCalculationRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
