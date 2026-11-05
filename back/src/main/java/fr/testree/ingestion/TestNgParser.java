package fr.testree.ingestion;

import java.io.InputStream;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Lit le fichier testng-results.xml envoyé par Jenkins, en flux (StAX) : une seule boucle sur les événements, et une
 * mémoire de ce qui a été vu avant (suite et test ATS en cours). Les règles viennent du dictionnaire de données (§ 9).
 *
 * <p>Sécurité (XXE, règle L13) : les DTD sont interdites et les entités externes jamais résolues. Un
 * {@code testng-results.xml} n'a jamais de DTD : on peut toutes les refuser.
 */
public class TestNgParser {

    /** Longueur maximale du message d'erreur gardé (règle L12, colonne {@code run_result.error_message}). */
    public static final int MAX_MESSAGE_LENGTH = 2000;

    /** Nombre maximal de lignes ATS gardées dans la pile d'appels (dictionnaire X23). */
    static final int MAX_ATS_STACK_LINES = 10;

    private static final String ROOT = "testng-results";
    private static final String SUITE = "suite";
    private static final String CLASS = "class";
    private static final String TEST_METHOD = "test-method";
    private static final String MESSAGE = "message";
    private static final String FULL_STACKTRACE = "full-stacktrace";
    private static final String REPORTER_OUTPUT = "reporter-output";
    private static final String NAME = "name";
    private static final String STARTED_AT = "started-at";

    /** Dates TestNG, ex. {@code 2026-10-06T21:50:05 CEST} : pas au format ISO, le fuseau est une abréviation. */
    private static final DateTimeFormatter TESTNG_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss z", Locale.ENGLISH);

    /** Nom d'un test ATS = nom de classe Java pointé : lettres (accents compris), chiffres, {@code _}, {@code $} (L15). */
    private static final Pattern VALID_PATH = Pattern.compile("[\\p{L}\\p{N}_$]+(\\.[\\p{L}\\p{N}_$]+)*");

    /** Emplacement {@code (script:ligne)} dans un message ATS, ex. {@code (appli.module_A.Script_10:6)} (X22b). */
    private static final Pattern ERROR_LOCATION = Pattern.compile("\\(([\\p{L}\\p{N}_$.]+:\\d+)\\)");

    private final XMLInputFactory factory;

    public TestNgParser() {
        factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
    }

    /**
     * Lit tout le fichier. Un fichier refusé lève {@link ResultFormatException} : il ne doit rien en rester.
     *
     * @return une entrée par suite, dans l'ordre du fichier (règle L08)
     */
    public List<ParsedSuite> parse(InputStream xml) {
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(xml);
            try {
                return readDocument(reader);
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new ResultFormatException("Fichier de résultats illisible : " + e.getMessage(), e);
        }
    }

    private List<ParsedSuite> readDocument(XMLStreamReader reader) throws XMLStreamException {
        List<ParsedSuite> suites = new ArrayList<>();
        boolean rootSeen = false;
        boolean rootClosed = false;
        SuiteInProgress suite = null;
        String currentPath = null;
        TestInProgress test = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.DTD) {
                throw new ResultFormatException("Fichier refusé : une déclaration DTD n'est pas autorisée");
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                String element = reader.getLocalName();
                if (!rootSeen) {
                    if (!ROOT.equals(element)) {
                        throw new ResultFormatException("Ce n'est pas un résultat TestNG (racine <" + element + ">)");
                    }
                    rootSeen = true;
                }
                switch (element) {
                    case REPORTER_OUTPUT -> skipElement(reader);
                    case SUITE -> suite = new SuiteInProgress(reader.getAttributeValue(null, NAME),
                            parseDate(reader.getAttributeValue(null, STARTED_AT)));
                    case CLASS -> currentPath = validPath(reader.getAttributeValue(null, NAME));
                    case TEST_METHOD -> test = startTest(reader, currentPath);
                    case MESSAGE -> {
                        if (test != null) {
                            test.message = truncate(reader.getElementText().strip());
                        }
                    }
                    case FULL_STACKTRACE -> {
                        if (test != null) {
                            test.atsCallStack = atsLines(reader.getElementText());
                        }
                    }
                    default -> {
                        // balise sans information utile (groups, test, exception, params…)
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                String element = reader.getLocalName();
                if (TEST_METHOD.equals(element) && test != null) {
                    if (!test.isConfig && suite != null) {
                        suite.add(test.toParsedTest());
                    }
                    test = null;
                } else if (SUITE.equals(element) && suite != null) {
                    suites.add(suite.toParsedSuite());
                    suite = null;
                } else if (ROOT.equals(element)) {
                    rootClosed = true;
                }
            }
        }
        if (!rootClosed) {
            throw new ResultFormatException("Fichier de résultats incomplet : </" + ROOT + "> manquant");
        }
        return suites;
    }

