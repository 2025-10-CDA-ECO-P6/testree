package fr.testree.ingestion;

/** Issue d'un envoi, notée dans le journal d'ingestion (contrainte {@code ck_ingest_log_outcome}). */
public enum IngestOutcome {
    ACCEPTE,
    DEJA_RECU,
    VIDE,
    REFUSE
}
