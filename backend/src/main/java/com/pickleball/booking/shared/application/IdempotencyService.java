package com.pickleball.booking.shared.application;
import com.pickleball.booking.shared.infrastructure.*; import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.util.*; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service;
@Service
public class IdempotencyService {
 private final ApiIdempotencyKeyRepository keys; private final JdbcTemplate jdbc;
 public IdempotencyService(ApiIdempotencyKeyRepository keys,JdbcTemplate jdbc){this.keys=keys;this.jdbc=jdbc;}
 public ApiIdempotencyKeyEntity begin(UUID org,UUID actor,String operation,String key,String requestIdentity){if(key==null||key.isBlank()||key.length()>100)throw new BusinessException("VALIDATION_FAILED","Idempotency-Key is required");String hash=sha256(requestIdentity);var existing=keys.findByActorUserIdAndOperationAndIdempotencyKey(actor,operation,key);if(existing.isPresent())return verify(existing.get(),hash);jdbc.update("insert into api_idempotency_keys (id, organization_id, actor_user_id, operation, idempotency_key, request_hash, expires_at) values (?, ?, ?, ?, ?, ?, now() + interval '90 days') on conflict (actor_user_id, operation, idempotency_key) do nothing",UUID.randomUUID(),org,actor,operation,key,hash);return verify(keys.findByActorUserIdAndOperationAndIdempotencyKey(actor,operation,key).orElseThrow(()->new IllegalStateException("Idempotency record was not persisted")),hash);}
 private ApiIdempotencyKeyEntity verify(ApiIdempotencyKeyEntity record,String hash){if(!record.getRequestHash().equals(hash))throw new BusinessException("IDEMPOTENCY_CONFLICT","Idempotency key was reused with another request");return record;}
 private String sha256(String value){try{var digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(digest);}catch(Exception e){throw new IllegalStateException(e);}}
}
