package com.noubli.app.domain.model

/** Causes d'échec d'une inscription ou d'une connexion, avec le message affiché à l'utilisateur. */
enum class AuthError(val message: String) {
    INVALID_USERNAME("Nom d'utilisateur : 3 à 30 caractères (lettres, chiffres, _ . -)"),
    WEAK_PASSWORD("Mot de passe : 6 caractères minimum"),
    USERNAME_TAKEN("Ce nom d'utilisateur existe déjà"),
    WRONG_CREDENTIALS("Identifiants incorrects")
}

/** Résultat d'une tentative d'authentification (succès avec l'utilisateur, ou échec typé). */
sealed interface AuthResult {
    data class Success(val user: User) : AuthResult
    data class Failure(val error: AuthError) : AuthResult
}
