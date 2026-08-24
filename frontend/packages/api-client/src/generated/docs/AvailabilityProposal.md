
# AvailabilityProposal


## Properties

Name | Type
------------ | -------------
`id` | string
`coachProfileId` | string
`startAt` | Date
`endAt` | Date
`preferredVenueId` | string
`status` | string
`submittedAt` | Date
`reviewedBy` | string
`reviewedAt` | Date
`reviewNote` | string

## Example

```typescript
import type { AvailabilityProposal } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "coachProfileId": null,
  "startAt": null,
  "endAt": null,
  "preferredVenueId": null,
  "status": null,
  "submittedAt": null,
  "reviewedBy": null,
  "reviewedAt": null,
  "reviewNote": null,
} satisfies AvailabilityProposal

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AvailabilityProposal
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
