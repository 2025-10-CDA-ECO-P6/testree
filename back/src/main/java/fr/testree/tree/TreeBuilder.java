package fr.testree.tree;

import java.util.ArrayList;
import java.util.List;

/** Transforme les chemins des tests ATS d'un build en arbre : projet › dossiers › tests ATS (ADR-012). */
public class TreeBuilder {

    /** Séparateur des chemins TestNG. {@code split} attend une expression régulière : le point doit être échappé. */
    private static final String PATH_SEPARATOR = "\\.";

    /**
     * Construit l'arbre d'un projet à partir des chemins complets de ses tests ATS. Dans
     * {@code module_A.Script_01}, le dernier segment est le test ATS, ceux d'avant sont des dossiers.
     */
    public TreeNode build(String projectName, List<String> fullPaths) {
        TreeNode project = new TreeNode(projectName, NodeType.PROJECT, new ArrayList<>());
        for (String path : fullPaths) {
            String[] parts = path.split(PATH_SEPARATOR);
            TreeNode leaf = new TreeNode(parts[parts.length - 1], NodeType.TEST, List.of());
            if (parts.length == 1) {
                project.children().add(leaf);
            } else {
                TreeNode folder = findOrCreateFolder(project, parts[0]);
                folder.children().add(leaf);
            }
        }
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
}
