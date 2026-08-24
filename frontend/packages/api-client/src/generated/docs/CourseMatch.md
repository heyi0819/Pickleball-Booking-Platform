
# CourseMatch


## Properties

Name | Type
------------ | -------------
`id` | string
`lessonRequestId` | string
`status` | string
`participantCount` | number
`minimumParticipants` | number
`maximumParticipants` | number
`version` | number
`sessions` | [Array&lt;CourseMatchSession&gt;](CourseMatchSession.md)
`coachInvitations` | [Array&lt;CourseMatchInvitation&gt;](CourseMatchInvitation.md)
`readiness` | [CourseMatchReadiness](CourseMatchReadiness.md)
`pricing` | [CourseMatchPriceState](CourseMatchPriceState.md)

## Example

```typescript
import type { CourseMatch } from '@pickleball/api-client-generated'

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "lessonRequestId": null,
  "status": null,
  "participantCount": null,
  "minimumParticipants": null,
  "maximumParticipants": null,
  "version": null,
  "sessions": null,
  "coachInvitations": null,
  "readiness": null,
  "pricing": null,
} satisfies CourseMatch

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CourseMatch
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
