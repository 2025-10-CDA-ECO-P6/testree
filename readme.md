# Testree

> **Test with care.** Les tests Agilitest d'une équipe, affichés en arbre et colorés par les résultats de la CI, pour répondre vite à la question : *« c'est mon test qui est cassé, ou l'application ? »*

Projet réalisé en alternance pour le titre professionnel **Concepteur Développeur d'Applications** (RNCP 37873).

---

## Le problème

Une équipe QA qui automatise ses tests avec Agilitest finit par avoir des centaines de scripts `.ats`, rangés en dossiers et lancés chaque nuit par Jenkins. Le matin, il faut retrouver ce qui a cassé, depuis quand, et surtout **pourquoi** : un bug de l'application, un script à mettre à jour, ou un problème d'environnement. Aujourd'hui, ce tri se fait à la main, dans les logs Jenkins et dans un fichier Excel tenu par une seule personne.

## Comment ça marche

```
Jenkins (post-build)                 Testree
┌──────────────────┐  curl + token  ┌─────────────────────────────────────────┐
│ testng-results   │ ─────────────▶ │ API d'ingestion → analyse du XML        │
│ .xml de la nuit  │  (multipart)   │   → PostgreSQL → arbre + diagnostic     │
└──────────────────┘                └─────────────────────────────────────────┘
```

- **Jenkins pousse, Testree ne va rien chercher** : une ligne `curl` dans le post-build, générée par Testree, suffit. Aucune connexion de Testree vers Jenkins ou GitLab.
- **L'arbre suit les dossiers du dépôt de tests** : on descend de module en module, chaque feuille est un script coloré selon ses derniers résultats.
- **Le panneau d'un script** montre sa frise de résultats et les faits utiles au diagnostic : depuis quand il échoue, la cause (signature de l'erreur), combien d'autres tests ont la même erreur, si le script a été modifié.

## Périmètre

| Version | Contenu |
|---|---|
| **V1** | Ingestion par API avec un token par projet · arbre drill-down coloré · panneau du script (frise, faits du diagnostic, « N autres tests ont la même erreur », étiquette appli / test / environnement) · comptes sur invitation, 3 rôles · assistant de création de projet qui génère le snippet Jenkins · thèmes clair et sombre · conservation du détail sur 60 jours |
| **V1.5** | Verdict automatique « test ou appli ? », réglé sur les étiquettes posées pendant le pilote · version de l'application testée · sous-scripts · résultats Karate |

## Avancement

| Partie | État |
|---|---|
| Conception | Besoins, user stories et critères d'acceptation, MCD, MLD, diagramme de séquence de l'ingestion |
| Front | Arbre, navigation et panneau du script, sur données de démonstration |
| Back | Base PostgreSQL et migrations Flyway ; lecture en flux des résultats TestNG ; regroupement des erreurs par signature (messages nettoyés, empreinte) ; construction de l'arbre à partir des chemins des tests ; tests d'intégration sur une vraie base |
| En cours | Enregistrement d'un build en une seule transaction, API d'ingestion avec token, puis branchement du front sur l'API |

## Stack

| Couche | Choix | Pourquoi |
|---|---|---|
| Front | Vue 3, TypeScript, Vue Flow, Pinia, Vite | Arbre interactif sans recoder le rendu |
| Back | Spring Boot 4.1, Java 21, Maven | Stack de l'entreprise, maintenable par l'équipe |
| Données | PostgreSQL 18, Flyway | Contraintes portées par la base, migrations versionnées |
| Tests | JUnit 5, Testcontainers, Vitest | Tests sur un vrai PostgreSQL jetable, pas sur une imitation |
| Livraison | Docker, GitHub Actions | CI du front et du back à chaque pull request |

## Lancer le projet en local

Prérequis : Git, JDK 21, Node 24, Docker Desktop.

```bash
cp .env.example .env          # puis choisir un mot de passe dans .env (jamais versionné)
docker compose up -d          # base PostgreSQL 18
cd back && ./mvnw verify      # compilation + tests (Testcontainers lance sa propre base)
./mvnw spring-boot:run        # API sur http://localhost:8080 (santé : /actuator/health)
cd ../front && npm ci && npm run dev
```

## Organisation du dépôt

| Dossier | Contenu |
|---|---|
| `front/` | Application Vue |
| `back/` | API Spring Boot ; migrations dans `src/main/resources/db/migration` |
| `docs/03-conception/` | [MCD](docs/03-conception/mcd/MCD.md), [MLD](docs/03-conception/mld/MLD.md), [séquence de l'ingestion](docs/03-conception/sequences/ingestion.md) |

Les autres documents de conception (user stories, critères d'acceptation, dictionnaire de données) seront publiés ici au fil du projet.

## Sécurité et sobriété

- Token d'ingestion par projet, stocké sous forme d'empreinte ; identifiants non devinables (UUID) **et** contrôle d'appartenance au projet à chaque lecture.
- Fichier de résultats lu sans DTD ni entités externes (protection contre les attaques XXE), messages d'erreur tronqués à une taille bornée.
- Règles d'intégrité imposées par la base (valeurs autorisées, doublons de build refusés, liens `http(s)` seulement) et vérifiées par des tests.
- Pas d'interrogation périodique de Jenkins : les données n'arrivent que quand un build se termine. Détail des résultats conservé 60 jours, puis purgé.

---

Raphaël Bard — alternant développeur, titre CDA niveau 6.
