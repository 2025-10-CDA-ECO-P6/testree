package fr.testree.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Parseur TestNG : cas nominaux puis règles L01 à L17 du dictionnaire de données (§ 9). */
class TestNgParserTest {

    private final TestNgParser parser = new TestNgParser();

    @Test
    @DisplayName("Un test ATS réussi est lu avec son chemin et son statut")
    void passedTestIsRead() {
        ParsedTest test = onlyTest("""
                <class name="module_A.Script_01">
                  <test-method name="TcScript_01" status="PASS" duration-ms="10"/>
                </class>
                """);

        assertThat(test.fullPath()).isEqualTo("module_A.Script_01");
        assertThat(test.status()).isEqualTo(RunStatus.PASS);
        assertThat(test.errorMessage()).isNull();
    }

    @Test
    @DisplayName("Les méthodes de configuration (beforeClass, cleanup) sont ignorées")
    void configurationMethodsAreIgnored() {
        List<ParsedTest> tests = testsOf("""
                <class name="module_A.Script_01">
                  <test-method is-config="true" name="beforeClass" status="PASS" duration-ms="1"/>
                  <test-method name="TcScript_01" status="PASS" duration-ms="10"/>
                  <test-method is-config="true" name="cleanup" status="FAIL" duration-ms="300"/>
                </class>
                """);

        assertThat(tests).extracting(ParsedTest::status).containsExactly(RunStatus.PASS);
    }

    @Test
    @DisplayName("Un échec garde son message, l'emplacement script:ligne et les lignes ATS de la pile")
    void failureKeepsMessageLocationAndAtsStack() {
        ParsedTest test = onlyTest("""
                <class name="appli.module_A.Script_10">
                  <test-method name="TcScript_10" status="FAIL" duration-ms="85077">
                    <exception class="AtsFailError">
                      <message>
                        <![CDATA[ActionMouseKey (appli.module_A.Script_10:6) -> {"errorMessage":"element not found"}]]>
                      </message>
                      <full-stacktrace>
                        <![CDATA[AtsFailError: boom
                at com.ats.executor.ActionTestScript.exec(ActionTestScript.java:2153)
                at appli.module_A.Script_10.executeActions(Script_10.java:90)]]>
                      </full-stacktrace>
                    </exception>
                  </test-method>
                </class>
                """);

        assertThat(test.errorMessage()).isEqualTo(
                "ActionMouseKey (appli.module_A.Script_10:6) -> {\"errorMessage\":\"element not found\"}");
        assertThat(test.errorLocation()).isEqualTo("appli.module_A.Script_10:6");
        assertThat(test.atsCallStack()).isEqualTo("at appli.module_A.Script_10.executeActions(Script_10.java:90)");
    }

    @Test
    @DisplayName("Les dates TestNG (format non ISO, fuseau abrégé) et la durée sont lues")
    void datesAndDurationAreRead() {
        List<ParsedSuite> suites = parse("""
                <testng-results>
                  <suite name="TNR-Suite_A" started-at="2026-10-06T21:46:13 CEST">
                    <class name="module_A.Script_01">
                      <test-method name="TcScript_01" status="PASS" started-at="2026-10-06T22:03:35 CEST" duration-ms="85077"/>
                    </class>
                  </suite>
                </testng-results>
                """);

        assertThat(suites.getFirst().startedAt()).isEqualTo(Instant.parse("2026-10-06T19:46:13Z"));
        ParsedTest test = suites.getFirst().tests().getFirst();
        assertThat(test.startedAt()).isEqualTo(Instant.parse("2026-10-06T20:03:35Z"));
        assertThat(test.durationMs()).isEqualTo(85_077);
    }

    @Test
    @DisplayName("L17 — une date illisible donne une date vide, sans refuser le fichier")
    void unreadableDateIsLeftEmpty() {
        ParsedTest test = onlyTest("""
                <class name="module_A.Script_01">
                  <test-method name="TcScript_01" status="PASS" started-at="hier soir" duration-ms="10"/>
                </class>
                """);

        assertThat(test.startedAt()).isNull();
    }

    @Test
    @DisplayName("L08 — chaque suite du fichier est lue séparément")
    void eachSuiteIsReadSeparately() {
        List<ParsedSuite> suites = parse("""
                <testng-results>
                  <suite name="TNR-Suite_A"><class name="module_A.Script_01"><test-method name="t" status="PASS"/></class></suite>
                  <suite name="TNR-Suite_B"><class name="module_B.Script_02"><test-method name="t" status="FAIL"/></class></suite>
                </testng-results>
                """);

        assertThat(suites).extracting(ParsedSuite::name).containsExactly("TNR-Suite_A", "TNR-Suite_B");
    }

