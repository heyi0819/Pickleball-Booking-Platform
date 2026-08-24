
# LoginUser


## Properties

Name | Type
------------ | -------------
`id` | string
`displayName` | string
`roles` | [Array&lt;RoleCode&gt;](RoleCode.md)

## Example

```typescript
import type { LoginUser } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "displayName": null,
  "roles": null,
} satisfies LoginUser

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as LoginUser
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
