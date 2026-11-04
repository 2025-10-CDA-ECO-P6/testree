package fr.testree.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** La brique atomique : le résultat d'un script dans un build (table {@code run_result}, MLD). */
@Entity
@Table(name = "run_result")
public class RunResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_run_id", nullable = false)
    private TestRun testRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "script_id", nullable = false)
    private Script script;

    /** Stocké en texte ({@code 'FAIL'}), jamais en numéro d'ordre : ajouter une valeur ne décale rien (ADR-032). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private RunStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /** {@code script:ligne}, avec la ligne : sert au lien qui ouvre Agilitest (ADR-027). */
    @Column(name = "error_location", length = 500)
    private String errorLocation;

    /** Nombre d'appels de sous-scripts faits par le script avant de s'arrêter (sa progression). */
    @Column(name = "steps_done")
    private Integer stepsDone;

    /** Pile d'appel ATS filtrée, 10 lignes au plus, pour les échecs seulement (ADR-027). */
    @Column(name = "ats_call_stack")
    private String atsCallStack;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signature_id")
    private ErrorSignature signature;

    protected RunResult() {
    }

    public RunResult(TestRun testRun, Script script, RunStatus status, Instant startedAt, long durationMs) {
        this.testRun = testRun;
        this.script = script;
        this.status = status;
        this.startedAt = startedAt;
        this.durationMs = durationMs;
    }

    /**
     * Rattache la cause d'un échec. Un résultat vert n'en a jamais : la base le refuse
     * ({@code ck_run_result_pass_no_signature}), le code aussi, pour que l'erreur soit claire dès l'appel.
     */
    public void recordFailure(String errorMessage, String errorLocation, String atsCallStack,
                              ErrorSignature signature) {
        if (status == RunStatus.PASS) {
            throw new IllegalStateException("Un résultat PASS n'a pas de cause d'échec");
        }
        this.errorMessage = errorMessage;
        this.errorLocation = errorLocation;
        this.atsCallStack = atsCallStack;
        this.signature = signature;
    }

    public void setStepsDone(Integer stepsDone) {
        this.stepsDone = stepsDone;
    }

    public UUID getId() {
        return id;
    }

    public TestRun getTestRun() {
        return testRun;
    }

    public Script getScript() {
        return script;
    }

    public RunStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getErrorLocation() {
        return errorLocation;
    }

    public Integer getStepsDone() {
        return stepsDone;
    }

    public String getAtsCallStack() {
        return atsCallStack;
    }

    public ErrorSignature getSignature() {
        return signature;
    }
}
