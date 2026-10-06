package com.noubli.app.domain

/**
 * Règles de validation des saisies utilisateur.
 * Chaque fonction renvoie null si la valeur est valide, sinon le message d'erreur à afficher.
 */
object Validators {

    /** 1 m : le moteur v2 affiche la précision réelle et reste prudent (voir ConfidenceExitPolicy). */
    const val MIN_RADIUS_M = 1
    const val MAX_RADIUS_M = 500
    const val DEFAULT_RADIUS_M = 50
    const val MAX_ITEMS = 30
    const val MIN_PASSWORD_LENGTH = 6
    private const val MAX_LABEL_LENGTH = 40

    private val USERNAME_REGEX = Regex("^[a-z0-9_.-]{3,30}$")

    /** Les noms d'utilisateur sont insensibles à la casse : on stocke la forme normalisée. */
    fun normalizeUsername(raw: String): String = raw.trim().lowercase()

    fun usernameError(raw: String): String? =
        if (USERNAME_REGEX.matches(normalizeUsername(raw))) null
        else "Nom d'utilisateur : 3 à 30 caractères (lettres, chiffres, _ . -)"

    fun passwordError(password: String): String? =
        if (password.length >= MIN_PASSWORD_LENGTH) null
        else "Mot de passe : $MIN_PASSWORD_LENGTH caractères minimum"

    fun zoneNameError(name: String): String? = when {
        name.isBlank() -> "Donne un nom à la zone"
        name.trim().length > MAX_LABEL_LENGTH -> "Nom trop long ($MAX_LABEL_LENGTH caractères max)"
        else -> null
    }

    fun itemLabelError(label: String): String? = when {
        label.isBlank() -> "Saisis un objet"
        label.trim().length > MAX_LABEL_LENGTH -> "Objet trop long ($MAX_LABEL_LENGTH caractères max)"
        else -> null
    }

    fun itemsError(labels: List<String>): String? = when {
        labels.none { it.isNotBlank() } -> "Ajoute au moins un objet à ne pas oublier"
        labels.size > MAX_ITEMS -> "$MAX_ITEMS objets maximum par zone"
        else -> null
    }

    fun radiusError(radiusM: Int): String? =
        if (radiusM in MIN_RADIUS_M..MAX_RADIUS_M) null
        else "Rayon entre $MIN_RADIUS_M et $MAX_RADIUS_M m"

    fun latitudeError(latitude: Double?): String? =
        if (latitude != null && latitude in -90.0..90.0) null
        else "Latitude invalide (entre -90 et 90)"

    fun longitudeError(longitude: Double?): String? =
        if (longitude != null && longitude in -180.0..180.0) null
        else "Longitude invalide (entre -180 et 180)"
}
