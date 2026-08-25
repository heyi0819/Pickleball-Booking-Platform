
# SessionRescheduleResult


## Properties

Name | Type
------------ | -------------
`changeRequestId` | string
`status` | string
`sessionId` | string
`sessionStatus` | string
`scheduledStartAt` | Date
`scheduledEndAt` | Date

## Example

```typescript
import type { SessionRescheduleResult } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "changeRequestId": null,
  "status": null,
  "sessionId": null,
  "sessionStatus": null,
  "scheduledStartAt": null,
  "scheduledEndAt": null,
} satisfies SessionRescheduleResult

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SessionRescheduleResult
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
