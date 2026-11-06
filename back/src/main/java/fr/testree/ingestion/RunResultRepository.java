package fr.testree.ingestion;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunResultRepository extends JpaRepository<RunResult, UUID> {
}
