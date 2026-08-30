
# AdminNotificationPage


## Properties

Name | Type
------------ | -------------
`items` | [Array&lt;AdminNotification&gt;](AdminNotification.md)
`page` | number
`size` | number
`totalElements` | number

## Example

```typescript
import type { AdminNotificationPage } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "items": null,
  "page": null,
  "size": null,
  "totalElements": null,
} satisfies AdminNotificationPage

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AdminNotificationPage
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
