package fr.testree.ingestion;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangedFileRepository extends JpaRepository<ChangedFile, UUID> {
}
