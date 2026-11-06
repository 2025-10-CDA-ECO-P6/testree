package fr.testree.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Un fichier du dépôt de tests modifié depuis le build précédent, envoyé par le curl de Jenkins (table
 * {@code changed_file}). Volontairement sans lien vers le script : un fichier modifié peut être un sous-script
 * (MCD § 5).
 */
@Entity
@Table(name = "changed_file")
public class ChangedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_run_id", nullable = false)
    private TestRun testRun;

    @Column(name = "path", nullable = false, length = 500)
    private String path;

    protected ChangedFile() {
    }

    public ChangedFile(TestRun testRun, String path) {
        this.testRun = testRun;
        this.path = path;
    }

    public UUID getId() {
        return id;
    }

    public TestRun getTestRun() {
        return testRun;
    }

    public String getPath() {
        return path;
    }
}
