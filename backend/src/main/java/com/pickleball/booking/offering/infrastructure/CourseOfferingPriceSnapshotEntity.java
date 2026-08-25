package com.pickleball.booking.offering.infrastructure;

import com.pickleball.booking.offering.domain.CourseOfferingPriceSnapshot;
import com.pickleball.booking.offering.domain.OfferingPriceSnapshotStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="course_offering_price_snapshots")
public class CourseOfferingPriceSnapshotEntity {
    @Id private UUID id;
    @Column(name="organization_id",nullable=false) private UUID organizationId;
    @Column(name="course_offering_id",nullable=false) private UUID courseOfferingId;
    @Column(name="version_no",nullable=false) private int versionNo;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private OfferingPriceSnapshotStatus status;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable=false,columnDefinition="char(3)") private String currency;
    @Column(name="price_per_participant",nullable=false,precision=12,scale=2) private BigDecimal pricePerParticipant;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="rule_trace",nullable=false,columnDefinition="jsonb") private Map<String,Object> ruleTrace;
    @Column(name="confirmed_by") private UUID confirmedBy;
    @Column(name="confirmed_at") private Instant confirmedAt;
    @Column(name="created_by") private UUID createdBy;
    @Column(name="created_at",nullable=false) private Instant createdAt;

    protected CourseOfferingPriceSnapshotEntity() { }
    public CourseOfferingPriceSnapshotEntity(CourseOfferingPriceSnapshot snapshot){id=snapshot.id(); organizationId=snapshot.organizationId(); courseOfferingId=snapshot.courseOfferingId(); apply(snapshot);}
    @PrePersist void prePersist(){if(createdAt==null) createdAt=Instant.now();}
    public void apply(CourseOfferingPriceSnapshot snapshot){versionNo=snapshot.versionNo(); status=snapshot.status(); currency=snapshot.currency(); pricePerParticipant=snapshot.pricePerParticipant(); ruleTrace=new LinkedHashMap<>(snapshot.ruleTrace()); confirmedBy=snapshot.confirmedBy(); confirmedAt=snapshot.confirmedAt(); createdBy=snapshot.createdBy();}
    public CourseOfferingPriceSnapshot toDomain(){return CourseOfferingPriceSnapshot.rehydrate(id,organizationId,courseOfferingId,versionNo,currency,pricePerParticipant,ruleTrace,createdBy,status,confirmedBy,confirmedAt);}
    public UUID getId(){return id;} public UUID getOrganizationId(){return organizationId;} public UUID getCourseOfferingId(){return courseOfferingId;} public int getVersionNo(){return versionNo;}
    public OfferingPriceSnapshotStatus getStatus(){return status;} public String getCurrency(){return currency;} public BigDecimal getPricePerParticipant(){return pricePerParticipant;}
}
