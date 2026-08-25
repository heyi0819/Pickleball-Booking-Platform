
# CoachSessionCancellationReview


## Properties

Name | Type
------------ | -------------
`requestId` | string
`status` | string
`sessionId` | string
`sessionStatus` | string
`reviewedAt` | Date

## Example

```typescript
import type { CoachSessionCancellationReview } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "requestId": null,
  "status": null,
  "sessionId": null,
  "sessionStatus": null,
  "reviewedAt": null,
} satisfies CoachSessionCancellationReview

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CoachSessionCancellationReview
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
