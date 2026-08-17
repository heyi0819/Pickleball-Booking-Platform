package com.pickleball.booking.organization.infrastructure;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrganizationRepository extends JpaRepository<OrganizationEntity, java.util.UUID> { Optional<OrganizationEntity> findByCode(String code); }
