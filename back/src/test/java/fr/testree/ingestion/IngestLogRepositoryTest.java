package fr.testree.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import fr.testree.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** Le journal d'ingestion sur un vrai PostgreSQL 18 (même conteneur que les autres classes de test). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class IngestLogRepositoryTest {

    private final IngestLogRepository ingestLogs;

    IngestLogRepositoryTest(@Autowired IngestLogRepository ingestLogs) {
        this.ingestLogs = ingestLogs;
    }

    @Test
    @DisplayName("Un envoi au token inconnu est journalisé sans projet, avec l'heure de la base")
    void refusedIngestIsLoggedWithoutProject() {
        IngestLog log = ingestLogs.saveAndFlush(
                new IngestLog(null, 401, IngestOutcome.REFUSE, "Token inconnu", null, null));

        assertThat(log.getId()).isNotNull();
        assertThat(log.getReceivedAt()).isNotNull();
        assertThat(log.getHttpStatus()).isEqualTo(401);
        assertThat(log.getProject()).isNull();
    }
}
