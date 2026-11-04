package fr.testree.ingestion;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestRunRepository extends JpaRepository<TestRun, UUID> {
}
