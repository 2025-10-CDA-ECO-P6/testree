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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Une cause d'échec reconnue d'un build à l'autre : le message ATS nettoyé, puis haché (table
 * {@code error_signature}, ADR-023). Elle survit à la purge de ses résultats : c'est l'historique des pannes.
 */
@Entity
@Table(name = "error_signature")
public class ErrorSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** SHA-256 du message nettoyé (règle N7), unique par projet. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "hash", nullable = false, length = 64)
    private String hash;

    @Column(name = "action", length = 100)
    private String action;

    @Column(name = "error_code", length = 20)
    private String errorCode;

    /** Emplacement sans le numéro de ligne, pour reconnaître la même panne quand la ligne bouge. */
    @Column(name = "location", length = 500)
    private String location;

    @Column(name = "normalized_text", nullable = false, length = 1000)
    private String normalizedText;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount;

    // Étiquette posée par un testeur depuis le panneau du script, et devinette des règles (V1.5).
    @Enumerated(EnumType.STRING)
    @Column(name = "label", length = 20)
    private FailureCause label;

    /** Auteur de l'étiquette ; deviendra une relation vers l'utilisateur quand l'entité des comptes existera. */
    @Column(name = "label_by")
    private UUID labelBy;

    @Column(name = "label_at")
    private Instant labelAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "guess", length = 20)
    private FailureCause guess;

    @Column(name = "guess_reason", length = 500)
    private String guessReason;

    protected ErrorSignature() {
    }

    /** Première apparition d'une signature : vue une fois, à {@code seenAt}. */
    public ErrorSignature(Project project, String hash, String action, String errorCode, String location,
                          String normalizedText, Instant seenAt) {
        this.project = project;
        this.hash = hash;
        this.action = action;
        this.errorCode = errorCode;
        this.location = location;
        this.normalizedText = normalizedText;
        this.firstSeenAt = seenAt;
        this.lastSeenAt = seenAt;
        this.occurrenceCount = 1;
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getHash() {
        return hash;
    }

    public String getAction() {
        return action;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getLocation() {
        return location;
    }

    public String getNormalizedText() {
        return normalizedText;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public int getOccurrenceCount() {
        return occurrenceCount;
    }

    public FailureCause getLabel() {
        return label;
    }

    public UUID getLabelBy() {
        return labelBy;
    }

    public Instant getLabelAt() {
        return labelAt;
    }

    public FailureCause getGuess() {
        return guess;
    }

    public String getGuessReason() {
        return guessReason;
    }
}
