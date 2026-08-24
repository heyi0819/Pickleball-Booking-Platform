package com.pickleball.booking.shared.infrastructure;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ApiIdempotencyKeyRepository extends JpaRepository<ApiIdempotencyKeyEntity,UUID>{ Optional<ApiIdempotencyKeyEntity> findByActorUserIdAndOperationAndIdempotencyKey(UUID actorUserId,String operation,String idempotencyKey); }
