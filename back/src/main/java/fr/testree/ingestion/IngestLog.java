package fr.testree.ingestion;

import fr.testree.project.Project;
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
import org.hibernate.annotations.Generated;

/**
 * Une ligne du journal d'ingestion, écrite pour chaque envoi, même refusé (table {@code ingest_log}). Jamais de token
 * ici (OWASP A09) : un envoi au token inconnu est noté sans projet.
 */
@Entity
@Table(name = "ingest_log")
public class IngestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    /** Vide quand le token n'a désigné aucun projet. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Generated
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    /** {@code smallint} en base : un {@code short} côté Java, sinon Hibernate refuse le schéma au démarrage. */
    @Column(name = "http_status", nullable = false)
    private short httpStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private IngestOutcome outcome;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "job_name", length = 300)
    private String jobName;

    @Column(name = "build_number")
    private Integer buildNumber;

    protected IngestLog() {
    }

    /**
     * @param project     projet désigné par le token, ou {@code null} si le token est inconnu
     * @param httpStatus  code HTTP renvoyé à Jenkins (la base le borne entre 100 et 599)
     * @param outcome     issue de l'envoi
     * @param reason      raison lisible d'un refus ou d'un envoi vide, sinon {@code null}
     * @param jobName    job Jenkins, ou {@code null} si l'envoi a été refusé avant d'être lu
     * @param buildNumber numéro du build, ou {@code null} dans le même cas
     */
    public IngestLog(Project project, int httpStatus, IngestOutcome outcome, String reason, String jobName,
                     Integer buildNumber) {
        this.project = project;
        this.httpStatus = (short) httpStatus;
        this.outcome = outcome;
        this.reason = reason;
        this.jobName = jobName;
        this.buildNumber = buildNumber;
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public IngestOutcome getOutcome() {
        return outcome;
    }

    public String getReason() {
        return reason;
    }

    public String getJobName() {
        return jobName;
    }

    public Integer getBuildNumber() {
        return buildNumber;
    }
}
