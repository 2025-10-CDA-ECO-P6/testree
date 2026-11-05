package fr.testree.ingestion;

/**
 * Ce qui identifie une cause d'échec (ADR-023, règles N1 à N8) : l'action ATS, l'emplacement sans numéro de ligne,
 * le code d'erreur, le message nettoyé, et leur empreinte SHA-256. Action, emplacement et code sont vides (null)
 * pour un message qui n'est pas au format ATS (règle N8).
 */
public record SignatureParts(String action, String location, String errorCode, String normalizedText, String hash) {
}
