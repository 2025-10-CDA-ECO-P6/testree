# Testree — back

API de Testree : Spring Boot 4.1, Java 21, Maven.

## Prérequis

- **JDK 21** (Temurin conseillé), avec la variable `JAVA_HOME` qui pointe dessus.
- Pas besoin d'installer Maven : le wrapper (`mvnw`) télécharge la bonne version au premier lancement.

## Commandes (depuis `back/`)

| Action | Windows (PowerShell) | Linux / macOS |
|---|---|---|
| Compiler et lancer les tests | `.\mvnw.cmd verify` | `./mvnw verify` |
| Lancer l'API (port 8080) | `.\mvnw.cmd spring-boot:run` | `./mvnw spring-boot:run` |

## Vérifier que l'API tourne

```
GET http://localhost:8080/actuator/health  →  {"groups":["liveness","readiness"],"status":"UP"}
```

Seul cet endpoint d'Actuator est exposé, sans le détail des composants (voir `src/main/resources/application.yaml`). `groups` liste seulement les noms des sondes « vivant » et « prêt » utilisées par Docker ou Kubernetes.
