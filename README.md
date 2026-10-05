# Noubli — ne laisse plus rien derrière toi

Application Android (Kotlin, Jetpack Compose, Room) qui t'alerte dès que tu t'éloignes
d'une zone où tu as des objets à ne pas oublier (clés, portefeuille, badge…).
100 % locale : aucun serveur, les données restent dans la base SQLite du téléphone.

## Fonctionnement

1. Tu crées un compte local, puis des **zones** (nom, position, distance d'alerte de 20 à 500 m).
2. Dans chaque zone, tu listes les **objets** à emporter.
3. Tu actives la **surveillance** : un service lit le GPS toutes les 5 s.
4. Quand tu sors d'une zone, une notification (son + vibration) liste les objets ; « J'ai tout » l'acquitte.
5. L'**historique** garde les alertes passées.

## Compiler et installer l'APK de test

Prérequis : JDK 17+ et le SDK Android (Android Studio les fournit).

```bash
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Ou ouvre le dossier dans Android Studio et appuie sur **Run**.
Sans rien installer : pousse le projet sur GitHub, le workflow `.github/workflows/android.yml`
publie l'APK (artefact `noubli-debug-apk`).

Si Gradle ne trouve pas le SDK, crée `local.properties` à la racine :
`sdk.dir=/chemin/vers/Android/Sdk`.

## Tester l'alerte

1. Crée un compte, puis une zone « Maison » avec l'objet « clés ».
2. « Utiliser ma position actuelle », rayon 50 m, enregistre.
3. Active la surveillance (accepte localisation précise et notifications).
4. Éloigne-toi de 70 à 100 m. Sur émulateur : *Extended controls > Location*, lecture d'un itinéraire.
5. Une notification apparaît ; appuie sur « J'ai tout », puis ouvre l'historique.

Astuce : sur certains téléphones (Xiaomi, Huawei, Samsung), désactive l'optimisation de batterie
pour Noubli, sinon le système peut couper la surveillance en arrière-plan.

## Structure du code

```
app/src/main/java/com/noubli/app/
├── domain/        Logique pure (testée en JVM) : ProximityEvaluator, GeoMath, Validators, PasswordHasher, modèles
├── data/          Dépôts (Auth, Zone, Alert), SessionStore, mappers ; data/db = Room (entités, DAO, base)
├── location/      MonitoringService (premier plan), CurrentLocationProvider, MonitoringController
├── notification/  NotificationHelper, AlertActionReceiver (bouton « J'ai tout »)
└── ui/            Compose : theme, common, auth, home, zone, history, NoubliNavHost
```

Règles de code : fichiers de 300 lignes maximum (le plus long en compte 192), commentaires en français,
dépendances injectées par constructeur (`AppContainer`).

## Documentation

`docs/diagrams/` contient les diagrammes PlantUML (`.puml`) et leurs rendus (`png/`) :

| Fichier | Contenu |
| --- | --- |
| 01-cas-utilisation | Cas d'utilisation |
| 02-mld | Modèle logique de données |
| 03-classes-metier-donnees | Classes : domaine, données, services |
| 04-classes-ui | Classes : présentation (MVVM) |
| 05-flux-authentification | Flux : inscription / connexion |
| 06-flux-creation-zone | Flux : création d'une zone |
| 07-flux-detection-alerte | Flux : surveillance, détection, alerte |
| 08-architecture-couches | Architecture en couches |
| 09-architecture-deploiement | Architecture de déploiement |

Pour régénérer les images : `java -jar plantuml.jar -tpng -o png docs/diagrams/*.puml`.
# Noubliapp
