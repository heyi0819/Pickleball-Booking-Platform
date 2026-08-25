
# CourseOfferingConfirmation


## Properties

Name | Type
------------ | -------------
`offeringId` | string
`offeringStatus` | string
`courseId` | string
`sessionIds` | Array&lt;string&gt;
`receivableIds` | Array&lt;string&gt;

## Example

```typescript
import type { CourseOfferingConfirmation } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "offeringId": null,
  "offeringStatus": null,
  "courseId": null,
  "sessionIds": null,
  "receivableIds": null,
} satisfies CourseOfferingConfirmation

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseOfferingConfirmation
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
