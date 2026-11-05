package fr.testree.ingestion;

import java.time.Instant;

/**
 * Un test ATS lu dans le fichier de résultats TestNG (une méthode métier, jamais une méthode de configuration).
 *
 * @param fullPath     chemin pointé du test ATS, ex. {@code appli.module_A.Script_01} (attribut {@code class@name})
 * @param status       statut TestNG
 * @param startedAt    début du test, ou {@code null} si la date est illisible (règle L17)
 * @param durationMs   durée en millisecondes
 * @param errorMessage message d'échec, tronqué à {@link TestNgParser#MAX_MESSAGE_LENGTH} caractères ; {@code null} si réussi
 * @param errorLocation emplacement {@code script:ligne} extrait du message, ou {@code null} s'il n'y en a pas
 * @param atsCallStack lignes ATS ({@code at ….executeActions(…)}) de la pile d'appels, ou {@code null}
 */
public record ParsedTest(String fullPath, RunStatus status, Instant startedAt, long durationMs, String errorMessage,
                         String errorLocation, String atsCallStack) {
}
