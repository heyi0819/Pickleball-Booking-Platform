
# SettlementCoachAllocationRequest


## Properties

Name | Type
------------ | -------------
`coachProfileId` | string
`allocationType` | [SettlementAllocationType](SettlementAllocationType.md)
`allocationValue` | string

## Example

```typescript
import type { SettlementCoachAllocationRequest } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "coachProfileId": null,
  "allocationType": null,
  "allocationValue": null,
} satisfies SettlementCoachAllocationRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SettlementCoachAllocationRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
