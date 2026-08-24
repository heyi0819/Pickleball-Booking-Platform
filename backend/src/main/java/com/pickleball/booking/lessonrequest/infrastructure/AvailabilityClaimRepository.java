package com.pickleball.booking.lessonrequest.infrastructure;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface AvailabilityClaimRepository extends JpaRepository<AvailabilityClaimEntity,UUID> { }
