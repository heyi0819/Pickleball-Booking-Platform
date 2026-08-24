
# LessonRequestDetail


## Properties

Name | Type
------------ | -------------
`lessonRequest` | [LessonRequest](LessonRequest.md)
`sessionPreferences` | [Array&lt;SessionPreference&gt;](SessionPreference.md)

## Example

```typescript
import type { LessonRequestDetail } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "lessonRequest": null,
  "sessionPreferences": null,
} satisfies LessonRequestDetail

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as LessonRequestDetail
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
