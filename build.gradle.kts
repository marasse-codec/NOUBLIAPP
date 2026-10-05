// Build racine : déclare les plugins (versions dans gradle/libs.versions.toml)
// sans les appliquer ; le module :app les applique.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
