package com.pickleball.booking.organization.infrastructure;
import java.util.Optional;
import java.util.List;
import com.pickleball.booking.organization.domain.OrganizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrganizationRepository extends JpaRepository<OrganizationEntity, java.util.UUID> { Optional<OrganizationEntity> findByCode(String code); List<OrganizationEntity> findByStatusOrderByNameAsc(OrganizationStatus status); }
