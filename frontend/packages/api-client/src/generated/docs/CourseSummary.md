
# CourseSummary


## Properties

Name | Type
------------ | -------------
`id` | string
`organizationId` | string
`courseNo` | string
`courseType` | string
`scheduleType` | string
`billingMode` | string
`skillLevel` | string
`expectedParticipantCount` | number
`minimumParticipants` | number
`maximumParticipants` | number
`totalSessionCount` | number
`status` | string
`nextSessionStartAt` | Date
`activeMembershipCount` | number

## Example

```typescript
import type { CourseSummary } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "organizationId": null,
  "courseNo": null,
  "courseType": null,
  "scheduleType": null,
  "billingMode": null,
  "skillLevel": null,
  "expectedParticipantCount": null,
  "minimumParticipants": null,
  "maximumParticipants": null,
  "totalSessionCount": null,
  "status": null,
  "nextSessionStartAt": null,
  "activeMembershipCount": null,
} satisfies CourseSummary

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseSummary
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
