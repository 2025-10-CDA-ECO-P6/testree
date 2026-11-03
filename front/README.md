# Testree — front

Application Vue de Testree : Vue 3, TypeScript, Vue Flow, Pinia, Vite.

## Prérequis

- Node.js 22.18 ou plus récent (24 conseillé).

## Commandes (depuis `front/`)

| Action | Commande |
|---|---|
| Installer les dépendances | `npm ci` |
| Lancer en développement | `npm run dev` |
| Tests unitaires (Vitest) | `npm run test:unit` |
| Vérifier les types | `npm run type-check` |
| Linter (oxlint + ESLint) | `npm run lint` |
| Construire pour la production | `npm run build` |

## Organisation

| Dossier | Contenu |
|---|---|
| `src/components/tree/` | Arbre Vue Flow, nœuds, panneau du script |
| `src/composables/` | Logique réutilisable, dont la navigation par niveaux (`useTreeNavigation`) |
| `src/fixtures/` | Données de démonstration, en attendant le branchement sur l'API |
| `src/assets/` | Styles, dont les variables de couleur des thèmes (`tree-tokens.css`) |
