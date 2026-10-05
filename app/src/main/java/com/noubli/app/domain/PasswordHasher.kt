package com.noubli.app.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hachage des mots de passe : PBKDF2-HMAC-SHA256 avec sel aléatoire par utilisateur.
 * Le mot de passe n'est jamais stocké en clair ; seuls le sel et l'empreinte le sont.
 */
class PasswordHasher(
    /** Nombre d'itérations (ralentit les attaques par force brute). */
    private val iterations: Int = 120_000,
    private val random: SecureRandom = SecureRandom()
) {

    /** Génère un sel aléatoire de 16 octets, encodé en Base64. */
    fun newSalt(): String {
        val salt = ByteArray(SALT_BYTES)
        random.nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    /** Calcule l'empreinte (Base64) du mot de passe avec le sel donné. */
    fun hash(password: String, saltBase64: String): String {
        val salt = Base64.getDecoder().decode(saltBase64)
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        try {
            val key = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
            return Base64.getEncoder().encodeToString(key)
        } finally {
            spec.clearPassword() // efface le mot de passe de la mémoire dès que possible
        }
    }

    /** Vérifie un mot de passe ; comparaison en temps constant pour éviter les fuites par timing. */
    fun matches(password: String, saltBase64: String, expectedHash: String): Boolean {
        val actual = hash(password, saltBase64).toByteArray()
        return MessageDigest.isEqual(actual, expectedHash.toByteArray())
    }

    private companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val SALT_BYTES = 16
        const val KEY_BITS = 256
    }
}
