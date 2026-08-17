package com.pickleball.booking.identity.infrastructure;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentityEntity, java.util.UUID> { Optional<ExternalIdentityEntity> findByProviderAndProviderSubjectAndRevokedAtIsNull(String provider, String providerSubject); }