    @Test
    @DisplayName("L09 — un test ATS présent deux fois : pire statut, durées additionnées, premier message d'échec")
    void duplicateTestKeepsWorstStatusAndSumsDurations() {
        List<ParsedTest> tests = testsOf("""
                <class name="module_A.Script_01"><test-method name="t" status="SKIP" duration-ms="5"/></class>
                <class name="module_A.Script_01">
                  <test-method name="t" status="FAIL" duration-ms="7"><exception><message>premier échec</message></exception></test-method>
                </class>
                """);

        assertThat(tests).singleElement().satisfies(test -> {
            assertThat(test.status()).isEqualTo(RunStatus.FAIL);
            assertThat(test.durationMs()).isEqualTo(12);
            assertThat(test.errorMessage()).isEqualTo("premier échec");
        });
    }

    @Test
    @DisplayName("L12 — un message énorme est tronqué à 2 000 caractères")
    void hugeMessageIsTruncated() {
        ParsedTest test = onlyTest("""
                <class name="module_A.Script_01">
                  <test-method name="t" status="FAIL"><exception><message>%s</message></exception></test-method>
                </class>
                """.formatted("x".repeat(5_000)));

        assertThat(test.errorMessage()).hasSize(TestNgParser.MAX_MESSAGE_LENGTH);
    }

    @Test
    @DisplayName("L16 — un nom avec un accent décomposé est normalisé (même test ATS qu'avec l'accent composé)")
    void namesAreNormalized() {
        ParsedTest test = onlyTest("""
                <class name="module_é.Script_01"><test-method name="t" status="PASS"/></class>
                """);

        assertThat(test.fullPath()).isEqualTo("module_é.Script_01");
    }

    @Test
    @DisplayName("L13 — une entité externe (attaque XXE) est refusée sans être lue")
    void externalEntityIsRejected(@TempDir Path tempDir) throws IOException {
        Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "mot_de_passe_base=Secret123");
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE testng-results [<!ENTITY xxe SYSTEM "%s">]>
                <testng-results>
                  <suite name="TNR-Suite_A"><class name="module_A.Script_01">
                    <test-method name="t" status="FAIL"><exception><message>&xxe;</message></exception></test-method>
                  </class></suite>
                </testng-results>
                """.formatted(secret.toUri());

        assertThatThrownBy(() -> parse(xml))
                .isInstanceOf(ResultFormatException.class)
                .hasMessageContaining("DTD")
                .hasMessageNotContaining("Secret123");
    }

    @Test
    @DisplayName("L02 — un fichier tronqué est refusé")
    void truncatedFileIsRejected() {
        assertThatThrownBy(() -> parse("<testng-results><suite name=\"TNR-Suite_A\">"))
                .isInstanceOf(ResultFormatException.class);
    }

    @Test
    @DisplayName("L03 — un XML qui n'est pas un résultat TestNG est refusé")
    void otherXmlIsRejected() {
        assertThatThrownBy(() -> parse("<html><body/></html>"))
                .isInstanceOf(ResultFormatException.class)
                .hasMessageContaining("pas un résultat TestNG");
    }

    @Test
    @DisplayName("L04 — un statut inconnu est refusé en le nommant")
    void unknownStatusIsRejected() {
        assertThatThrownBy(() -> testsOf("""
                <class name="module_A.Script_01"><test-method name="t" status="BROKEN"/></class>
                """))
                .isInstanceOf(ResultFormatException.class)
                .hasMessageContaining("BROKEN");
    }

    @Test
    @DisplayName("L15 — un nom de test ATS hors format est refusé en le nommant")
    void invalidTestNameIsRejected() {
        assertThatThrownBy(() -> testsOf("""
                <class name="module_A..Script_01"><test-method name="t" status="PASS"/></class>
                """))
                .isInstanceOf(ResultFormatException.class)
                .hasMessageContaining("module_A..Script_01");
    }

    @Test
    @DisplayName("Le vrai fichier anonymisé : 18 tests ATS, dont 4 en échec, comme annoncé en tête du fichier")
    void anonymizedRealFileIsRead() throws IOException {
        try (InputStream xml = getClass().getResourceAsStream("/testng/testng-results-anonymise.xml")) {
            List<ParsedSuite> suites = parser.parse(xml);

            assertThat(suites).singleElement().satisfies(suite -> {
                assertThat(suite.tests()).hasSize(18);
                assertThat(suite.tests()).filteredOn(test -> test.status() == RunStatus.FAIL).hasSize(4);
                assertThat(suite.tests()).filteredOn(test -> test.status() == RunStatus.SKIP).hasSize(1);
            });
        }
    }

    private List<ParsedSuite> parse(String xml) {
        return parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    /** Enveloppe les classes données dans un fichier TestNG à une suite, et renvoie ses tests ATS. */
    private List<ParsedTest> testsOf(String classes) {
        return parse("<testng-results><suite name=\"TNR-Suite_A\">" + classes + "</suite></testng-results>")
                .getFirst().tests();
    }

    private ParsedTest onlyTest(String classes) {
        List<ParsedTest> tests = testsOf(classes);
        assertThat(tests).hasSize(1);
        return tests.getFirst();
    }
}
