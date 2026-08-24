
# CoachApplication


## Properties

Name | Type
------------ | -------------
`id` | string
`coachProfileId` | string
`status` | string
`applicationNote` | string
`submittedAt` | Date
`reviewedBy` | string
`reviewedAt` | Date
`reviewNote` | string

## Example

```typescript
import type { CoachApplication } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "coachProfileId": null,
  "status": null,
  "applicationNote": null,
  "submittedAt": null,
  "reviewedBy": null,
  "reviewedAt": null,
  "reviewNote": null,
} satisfies CoachApplication

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CoachApplication
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
