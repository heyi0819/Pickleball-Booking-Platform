
# CoachApplicationRequest


## Properties

Name | Type
------------ | -------------
`applicationNote` | string
`skillLevel` | string
`bio` | string

## Example

```typescript
import type { CoachApplicationRequest } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "applicationNote": null,
  "skillLevel": null,
  "bio": null,
} satisfies CoachApplicationRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CoachApplicationRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
