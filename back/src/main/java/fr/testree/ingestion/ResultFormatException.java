package fr.testree.ingestion;

/**
 * Le fichier de résultats est refusé (mal formé, tronqué, piégé, pas TestNG…). Rien ne doit être enregistré ; la couche
 * web répond 400 avec ce message (séquence d'ingestion, règles L01 à L04, L13, L15 du dictionnaire de données).
 */
public class ResultFormatException extends RuntimeException {

    public ResultFormatException(String message) {
        super(message);
    }

    public ResultFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
