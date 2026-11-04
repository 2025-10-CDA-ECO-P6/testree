package fr.testree.tree;

import java.util.List;

/** Un nœud de l'arbre : le projet, un dossier ou un test ATS (une feuille, sans enfants). */
public record TreeNode(String name, NodeType type, List<TreeNode> children) {
}
