package com.pickleball.booking.identity.infrastructure;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PlatformUserRepository extends JpaRepository<PlatformUserEntity, UUID> {}
