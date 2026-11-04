package fr.testree.tree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TreeBuilderTest {

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
