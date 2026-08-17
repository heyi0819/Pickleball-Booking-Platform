
# Me


## Properties

Name | Type
------------ | -------------
`id` | string
`displayName` | string
`phone` | string
`email` | string
`locale` | string
`profileComplete` | boolean
`roles` | [Array&lt;RoleContext&gt;](RoleContext.md)

## Example

```typescript
import type { Me } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "displayName": null,
  "phone": null,
  "email": null,
  "locale": null,
  "profileComplete": null,
  "roles": null,
} satisfies Me

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as Me
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


