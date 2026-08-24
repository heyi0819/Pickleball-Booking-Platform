package com.pickleball.booking.shared.infrastructure;

import jakarta.persistence.*;
import java.time.*; import java.util.UUID;
@Entity @Table(name="api_idempotency_keys")
public class ApiIdempotencyKeyEntity {
 @Id private UUID id; @Column(name="organization_id") private UUID organizationId; @Column(name="actor_user_id",nullable=false) private UUID actorUserId; @Column(nullable=false) private String operation; @Column(name="idempotency_key",nullable=false) private String idempotencyKey; @Column(name="request_hash",nullable=false) private String requestHash; @Column(name="result_resource_type") private String resultResourceType; @Column(name="result_resource_id") private UUID resultResourceId; @Column(name="response_status") private Integer responseStatus; @Column(name="expires_at",nullable=false) private Instant expiresAt; @Column(name="created_at",nullable=false) private Instant createdAt;
 protected ApiIdempotencyKeyEntity(){} public ApiIdempotencyKeyEntity(UUID org,UUID actor,String operation,String key,String hash){id=UUID.randomUUID();organizationId=org;actorUserId=actor;this.operation=operation;idempotencyKey=key;requestHash=hash;expiresAt=Instant.now().plus(Duration.ofDays(90));} @PrePersist void created(){createdAt=Instant.now();}
 public UUID getResultResourceId(){return resultResourceId;} public String getRequestHash(){return requestHash;} public void complete(String type,UUID id,int status){resultResourceType=type;resultResourceId=id;responseStatus=status;}
}
