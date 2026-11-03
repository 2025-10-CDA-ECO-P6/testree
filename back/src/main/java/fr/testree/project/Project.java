package fr.testree.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Un projet Testree : un dépôt de tests ATS dont Jenkins envoie les résultats (table {@code project}, MLD).
 *
 * <p>Le token d'ingestion n'est jamais stocké : seule son empreinte SHA-256 l'est (ADR-008).
 */
@Entity
@Table(name = "project")
public class Project {

    /**
     * UUID v7 calculé par PostgreSQL ({@code DEFAULT uuidv7()}, ADR-032). Pour Hibernate, « IDENTITY » veut dire :
     * ne pas envoyer l'identifiant dans l'INSERT, puis relire celui que la base a choisi.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** {@code char(64)} en base : sans ce type explicite, Hibernate attendrait un varchar et refuserait le schéma. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /** Valeur par défaut de la base ({@code now()}), relue après l'INSERT. */
    @Generated
    @Column(name = "token_created_at", nullable = false)
    private Instant tokenCreatedAt;

    @Generated
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Réservé à Hibernate, qui recrée l'objet à partir d'une ligne. */
    protected Project() {
    }

    public Project(String name, String tokenHash) {
        this.name = name;
        this.tokenHash = tokenHash;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getTokenCreatedAt() {
        return tokenCreatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
