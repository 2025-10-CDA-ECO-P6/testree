package fr.testree.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nettoie un message d'échec pour reconnaître la même panne d'un test ATS à l'autre et d'un jour à l'autre
 * (ADR-023, dictionnaire de données § 6, règles N1 à N8 appliquées dans cet ordre). Java pur, sans état.
 */
public class SignatureService {

    /** Longueur maximale du message nettoyé avant le calcul de l'empreinte (N7, colonne {@code normalized_text}). */
    public static final int MAX_NORMALIZED_LENGTH = 1000;

    /** Longueur maximale du message lu : borne le temps de calcul, même sur un message énorme (S07, DIA-07.4). */
    static final int MAX_INPUT_LENGTH = 4 * MAX_NORMALIZED_LENGTH;

    /** Emplacement remplacé quand l'erreur est dans le test ATS lui-même, et pas dans un sous-script (N3). */
    static final String SELF_LOCATION = "<script>";

    /** Format ATS (N2) : {@code ActionMouseKey (appli.module_A.Script_10:6) -> {…JSON…}}. */
    private static final Pattern ATS_MESSAGE =
            Pattern.compile("^(\\w+) \\(([\\p{L}\\p{N}_$.]+):\\d+\\) -> (\\{.*})$");
    private static final Pattern JSON_ERROR_CODE = Pattern.compile("\"errorCode\"\\s*:\\s*(-?\\d+)");
    private static final Pattern JSON_ERROR_MESSAGE = Pattern.compile("\"errorMessage\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern DURATION = Pattern.compile("after \\d+ ms");
    private static final Pattern BRACKETED_NUMBER = Pattern.compile("\\[\\d+]");
    private static final Pattern DATE = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    private static final Pattern TIME = Pattern.compile("\\b\\d{1,2}:\\d{2}(:\\d{2})?\\b");
    private static final Pattern UUID_TEXT =
            Pattern.compile("\\b\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}\\b");
    private static final Pattern HEX_ID = Pattern.compile("@\\p{XDigit}{6,}");
    /** Nombre isolé de 2 chiffres ou plus ; pas ceux collés à un mot ({@code elevation-2}, {@code Script_10}). */
    private static final Pattern ISOLATED_NUMBER = Pattern.compile("(?<![\\w-])\\d{2,}(?![\\w-])");

    /**
     * @param message  message d'échec lu dans le fichier de résultats
     * @param testPath chemin du test ATS qui a échoué, pour reconnaître une erreur « dans le script lui-même » (N3)
     */
    public SignatureParts sign(String message, String testPath) {
        String bounded = message.length() <= MAX_INPUT_LENGTH ? message : message.substring(0, MAX_INPUT_LENGTH);
        String collapsed = collapseWhitespace(bounded);
        Matcher ats = ATS_MESSAGE.matcher(collapsed);
        if (!ats.matches()) {
            String firstLine = bounded.strip().lines().findFirst().orElse("");
            return parts(null, null, null, collapseWhitespace(firstLine));
        }
        String action = ats.group(1);
        String location = ats.group(2).equals(testPath) ? SELF_LOCATION : ats.group(2);
        String json = ats.group(3);
        Matcher code = JSON_ERROR_CODE.matcher(json);
        Matcher text = JSON_ERROR_MESSAGE.matcher(json);
        return parts(action, location, code.find() ? code.group(1) : null, text.find() ? unescape(text.group(1)) : json);
    }

    private static SignatureParts parts(String action, String location, String errorCode, String text) {
        String normalized = truncate(normalize(text));
        String key = String.join("|", orEmpty(action), orEmpty(location), orEmpty(errorCode), normalized);
        return new SignatureParts(action, location, errorCode, normalized, sha256(key));
    }

    /** N4 à N6, dans cet ordre : durées, nombres entre crochets, dates, heures, identifiants, nombres isolés. */
    static String normalize(String text) {
        String result = DURATION.matcher(text).replaceAll("after <n> ms");
        result = BRACKETED_NUMBER.matcher(result).replaceAll("[<n>]");
        result = DATE.matcher(result).replaceAll("<date>");
        result = TIME.matcher(result).replaceAll("<heure>");
        result = UUID_TEXT.matcher(result).replaceAll("<id>");
        result = HEX_ID.matcher(result).replaceAll("@<id>");
        return ISOLATED_NUMBER.matcher(result).replaceAll("<n>");
    }

    private static String collapseWhitespace(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }

    private static String truncate(String text) {
        return text.length() <= MAX_NORMALIZED_LENGTH ? text : text.substring(0, MAX_NORMALIZED_LENGTH);
    }

    /** Enlève l'échappement JSON des guillemets et des barres obliques inverses dans le message ATS. */
    private static String unescape(String jsonString) {
        return jsonString.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est présent dans toute JVM (exigé par la spécification Java) : ce cas n'arrive pas.
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
