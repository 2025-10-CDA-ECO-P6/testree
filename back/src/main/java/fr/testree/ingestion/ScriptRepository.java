package fr.testree.ingestion;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScriptRepository extends JpaRepository<Script, UUID> {

    /** Les scripts déjà connus d'un projet, comparés à ceux du build reçu (nouveaux, absents). */
    List<Script> findByProjectId(UUID projectId);
}
