package fr.testree.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.testree.TestcontainersConfiguration;
import fr.testree.project.Project;
import fr.testree.project.ProjectRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les entités de l'ingestion, enregistrées puis relues sur un vrai PostgreSQL 18. Données inspirées du XML anonymisé
 * ({@code testng/testng-results-anonymise.xml}). Chaque test est annulé à la fin : la base est partagée.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class IngestionRepositoriesTest {

    private static final Instant STARTED_AT = Instant.parse("2026-10-06T19:50:05Z");
    private static final String SCRIPT_PATH = "appli.module_A.sous_module_C.sous_module_F.Script_10";
    private static final String SIGNATURE_HASH = "d".repeat(64);
    private static final String PROJECT_A = "Projet A";
    private static final String PROJECT_B = "Projet B";

    private final ProjectRepository projects;
    private final ScriptRepository scripts;
    private final TestRunRepository runs;
    private final ErrorSignatureRepository signatures;
    private final RunResultRepository results;
    private final ChangedFileRepository changedFiles;
    private final EntityManager entityManager;

    @Autowired
    IngestionRepositoriesTest(ProjectRepository projects, ScriptRepository scripts, TestRunRepository runs,
                              ErrorSignatureRepository signatures, RunResultRepository results,
                              ChangedFileRepository changedFiles, EntityManager entityManager) {
        this.projects = projects;
        this.scripts = scripts;
        this.runs = runs;
        this.signatures = signatures;
        this.results = results;
        this.changedFiles = changedFiles;
        this.entityManager = entityManager;
    }

    @Test
    @DisplayName("Un build complet (script, résultat en échec, signature, fichier modifié) se relit à l'identique")
    void fullBuildIsSavedAndReadBack() {
        Project project = project(PROJECT_A);
        TestRun run = newRun(project, 1);
        run.setGitInfo("a1b2c3d", "9f8e7d6", "master");
        runs.save(run);
        Script script = scripts.save(new Script(project, SCRIPT_PATH, STARTED_AT));
        ErrorSignature signature = signatures.save(new ErrorSignature(project, SIGNATURE_HASH, "ActionMouseKey", "-1",
                "<script>", "element not found: BUTTON [text = Libellé_X] after <n> ms", STARTED_AT));
        RunResult result = new RunResult(run, script, RunStatus.FAIL, STARTED_AT, 85_077);
        result.recordFailure("element not found: BUTTON [text = Libellé_X] after 19320 ms", SCRIPT_PATH + ":6",
                SCRIPT_PATH + ".executeActions(Script_10.java:92)", signature);
        result.setStepsDone(2);
        UUID resultId = results.save(result).getId();
        changedFiles.save(new ChangedFile(run, "appli/module_A/sous_module_C/sous_module_F/Script_10.ats"));
        entityManager.flush();
        entityManager.clear(); // oublie les objets en mémoire : la suite relit vraiment la base

        RunResult reloaded = results.findById(resultId).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(RunStatus.FAIL);
        assertThat(reloaded.getErrorLocation()).isEqualTo(SCRIPT_PATH + ":6");
        assertThat(reloaded.getStepsDone()).isEqualTo(2);
        assertThat(reloaded.getScript().getFullPath()).isEqualTo(SCRIPT_PATH);
        assertThat(reloaded.getTestRun().getGitPreviousCommit()).isEqualTo("9f8e7d6");
        assertThat(reloaded.getTestRun().getReceivedAt()).isNotNull();
        assertThat(reloaded.getSignature().getOccurrenceCount()).isEqualTo(1);
        assertThat(changedFiles.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Les statuts sont stockés en texte, comme l'exigent les CHECK de la base")
    void enumsAreStoredAsText() {
        Project project = project(PROJECT_A);
        TestRun run = runs.save(newRun(project, 1));
        Script script = scripts.save(new Script(project, SCRIPT_PATH, STARTED_AT));
        UUID resultId = results.saveAndFlush(new RunResult(run, script, RunStatus.SKIP, STARTED_AT, 0)).getId();

        Object status = entityManager.createNativeQuery("SELECT status FROM run_result WHERE id = ?1")
                .setParameter(1, resultId)
                .getSingleResult();

        assertThat(status).isEqualTo("SKIP");
    }

    @Test
    @DisplayName("Le même build envoyé deux fois est refusé : l'ingestion en fera un « déjà reçu » (ADR-026)")
    void duplicateBuildIsRejected() {
        Project project = project(PROJECT_A);
        runs.saveAndFlush(newRun(project, 7));
        TestRun again = newRun(project, 7);

        assertThatThrownBy(() -> runs.saveAndFlush(again)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Les scripts d'un projet ne remontent jamais ceux d'un autre projet")
    void scriptsAreIsolatedByProject() {
        Project projectA = project(PROJECT_A);
        Project projectB = project(PROJECT_B);
        scripts.save(new Script(projectA, SCRIPT_PATH, STARTED_AT));
        scripts.save(new Script(projectB, SCRIPT_PATH, STARTED_AT));
        scripts.save(new Script(projectB, "appli.module_A.Script_01", STARTED_AT));

        assertThat(scripts.findByProjectId(projectA.getId()))
                .extracting(script -> script.getProject().getId())
                .containsExactly(projectA.getId());
    }

    @Test
    @DisplayName("Une signature se retrouve par projet et empreinte : la même erreur compte à part dans chaque projet")
    void signatureIsFoundByProjectAndHash() {
        Project projectA = project(PROJECT_A);
        Project projectB = project(PROJECT_B);
        signatures.save(new ErrorSignature(projectA, SIGNATURE_HASH, null, null, null, "texte A", STARTED_AT));
        signatures.save(new ErrorSignature(projectB, SIGNATURE_HASH, null, null, null, "texte B", STARTED_AT));

        assertThat(signatures.findByProjectIdAndHash(projectB.getId(), SIGNATURE_HASH))
                .map(ErrorSignature::getNormalizedText)
                .hasValue("texte B");
        assertThat(signatures.findByProjectIdAndHash(projectA.getId(), "e".repeat(64))).isEmpty();
    }

    private Project project(String name) {
        // Empreinte factice mais unique par projet (contrainte uk_project_token_hash).
        String tokenHash = (name.replace(" ", "") + "0".repeat(64)).substring(0, 64);
        return projects.save(new Project(name, tokenHash));
    }

    private static TestRun newRun(Project project, int buildNumber) {
        BuildKey key = new BuildKey("nightly", buildNumber, "TNR-Suite_A", STARTED_AT);
        return new TestRun(project, key, "https://jenkins.example/job/nightly/" + buildNumber + "/", 1_903_702, 104_405);
    }
}
