// Configuration globale du build : dépôts de plugins/dépendances et modules du projet.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Les dépôts sont déclarés ici une seule fois (pas dans les modules).
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Noubli"
include(":app")
