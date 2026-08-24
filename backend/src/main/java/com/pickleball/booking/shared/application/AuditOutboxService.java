package com.pickleball.booking.shared.application;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service;
@Service
public class AuditOutboxService {
 private final JdbcTemplate jdbc; public AuditOutboxService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void record(UUID org,UUID actor,String action,String type,UUID id,String reason){jdbc.update("insert into audit_logs(organization_id,actor_user_id,actor_type,action,entity_type,entity_id,reason,created_at) values (?,?, 'USER', ?,?,?,?, now())",org,actor,action,type,id,reason);jdbc.update("insert into outbox_events(id,organization_id,aggregate_type,aggregate_id,event_type,payload,status,attempt_count,available_at,created_at) values (?,?,?,?,?,cast('{}' as jsonb),'PENDING',0,now(),now())",UUID.randomUUID(),org,type,id,action);}
}
