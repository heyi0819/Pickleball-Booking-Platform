
# CommitteeMembership


## Properties

Name | Type
------------ | -------------
`id` | string
`userId` | string
`organizationId` | string
`status` | string

## Example

```typescript
import type { CommitteeMembership } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "userId": null,
  "organizationId": null,
  "status": null,
} satisfies CommitteeMembership

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CommitteeMembership
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
