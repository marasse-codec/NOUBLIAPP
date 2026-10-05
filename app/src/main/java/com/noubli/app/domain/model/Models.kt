package com.noubli.app.domain.model

// ---------------------------------------------------------------------------
// Modèles métier de Noubli.
// Ce sont de simples structures de données, indépendantes d'Android et de la
// base de données : elles sont donc testables en JVM pur.
// ---------------------------------------------------------------------------

/** Utilisateur connecté (le mot de passe n'apparaît jamais dans le modèle métier). */
data class User(val id: Long, val username: String)

/** Objet à ne pas oublier en quittant une zone (ex. « clés », « portefeuille »). */
data class ReminderItem(val id: Long = 0, val label: String)

/**
 * Zone surveillée : un cercle (centre GPS + rayon en mètres) auquel est associée
 * une liste d'objets. Une alerte est émise quand l'utilisateur sort du cercle.
 */
data class Zone(
    val id: Long = 0,
    val userId: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusM: Int,
    val isActive: Boolean = true,
    val createdAt: Long = 0,
    val items: List<ReminderItem> = emptyList()
)

/** Trace d'une alerte émise : alimente l'écran « Historique ». */
data class AlertEvent(
    val id: Long = 0,
    val zoneId: Long,
    val zoneName: String,
    val triggeredAt: Long,
    val distanceM: Int,
    val itemsSummary: String,
    val acknowledgedAt: Long? = null
) {
    /** Vrai si l'utilisateur a appuyé sur « J'ai tout ». */
    val isAcknowledged: Boolean get() = acknowledgedAt != null
}

/** Position GPS brute, indépendante de la classe android.location.Location. */
data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    /** Précision estimée en mètres (Float.MAX_VALUE si inconnue). */
    val accuracyM: Float,
    val timeMs: Long
)

/** Résultat de la détection : l'utilisateur vient de quitter [zone]. */
data class ZoneExit(val zone: Zone, val distanceM: Int)
