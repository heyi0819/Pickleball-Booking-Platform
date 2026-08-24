package com.pickleball.booking.lessonrequest.infrastructure;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface LessonSessionPreferenceRepository extends JpaRepository<LessonSessionPreferenceEntity,UUID> { List<LessonSessionPreferenceEntity> findByLessonRequestIdOrderBySequenceNo(UUID requestId); }
