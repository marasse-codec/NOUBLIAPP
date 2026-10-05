package com.noubli.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests du hachage de mots de passe (peu d'itérations pour que les tests restent rapides). */
class PasswordHasherTest {

    private val hasher = PasswordHasher(iterations = 1_000)

    @Test
    fun `le bon mot de passe est reconnu`() {
        val salt = hasher.newSalt()
        val hash = hasher.hash("secret123", salt)
        assertTrue(hasher.matches("secret123", salt, hash))
    }

    @Test
    fun `un mauvais mot de passe est rejete`() {
        val salt = hasher.newSalt()
        val hash = hasher.hash("secret123", salt)
        assertFalse(hasher.matches("secret124", salt, hash))
    }

    @Test
    fun `le hachage est deterministe pour un sel donne`() {
        val salt = hasher.newSalt()
        assertEquals(hasher.hash("abc123", salt), hasher.hash("abc123", salt))
    }

    @Test
    fun `deux sels differents donnent deux empreintes differentes`() {
        val hashA = hasher.hash("abc123", hasher.newSalt())
        val hashB = hasher.hash("abc123", hasher.newSalt())
        assertNotEquals(hashA, hashB)
    }

    @Test
    fun `l'empreinte ne contient pas le mot de passe en clair`() {
        val hash = hasher.hash("motdepasse", hasher.newSalt())
        assertFalse(hash.contains("motdepasse"))
    }
}
