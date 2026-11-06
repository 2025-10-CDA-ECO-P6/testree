package fr.testree;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
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

    /**
     * Un seul conteneur pour toutes les classes de test. Spring crée un contexte par configuration de test différente
     * (avec ou sans MockMvc, par exemple) et chaque contexte appelait cette méthode : un PostgreSQL de plus à chaque
     * fois. Le champ {@code static} fait que tous les contextes reçoivent le même ; le démarrer quand il tourne déjà
     * ne fait rien. Conséquence : la base est partagée, chaque test doit annuler ce qu'il écrit ({@code @Transactional}).
     */
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }
}
