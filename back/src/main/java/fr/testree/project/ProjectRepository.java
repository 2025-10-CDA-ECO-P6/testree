package fr.testree.project;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /** Retrouve le projet d'un envoi Jenkins à partir de l'empreinte du token reçu (index {@code uk_project_token_hash}). */
    Optional<Project> findByTokenHash(String tokenHash);
}
