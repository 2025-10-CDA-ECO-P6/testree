package fr.testree.ingestion;

import fr.testree.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;

/** Une exécution : un build Jenkins reçu par {@code POST /api/v1/ingest} (table {@code test_run}, MLD). */
@Entity
@Table(name = "test_run")
public class TestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "job_name", nullable = false, length = 300)
    private String jobName;

    @Column(name = "build_number", nullable = false)
    private int buildNumber;

    /** Doit commencer par http(s) : la base le vérifie, car ce lien est affiché dans le front (anti-XSS). */
    @Column(name = "build_url", nullable = false, length = 500)
    private String buildUrl;

    @Column(name = "suite_name", nullable = false, length = 200)
    private String suiteName;

    @Column(name = "git_commit", length = 40)
    private String gitCommit;

    @Column(name = "git_previous_commit", length = 40)
    private String gitPreviousCommit;

    @Column(name = "git_branch", length = 200)
    private String gitBranch;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    /** Heure de réception, donnée par la base : c'est elle que la purge à 60 jours regarde. */
    @Generated
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "payload_bytes", nullable = false)
    private int payloadBytes;

    /** Message d'une méthode de configuration de la suite en échec (ADR-027). */
    @Column(name = "config_error", length = 2000)
    private String configError;

    protected TestRun() {
    }

    public TestRun(Project project, BuildKey key, String buildUrl, long durationMs, int payloadBytes) {
        this.project = project;
        this.jobName = key.jobName();
        this.buildNumber = key.buildNumber();
        this.suiteName = key.suiteName();
        this.startedAt = key.startedAt();
        this.buildUrl = buildUrl;
        this.durationMs = durationMs;
        this.payloadBytes = payloadBytes;
    }

    /** Variables Git envoyées par le curl de Jenkins ; toutes facultatives (ADR-007). */
    public void setGitInfo(String commit, String previousCommit, String branch) {
        this.gitCommit = commit;
        this.gitPreviousCommit = previousCommit;
        this.gitBranch = branch;
    }

    public void setConfigError(String configError) {
        this.configError = configError;
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getJobName() {
        return jobName;
    }

    public int getBuildNumber() {
        return buildNumber;
    }

    public String getBuildUrl() {
        return buildUrl;
    }

    public String getSuiteName() {
        return suiteName;
    }

    public String getGitCommit() {
        return gitCommit;
    }

    public String getGitPreviousCommit() {
        return gitPreviousCommit;
    }

    public String getGitBranch() {
        return gitBranch;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public int getPayloadBytes() {
        return payloadBytes;
    }

    public String getConfigError() {
        return configError;
    }
}
