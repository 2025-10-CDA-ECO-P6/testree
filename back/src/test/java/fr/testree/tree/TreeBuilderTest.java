package fr.testree.tree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TreeBuilderTest {

    // Cas T02 de cas-de-test-treebuilder.md : trois tests ATS à cinq niveaux de profondeur.

    private static final List<String> NESTED_PATHS = List.of(
            "appli.module_A.sous_module_C.sous_module_F.Script_08",
            "appli.module_A.sous_module_C.sous_module_F.Script_10",
            "appli.module_A.sous_module_C.sous_module_G.Script_12");

    private static final TreeNode NESTED_TREE = project("HCM",
            folder("appli",
                    folder("module_A",
                            folder("sous_module_C",
                                    folder("sous_module_F", atsTest("Script_08"), atsTest("Script_10")),
                                    folder("sous_module_G", atsTest("Script_12"))))));

    private final TreeBuilder builder = new TreeBuilder();

    @Test
    @DisplayName("Un test ATS sans dossier se pose directement sous le projet")
    void testWithoutFolderIsPlacedUnderProject() {
        TreeNode tree = builder.build("HCM", List.of("Script_Racine"));

        assertThat(tree).isEqualTo(project("HCM", atsTest("Script_Racine")));
    }

    @Test
    @DisplayName("Un test ATS dans un dossier : projet, dossier, test ATS")
    void testInFolderIsPlacedUnderFolder() {
        TreeNode tree = builder.build("HCM", List.of("Module_A.Script_01"));

        assertThat(tree).isEqualTo(project("HCM", folder("Module_A", atsTest("Script_01"))));
    }

    @Test
    @DisplayName("Un dossier partagé par plusieurs tests ATS n'est créé qu'une fois")
    void sharedFolderIsCreatedOnlyOnce() {
        TreeNode tree = builder.build("HCM", List.of("Module_A.Script_01", "Module_A.Script_02", "Module_B.Script_03"));

        assertThat(tree).isEqualTo(project("HCM",
                folder("Module_A", atsTest("Script_01"), atsTest("Script_02")),
                folder("Module_B", atsTest("Script_03"))));
    }

    @Test
    @DisplayName("Les dossiers imbriqués sont créés à chaque niveau, sans limite de profondeur")
    void nestedFoldersAreCreatedAtEveryLevel() {
        TreeNode tree = builder.build("HCM", NESTED_PATHS);

        assertThat(tree).isEqualTo(NESTED_TREE);
    }

    @Test
    @DisplayName("L'ordre des chemins dans le XML ne change pas l'arbre")
    void pathOrderDoesNotChangeTheTree() {
        List<String> reversed = NESTED_PATHS.reversed();

        TreeNode tree = builder.build("HCM", reversed);

        assertThat(tree).isEqualTo(NESTED_TREE);
    }

    @Test
    @DisplayName("Les dossiers passent avant les tests ATS, puis ordre alphabétique sans tenir compte des majuscules")
    void foldersComeFirstThenAlphabeticalIgnoringCase() {
        TreeNode tree = builder.build("HCM", List.of(
                "module_A.Script_02", "module_A.sous_module_I.Script_18", "module_A.script_01"));

        assertThat(tree).isEqualTo(project("HCM",
                folder("module_A",
                        folder("sous_module_I", atsTest("Script_18")),
                        atsTest("script_01"),
                        atsTest("Script_02"))));
    }

    // Raccourcis pour écrire l'arbre attendu sur le modèle du dessin : projet › dossiers › tests ATS.

    private static TreeNode project(String name, TreeNode... children) {
        return new TreeNode(name, NodeType.PROJECT, List.of(children));
    }

    private static TreeNode folder(String name, TreeNode... children) {
        return new TreeNode(name, NodeType.FOLDER, List.of(children));
    }

    private static TreeNode atsTest(String name) {
        return new TreeNode(name, NodeType.TEST, List.of());
    }
}
