
# RoleContext


## Properties

Name | Type
------------ | -------------
`roleCode` | [RoleCode](RoleCode.md)
`organizationId` | string
`organizationCode` | string
`organizationName` | string

## Example

```typescript
import type { RoleContext } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "roleCode": null,
  "organizationId": null,
  "organizationCode": null,
  "organizationName": null,
} satisfies RoleContext

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RoleContext
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
