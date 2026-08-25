
# SessionChangeReviewQueueItem


## Properties

Name | Type
------------ | -------------
`requestId` | string
`sessionId` | string
`courseId` | string
`courseNo` | string
`sequenceNo` | number
`scheduledStartAt` | Date
`scheduledEndAt` | Date
`requestedBy` | string
`requesterDisplayName` | string
`proposedStartAt` | Date
`proposedEndAt` | Date
`reason` | string
`status` | string
`createdAt` | Date

## Example

```typescript
import type { SessionChangeReviewQueueItem } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "requestId": null,
  "sessionId": null,
  "courseId": null,
  "courseNo": null,
  "sequenceNo": null,
  "scheduledStartAt": null,
  "scheduledEndAt": null,
  "requestedBy": null,
  "requesterDisplayName": null,
  "proposedStartAt": null,
  "proposedEndAt": null,
  "reason": null,
  "status": null,
  "createdAt": null,
} satisfies SessionChangeReviewQueueItem

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SessionChangeReviewQueueItem
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
