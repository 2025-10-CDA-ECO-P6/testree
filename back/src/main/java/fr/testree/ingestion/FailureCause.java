package fr.testree.ingestion;

/**
 * « C'est mon test ou l'appli ? » : la cause d'une signature d'erreur, posée par un testeur (étiquette, V1) ou devinée
 * par les règles (V1.5), ADR-023. Contraintes {@code ck_error_signature_label} et {@code ck_error_signature_guess}.
 */
public enum FailureCause {
    APPLI,
    TEST,
    ENVIRONNEMENT
}
