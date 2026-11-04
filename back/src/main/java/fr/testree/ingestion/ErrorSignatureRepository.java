package fr.testree.ingestion;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErrorSignatureRepository extends JpaRepository<ErrorSignature, UUID> {

    /** La signature déjà connue de ce projet pour ce message nettoyé (index {@code uk_error_signature_hash}). */
    Optional<ErrorSignature> findByProjectIdAndHash(UUID projectId, String hash);
}
