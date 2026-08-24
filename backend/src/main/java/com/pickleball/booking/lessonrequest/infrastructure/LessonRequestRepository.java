package com.pickleball.booking.lessonrequest.infrastructure;
import java.util.*; import org.springframework.data.jpa.repository.*; import jakarta.persistence.LockModeType;
public interface LessonRequestRepository extends JpaRepository<LessonRequestEntity,UUID> { List<LessonRequestEntity> findByOrganizationIdOrderByCreatedAtDesc(UUID org); List<LessonRequestEntity> findByRequesterUserIdOrderByCreatedAtDesc(UUID user); @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from LessonRequestEntity r where r.id=:id") Optional<LessonRequestEntity> findLockedById(UUID id); }
