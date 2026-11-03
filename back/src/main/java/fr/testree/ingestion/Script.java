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

/**
 * Un script ATS d'un projet, identifié par son chemin pointé (table {@code script}, MLD).
 *
 * <p>{@code failing_since} et {@code failing_run_count} survivent à la purge des résultats (ADR-013) : ce sont les seuls
 * chiffres d'historique gardés au-delà de 60 jours.
 */
@Entity
@Table(name = "script")
public class Script {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** Ex. {@code appli.module_A.Script_01} : l'arbre se reconstruit à partir de ce chemin (ADR-005). */
    @Column(name = "full_path", nullable = false, length = 500)
    private String fullPath;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "failing_since")
    private Instant failingSince;

    @Column(name = "failing_run_count", nullable = false)
    private int failingRunCount;

    protected Script() {
    }

    public Script(Project project, String fullPath, Instant firstSeenAt) {
        this.project = project;
        this.fullPath = fullPath;
        this.firstSeenAt = firstSeenAt;
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getFullPath() {
        return fullPath;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getFailingSince() {
        return failingSince;
    }

    public int getFailingRunCount() {
        return failingRunCount;
    }
}
