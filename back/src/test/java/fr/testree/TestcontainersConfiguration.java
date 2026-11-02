package fr.testree;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base des tests : un vrai PostgreSQL 18 jetable, lancé dans Docker au début des tests puis supprimé.
 *
 * <p>Même version qu'en développement (docker-compose.yml) : les tests tournent sur le vrai moteur,
 * avec uuidv7(), l'index sur lower(email) et les CHECK propres à PostgreSQL (ADR-032). Une base en
 * mémoire comme H2 ne connaît pas ces fonctions. {@code @ServiceConnection} branche la datasource
 * de Spring sur ce conteneur, sans configuration à écrire.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    JdbcDatabaseContainer<?> postgresContainer() {
        return new PostgreSQLContainer("postgres:18");
    }
}
