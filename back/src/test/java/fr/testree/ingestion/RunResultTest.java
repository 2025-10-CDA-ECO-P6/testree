package fr.testree.ingestion;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.testree.project.Project;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Règle portée par l'entité elle-même : test unitaire, sans Spring ni base. */
class RunResultTest {

    @Test
    @DisplayName("Un résultat PASS refuse une cause d'échec")
    void passResultRejectsFailure() {
        Instant now = Instant.parse("2026-10-06T19:50:05Z");
        Project project = new Project("Projet A", "a".repeat(64));
        TestRun run = new TestRun(project, new BuildKey("nightly", 1, "TNR-Suite_A", now),
                "https://jenkins.example/job/nightly/1/", 1_000, 2_048);
        RunResult result = new RunResult(run, new Script(project, "appli.module_A.Script_01", now),
                RunStatus.PASS, now, 1_000);

        assertThatThrownBy(() -> result.recordFailure("message", "appli.module_A.Script_01:3", null, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
