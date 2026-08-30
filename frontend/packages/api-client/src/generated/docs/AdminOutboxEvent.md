
# AdminOutboxEvent


## Properties

Name | Type
------------ | -------------
`id` | string
`organizationId` | string
`aggregateType` | string
`aggregateId` | string
`eventType` | string
`status` | [OutboxEventStatus](OutboxEventStatus.md)
`attemptCount` | number
`availableAt` | Date
`processedAt` | Date
`lastError` | string
`createdAt` | Date

## Example

```typescript
import type { AdminOutboxEvent } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "organizationId": null,
  "aggregateType": null,
  "aggregateId": null,
  "eventType": null,
  "status": null,
  "attemptCount": null,
  "availableAt": null,
  "processedAt": null,
  "lastError": null,
  "createdAt": null,
} satisfies AdminOutboxEvent

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AdminOutboxEvent
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
