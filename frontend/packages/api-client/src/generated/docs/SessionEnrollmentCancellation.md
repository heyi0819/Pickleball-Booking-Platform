
# SessionEnrollmentCancellation


## Properties

Name | Type
------------ | -------------
`enrollmentId` | string
`status` | string
`cancelledAt` | Date
`courseSessionStatus` | string

## Example

```typescript
import type { SessionEnrollmentCancellation } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "enrollmentId": null,
  "status": null,
  "cancelledAt": null,
  "courseSessionStatus": null,
} satisfies SessionEnrollmentCancellation

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SessionEnrollmentCancellation
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
