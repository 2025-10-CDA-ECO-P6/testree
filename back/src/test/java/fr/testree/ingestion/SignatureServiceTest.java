package fr.testree.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Nettoyage des messages d'échec (ADR-023, règles N1 à N8) : cas S01 à S08, tirés de résultats réels anonymisés. */
class SignatureServiceTest {

    private static final String SCRIPT_10 = "appli.module_A.sous_module_C.sous_module_F.Script_10";
    private static final String SCRIPT_11 = "appli.module_A.sous_module_C.sous_module_F.Script_11";

    private final SignatureService service = new SignatureService();

    @Test
    @DisplayName("S01 — même erreur dans deux tests ATS, chacun à sa ligne : même signature, emplacement <script>")
    void sameErrorInTwoTestsGivesSameSignature() {
        SignatureParts first = service.sign(atsMessage("ActionMouseKey", SCRIPT_10 + ":6", -1,
                "element not found: BUTTON [text = Libellé_X] after 19320 ms"), SCRIPT_10);
        SignatureParts second = service.sign(atsMessage("ActionMouseKey", SCRIPT_11 + ":6", -1,
                "element not found: BUTTON [text = Libellé_X] after 19780 ms"), SCRIPT_11);

        assertThat(first.hash()).isEqualTo(second.hash());
        assertThat(first.location()).isEqualTo("<script>");
        assertThat(first.normalizedText()).isEqualTo("element not found: BUTTON [text = Libellé_X] after <n> ms");
    }

    @Test
    @DisplayName("S02 et S08 — la ligne du sous-script a bougé : même signature, emplacement sans la ligne")
    void movedLineInSubScriptGivesSameSignature() {
        SignatureParts lineOne = service.sign(atsMessage("ActionMouseKey", "lib.outils.sous_script_N:1", -1,
                "element not found: A [text = Libellé_Z] after 17107 ms"), "appli.module_A.Script_01");
        SignatureParts lineTwo = service.sign(atsMessage("ActionMouseKey", "lib.outils.sous_script_N:2", -1,
                "element not found: A [text = Libellé_Z] after 17134 ms"), "appli.module_A.Script_02");

        assertThat(lineOne.hash()).isEqualTo(lineTwo.hash());
        assertThat(lineTwo.location()).isEqualTo("lib.outils.sous_script_N");
    }

    @Test
    @DisplayName("S04 — même sous-script, erreurs différentes : signatures différentes")
    void differentErrorsInSameSubScriptGiveDifferentSignatures() {
        SignatureParts notFound = service.sign(atsMessage("ActionMouseKey", "lib.outils.sous_script_N:2", -1,
                "element not found: A [text = Libellé_Z] after 17134 ms"), "appli.module_A.Script_01");
        SignatureParts countFailed = service.sign(atsMessage("ActionAssertCount", "lib.outils.sous_script_N:2", -8,
                "expected at least [1] occurrence(s) but [0] found"), "appli.module_A.Script_01");

        assertThat(countFailed.hash()).isNotEqualTo(notFound.hash());
        assertThat(countFailed.normalizedText()).isEqualTo("expected at least [<n>] occurrence(s) but [<n>] found");
    }

    @Test
    @DisplayName("S05 — même texte dans deux sous-scripts différents : signatures différentes (l'emplacement compte)")
    void sameTextInDifferentSubScriptsGivesDifferentSignatures() {
        String text = "element not found: A [text = Libellé_Z] after 17134 ms";
        SignatureParts inN = service.sign(atsMessage("ActionMouseKey", "lib.outils.sous_script_N:2", -1, text), "a.Script_01");
        SignatureParts inM = service.sign(atsMessage("ActionMouseKey", "lib.outils.sous_script_M:2", -1, text), "a.Script_01");

        assertThat(inN.hash()).isNotEqualTo(inM.hash());
    }

    @Test
    @DisplayName("S06 — message non structuré (exception Java) : première ligne seulement, sans erreur")
    void unstructuredMessageKeepsFirstLine() {
        SignatureParts parts = service.sign("""
                java.lang.NullPointerException: Cannot invoke "x" because "y" is null
                    at a.b.C.m(C.java:12)
                    at d.e.F.n(F.java:34)""", "a.Script_01");

        assertThat(parts.normalizedText()).isEqualTo("java.lang.NullPointerException: Cannot invoke \"x\" because \"y\" is null");
        assertThat(parts.action()).isNull();
        assertThat(parts.location()).isNull();
    }

    @Test
    @DisplayName("S07 — message énorme : texte nettoyé borné à 1 000 caractères")
    void hugeMessageIsBounded() {
        SignatureParts parts = service.sign("x ".repeat(500_000), "a.Script_01");

        assertThat(parts.normalizedText()).hasSize(SignatureService.MAX_NORMALIZED_LENGTH);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("N4 à N6 — ce qui varie d'un échec à l'autre est remplacé, le reste est gardé")
    @CsvSource(delimiter = '|', textBlock = """
            after 19320 ms                           | after <n> ms
            expected [1] but [0]                     | expected [<n>] but [<n>]
            le 2026-10-06 à 21:50:05                 | le <date> à <heure>
            id 3f2a9c1e-0b1d-4e5f-9a8b-123456789abc  | id <id>
            objet@56a3dd73                           | objet@<id>
            1234 lignes                              | <n> lignes
            DIV [class = elevation-2] dans Script_10 | DIV [class = elevation-2] dans Script_10
            """)
    void variablePartsAreReplaced(String input, String expected) {
        assertThat(SignatureService.normalize(input)).isEqualTo(expected);
    }

    private static String atsMessage(String action, String locationWithLine, int errorCode, String errorMessage) {
        return "%s (%s) -> {\"app\":\"chrome\",\"errorCode\":%d,\"errorMessage\":\"%s\"}"
                .formatted(action, locationWithLine, errorCode, errorMessage);
    }
}
