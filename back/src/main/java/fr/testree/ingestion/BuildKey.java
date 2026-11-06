package fr.testree.ingestion;

import java.time.Instant;

/**
 * Ce qui identifie un build reçu, avec le projet : c'est la clé de doublon d'ADR-026 (contrainte
 * {@code uk_test_run_build}). Le même build envoyé deux fois est refusé par la base.
 *
 * @param jobName     nom du job Jenkins
 * @param buildNumber numéro du build Jenkins
 * @param suiteName   suite TestNG lue dans le XML
 * @param startedAt   début de la suite, lu dans le XML
 */
public record BuildKey(String jobName, int buildNumber, String suiteName, Instant startedAt) {
}
