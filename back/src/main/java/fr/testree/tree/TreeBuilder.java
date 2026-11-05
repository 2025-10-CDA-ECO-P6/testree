package fr.testree.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Transforme les chemins des tests ATS d'un build en arbre : projet › dossiers › tests ATS (ADR-012). */
public class TreeBuilder {

    /** Séparateur des chemins TestNG. {@code split} attend une expression régulière : le point doit être échappé. */
    private static final String PATH_SEPARATOR = "\\.";

    /**
     * Ordre des enfants : les dossiers avant les tests ATS ({@link NodeType} déclare {@code FOLDER} avant {@code TEST}),
     * puis par nom sans tenir compte des majuscules. L'arbre ne dépend ainsi pas de l'ordre du XML.
     */
    private static final Comparator<TreeNode> FOLDERS_FIRST_THEN_BY_NAME = Comparator
            .comparing(TreeNode::type)
            .thenComparing(TreeNode::name, String.CASE_INSENSITIVE_ORDER);

    /**
     * Construit l'arbre d'un projet à partir des chemins complets de ses tests ATS. Dans
     * {@code appli.module_A.Script_01}, le dernier segment est le test ATS, ceux d'avant sont des dossiers,
     * sans limite de profondeur.
     */
    public TreeNode build(String projectName, List<String> fullPaths) {
        TreeNode project = new TreeNode(projectName, NodeType.PROJECT, new ArrayList<>());
        for (String path : fullPaths) {
            String[] parts = path.split(PATH_SEPARATOR);
            TreeNode current = project;
            for (int i = 0; i < parts.length - 1; i++) {
                current = findOrCreateFolder(current, parts[i]);
            }
            current.children().add(new TreeNode(parts[parts.length - 1], NodeType.TEST, List.of()));
        }
        sortChildren(project);
        return project;
    }

    /** Renvoie le dossier {@code name} déjà présent sous {@code parent}, sinon le crée : un dossier partagé n'existe qu'une fois. */
    private TreeNode findOrCreateFolder(TreeNode parent, String name) {
        for (TreeNode child : parent.children()) {
            if (child.name().equals(name)) {
                return child;
            }
        }
        TreeNode folder = new TreeNode(name, NodeType.FOLDER, new ArrayList<>());
        parent.children().add(folder);
        return folder;
    }

    /** Trie les enfants du nœud, puis ceux de chaque enfant (récursivité). Un test ATS n'a pas d'enfants : rien à trier. */
    private void sortChildren(TreeNode node) {
        if (node.type() == NodeType.TEST) {
            return;
        }
        node.children().sort(FOLDERS_FIRST_THEN_BY_NAME);
        node.children().forEach(this::sortChildren);
    }
}
