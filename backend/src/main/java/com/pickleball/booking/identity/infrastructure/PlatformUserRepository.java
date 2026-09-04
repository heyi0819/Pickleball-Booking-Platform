package com.pickleball.booking.identity.infrastructure;
import java.util.UUID;
import java.util.List;
import com.pickleball.booking.identity.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PlatformUserRepository extends JpaRepository<PlatformUserEntity, UUID> {
    List<PlatformUserEntity> findTop20ByDisplayNameContainingIgnoreCaseAndStatusOrderByDisplayNameAsc(String query, UserStatus status);
}
