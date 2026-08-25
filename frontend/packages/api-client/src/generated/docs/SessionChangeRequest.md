
# SessionChangeRequest


## Properties

Name | Type
------------ | -------------
`changeRequestId` | string
`sessionId` | string
`status` | string
`requestType` | string
`proposedStartAt` | Date
`proposedEndAt` | Date
`reason` | string
`decidedBy` | string
`decidedAt` | Date
`decisionReason` | string

## Example

```typescript
import type { SessionChangeRequest } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "changeRequestId": null,
  "sessionId": null,
  "status": null,
  "requestType": null,
  "proposedStartAt": null,
  "proposedEndAt": null,
  "reason": null,
  "decidedBy": null,
  "decidedAt": null,
  "decisionReason": null,
} satisfies SessionChangeRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SessionChangeRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
