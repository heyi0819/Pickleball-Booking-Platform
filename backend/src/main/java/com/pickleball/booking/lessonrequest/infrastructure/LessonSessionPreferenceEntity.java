package com.pickleball.booking.lessonrequest.infrastructure;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="lesson_request_session_preferences")
public class LessonSessionPreferenceEntity {
 @Id private UUID id; @Column(name="lesson_request_id",nullable=false) private UUID lessonRequestId; @Column(name="sequence_no",nullable=false) private short sequenceNo; @Column(name="preferred_start_at",nullable=false) private Instant startAt; @Column(name="preferred_end_at",nullable=false) private Instant endAt; @Column(name="preferred_venue_id") private UUID preferredVenueId; private String note; @Column(name="created_at",nullable=false) private Instant createdAt;
 protected LessonSessionPreferenceEntity(){} public LessonSessionPreferenceEntity(UUID request,short sequence,Instant start,Instant end,UUID venue,String note){if(start==null||end==null||!start.isBefore(end))throw new IllegalArgumentException("Session preference time range is invalid");id=UUID.randomUUID();lessonRequestId=request;sequenceNo=sequence;startAt=start;endAt=end;preferredVenueId=venue;this.note=note;} @PrePersist void created(){createdAt=Instant.now();}
 public UUID getId(){return id;} public UUID getLessonRequestId(){return lessonRequestId;} public short getSequenceNo(){return sequenceNo;} public Instant getStartAt(){return startAt;} public Instant getEndAt(){return endAt;} public UUID getPreferredVenueId(){return preferredVenueId;} public String getNote(){return note;}
}
