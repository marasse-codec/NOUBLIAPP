# Noubli — ne laisse plus rien derrière toi

Application Android (Kotlin, Jetpack Compose, Room) qui t'alerte dès que tu t'éloignes
d'une zone où tu as des objets à ne pas oublier (clés, portefeuille, badge…).
100 % locale : aucun serveur, les données restent dans la base SQLite du téléphone.

## Fonctionnement

1. Tu crées un compte local, puis des **zones** (nom, position, distance d'alerte de 1 à 500 m).
2. Dans chaque zone, tu listes les **objets** à emporter.
3. Tu actives la **surveillance** : un service lit le GPS chaque seconde et le moteur de suivi décide de la sortie.
4. Quand tu sors d'une zone, une notification (son + vibration) liste les objets ; « J'ai tout » l'acquitte.
5. L'**historique** garde les alertes passées.

## Version 2 (étape 2) : suivi précis et radar

- **Rayon dès 1 m**, avec un curseur logarithmique (fin près de 1 m). L'alerte reste **prudente** :
  elle part quand distance − rayon > 3 × précision du GPS, confirmé sur 3 mesures. Résultat simulé :
  0 fausse alerte sur 400 essais de 5 min immobile ; avec un GPS à ±4 m, l'alerte part environ
  12 à 16 s (≈ 16 m) après le départ. La fusion avec les pas et le cap (étape 3) vise à réduire ce délai.
- **Suivi en direct** (bouton sur chaque zone) : radar (rayon, seuil de déclenchement, position ±σ, trajet),
  distance, marge, précision, déplacement calculé.
- **Simulation de sortie** (sans bouger ni notifier) et **enregistrement de trajets** en base (tables `trace`,
  `trace_point`, migration 1 → 2).
- Tous les réglages sont dans `DetectionConfig` ; `useLegacyPolicy = true` rétablit la règle de la v1.

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
├── domain/        Logique pure (testée en JVM) : GeoMath, Validators, RadiusScale, PasswordHasher, modèles
│   ├── sensing/   Ports et modèles capteurs : PositionSource, PositionEstimate, DetectionConfig
│   ├── fusion/    PositionFusion, GpsOnlyFusion (la fusion pas + cap viendra ici)
│   └── policy/    ExitPolicy, ConfidenceExitPolicy (v2), LegacyThresholdPolicy (v1)
├── engine/        TrackingEngine (coroutines), TrackingPipeline (pur), TrackingBus, TrackingSnapshot, AlertChannel
├── sensors/       Adaptateurs Android : SystemLocationSource (GPS), NotificationAlertChannel
├── replay/        TraceScript + SimulatedPositionSource (simulation), TraceRecorder (enregistrement en base)
├── data/          Dépôts (Auth, Zone, Alert, Trace), SessionStore, mappers ; data/db = Room (entités, DAO, base v2)
├── location/      MonitoringService (cycle de vie, délègue au moteur), CurrentLocationProvider, MonitoringController
├── notification/  NotificationHelper, AlertActionReceiver (bouton « J'ai tout »)
└── ui/            Compose : theme, common, auth, home, zone, history, live (radar), NoubliNavHost
```

Règles de code : fichiers de 300 lignes maximum (le plus long en compte 220), commentaires en français,
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
| v2/10-architecture-v2 | Architecture cible v2 (ports et adaptateurs) |
| v2/11-classes-v2 | Classes v2 |
| v2/12-flux-fusion-decision | Flux : mesure -> fusion -> décision |
| v2/13-maquette-suivi-direct | Maquette de l'écran Suivi en direct |

Pour régénérer les images : `java -jar plantuml.jar -tpng -o png docs/diagrams/*.puml docs/diagrams/v2/*.puml`.
