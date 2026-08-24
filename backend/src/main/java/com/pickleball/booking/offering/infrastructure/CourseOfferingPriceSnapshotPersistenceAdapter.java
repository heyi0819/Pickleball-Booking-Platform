package com.pickleball.booking.offering.infrastructure;

import com.pickleball.booking.offering.domain.*;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class CourseOfferingPriceSnapshotPersistenceAdapter implements CourseOfferingPriceSnapshotRepository {
    private final CourseOfferingPriceSnapshotJpaRepository snapshots;
    public CourseOfferingPriceSnapshotPersistenceAdapter(CourseOfferingPriceSnapshotJpaRepository snapshots){this.snapshots=snapshots;}
    @Override public Optional<CourseOfferingPriceSnapshot> findById(UUID snapshotId){return snapshots.findById(snapshotId).map(CourseOfferingPriceSnapshotEntity::toDomain);}
    public Optional<CourseOfferingPriceSnapshot> findLockedById(UUID snapshotId){return snapshots.findLockedById(snapshotId).map(CourseOfferingPriceSnapshotEntity::toDomain);}
    @Override public Optional<CourseOfferingPriceSnapshot> findConfirmedByOfferingId(UUID offeringId){return snapshots.findByCourseOfferingIdAndStatus(offeringId,OfferingPriceSnapshotStatus.CONFIRMED).map(CourseOfferingPriceSnapshotEntity::toDomain);}
    public int nextVersion(UUID offeringId){return snapshots.findTopByCourseOfferingIdOrderByVersionNoDesc(offeringId).map(s -> s.getVersionNo()+1).orElse(1);}
    @Override public CourseOfferingPriceSnapshot save(CourseOfferingPriceSnapshot snapshot){var entity=snapshots.findById(snapshot.id()).orElseGet(() -> new CourseOfferingPriceSnapshotEntity(snapshot)); entity.apply(snapshot); snapshots.save(entity); return snapshot;}
}
