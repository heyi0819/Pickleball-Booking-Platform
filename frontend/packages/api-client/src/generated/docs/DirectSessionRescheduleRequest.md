
# DirectSessionRescheduleRequest


## Properties

Name | Type
------------ | -------------
`startAt` | Date
`endAt` | Date
`reason` | string

## Example

```typescript
import type { DirectSessionRescheduleRequest } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "startAt": null,
  "endAt": null,
  "reason": null,
} satisfies DirectSessionRescheduleRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as DirectSessionRescheduleRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
