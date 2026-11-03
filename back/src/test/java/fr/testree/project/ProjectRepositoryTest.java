package fr.testree.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.testree.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le mapping JPA du projet sur la table du MLD. Que l'application démarre prouve déjà que chaque entité correspond à
 * sa table : Hibernate est en {@code validate} et refuse de démarrer au moindre écart.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ProjectRepositoryTest {

    private static final String NAME = "Projet A";
    private static final String TOKEN_HASH = "a".repeat(64);

    private final ProjectRepository projects;

    ProjectRepositoryTest(@Autowired ProjectRepository projects) {
        this.projects = projects;
    }

    @Test
    @DisplayName("La base attribue au projet un UUID v7 et sa date de création")
    void databaseGeneratesIdAndCreationDate() {
        Project saved = projects.saveAndFlush(new Project(NAME, TOKEN_HASH));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId().version()).isEqualTo(7);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getTokenCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Un projet se retrouve par l'empreinte de son token, un token inconnu ne donne rien")
    void findsProjectByTokenHash() {
        projects.saveAndFlush(new Project(NAME, TOKEN_HASH));

        assertThat(projects.findByTokenHash(TOKEN_HASH)).map(Project::getName).hasValue(NAME);
        assertThat(projects.findByTokenHash("b".repeat(64))).isEmpty();
    }

    @Test
    @DisplayName("Deux projets ne peuvent pas porter le même nom")
    void duplicateNameIsRejected() {
        projects.saveAndFlush(new Project(NAME, TOKEN_HASH));
        Project homonyme = new Project(NAME, "c".repeat(64));

        assertThatThrownBy(() -> projects.saveAndFlush(homonyme))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