    private TestInProgress startTest(XMLStreamReader reader, String currentPath) {
        TestInProgress test = new TestInProgress();
        test.isConfig = "true".equals(reader.getAttributeValue(null, "is-config"));
        test.path = currentPath;
        test.status = parseStatus(reader.getAttributeValue(null, "status"));
        test.startedAt = parseDate(reader.getAttributeValue(null, STARTED_AT));
        test.durationMs = parseDuration(reader.getAttributeValue(null, "duration-ms"));
        return test;
    }

    /** Avance jusqu'à la fermeture de l'élément courant, en ignorant tout son contenu (reporter-output : ~57 % du fichier). */
    private static void skipElement(XMLStreamReader reader) throws XMLStreamException {
        int depth = 1;
        while (depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    private static String validPath(String rawPath) {
        if (rawPath == null) {
            throw new ResultFormatException("Une balise <class> n'a pas de nom");
        }
        String path = Normalizer.normalize(rawPath, Normalizer.Form.NFC);
        if (!VALID_PATH.matcher(path).matches()) {
            throw new ResultFormatException("Nom de test ATS invalide : « " + rawPath + " »");
        }
        return path;
    }

    private static RunStatus parseStatus(String status) {
        if (status == null) {
            throw new ResultFormatException("Une balise <test-method> n'a pas de statut");
        }
        try {
            return RunStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new ResultFormatException("Statut inconnu : « " + status + " » (attendu : PASS, FAIL ou SKIP)", e);
        }
    }

    /** Date TestNG, ou {@code null} si elle est absente ou illisible : le service prendra l'heure de réception (L17). */
    private static Instant parseDate(String date) {
        if (date == null) {
            return null;
        }
        try {
            return ZonedDateTime.parse(date, TESTNG_DATE).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static long parseDuration(String durationMs) {
        try {
            return durationMs == null ? 0 : Long.parseLong(durationMs);
        } catch (NumberFormatException e) {
            throw new ResultFormatException("Durée illisible : « " + durationMs + " »", e);
        }
    }

    private static String truncate(String message) {
        return message.length() <= MAX_MESSAGE_LENGTH ? message : message.substring(0, MAX_MESSAGE_LENGTH);
    }

    /** Garde seulement les lignes {@code at <test ATS>.executeActions(…)} : le reste de la pile est du bruit (X23). */
    private static String atsLines(String stacktrace) {
        String lines = stacktrace.lines()
                .map(String::strip)
                .filter(line -> line.startsWith("at ") && line.contains(".executeActions("))
                .limit(MAX_ATS_STACK_LINES)
                .collect(Collectors.joining("\n"));
        return lines.isEmpty() ? null : lines;
    }

    private static String errorLocation(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = ERROR_LOCATION.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Ce qu'on sait du test-method en cours de lecture ; il devient un {@link ParsedTest} à sa fermeture. */
    private static final class TestInProgress {
        private boolean isConfig;
        private String path;
        private RunStatus status;
        private Instant startedAt;
        private long durationMs;
        private String message;
        private String atsCallStack;

        private ParsedTest toParsedTest() {
            boolean failed = status == RunStatus.FAIL;
            return new ParsedTest(path, status, startedAt, durationMs, message,
                    failed ? errorLocation(message) : null, failed ? atsCallStack : null);
        }
    }

    /**
     * La suite en cours. Un même test ATS peut apparaître deux fois (règle L09) : un seul résultat, avec le pire statut
     * (FAIL &gt; SKIP &gt; PASS), les durées additionnées, le premier début et le premier message d'échec.
     */
    private static final class SuiteInProgress {
        private final String name;
        private final Instant startedAt;
        private final Map<String, ParsedTest> tests = new LinkedHashMap<>();

        private SuiteInProgress(String name, Instant startedAt) {
            this.name = name;
            this.startedAt = startedAt;
        }

        private void add(ParsedTest test) {
            tests.merge(test.fullPath(), test, SuiteInProgress::mergeDuplicates);
        }

        private static ParsedTest mergeDuplicates(ParsedTest first, ParsedTest second) {
            ParsedTest worst = severity(second.status()) > severity(first.status()) ? second : first;
            ParsedTest firstFailure = first.errorMessage() != null ? first : second;
            Instant start = first.startedAt() != null ? first.startedAt() : second.startedAt();
            return new ParsedTest(first.fullPath(), worst.status(), start, first.durationMs() + second.durationMs(),
                    firstFailure.errorMessage(), firstFailure.errorLocation(), firstFailure.atsCallStack());
        }

        private static int severity(RunStatus status) {
            return switch (status) {
                case PASS -> 0;
                case SKIP -> 1;
                case FAIL -> 2;
            };
        }

        private ParsedSuite toParsedSuite() {
            return new ParsedSuite(name, startedAt, List.copyOf(tests.values()));
        }
    }
}
