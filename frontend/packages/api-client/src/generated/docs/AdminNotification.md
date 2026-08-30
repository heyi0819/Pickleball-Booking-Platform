
# AdminNotification


## Properties

Name | Type
------------ | -------------
`id` | string
`organizationId` | string
`notificationTargetId` | string
`recipientUserId` | string
`channel` | string
`templateCode` | string
`businessType` | string
`businessId` | string
`status` | [NotificationStatus](NotificationStatus.md)
`attemptCount` | number
`nextAttemptAt` | Date
`sentAt` | Date
`lastErrorCode` | string
`lastErrorMessage` | string
`createdAt` | Date
`updatedAt` | Date

## Example

```typescript
import type { AdminNotification } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "organizationId": null,
  "notificationTargetId": null,
  "recipientUserId": null,
  "channel": null,
  "templateCode": null,
  "businessType": null,
  "businessId": null,
  "status": null,
  "attemptCount": null,
  "nextAttemptAt": null,
  "sentAt": null,
  "lastErrorCode": null,
  "lastErrorMessage": null,
  "createdAt": null,
  "updatedAt": null,
} satisfies AdminNotification

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AdminNotification
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
