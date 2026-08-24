
# CourseMatchPricingItem


## Properties

Name | Type
------------ | -------------
`courseMatchSessionId` | string
`itemType` | string
`description` | string
`quantity` | string
`unitAmount` | string
`lineAmount` | string
`sourceReferenceType` | string
`sourceReferenceId` | string

## Example

```typescript
import type { CourseMatchPricingItem } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "courseMatchSessionId": null,
  "itemType": null,
  "description": null,
  "quantity": null,
  "unitAmount": null,
  "lineAmount": null,
  "sourceReferenceType": null,
  "sourceReferenceId": null,
} satisfies CourseMatchPricingItem

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseMatchPricingItem
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
