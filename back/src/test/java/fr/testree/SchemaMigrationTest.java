package fr.testree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * La migration Flyway V1 crée le schéma du MLD, et la base fait elle-même respecter ses règles
 * d'intégrité (MLD § 6). Chaque test est annulé à la fin (rollback) : ils ne se gênent pas entre eux.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SchemaMigrationTest {

    private final JdbcTemplate jdbc;

    SchemaMigrationTest(@Autowired JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("Flyway crée les 11 tables du MLD")
    void migrationCreatesAllTables() {
        Integer tableCount = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, Integer.class);

        assertThat(tableCount).isEqualTo(11);
    }

    @Test
    @DisplayName("Le même build envoyé deux fois est refusé par la base (ADR-026)")
    void duplicateBuildIsRejected() {
        UUID projectId = insertProject();
        insertBuild(projectId, "https://jenkins.example/job/nightly/1");

        assertThatThrownBy(() -> insertBuild(projectId, "https://jenkins.example/job/nightly/1"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("Un lien de build qui n'est pas en http(s) est refusé (protection XSS)")
    void nonHttpBuildUrlIsRejected() {
        UUID projectId = insertProject();

        assertThatThrownBy(() -> insertBuild(projectId, "javascript:alert(1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertProject() {
        return jdbc.queryForObject(
                "INSERT INTO project (name, token_hash) VALUES (?, ?) RETURNING id",
                UUID.class, "Projet de test", "a".repeat(64));
    }

    private void insertBuild(UUID projectId, String buildUrl) {
        jdbc.update("""
                INSERT INTO test_run (project_id, job_name, build_number, build_url, suite_name,
                                      started_at, duration_ms, payload_bytes)
                VALUES (?, 'nightly', 1, ?, 'Suite', '2026-11-01T21:00:00+01:00', 1000, 2048)
                """, projectId, buildUrl);
    }
}
