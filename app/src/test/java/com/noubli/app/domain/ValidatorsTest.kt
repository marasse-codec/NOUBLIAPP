package com.noubli.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests des règles de validation des saisies. */
class ValidatorsTest {

    @Test
    fun `nom d'utilisateur valide ou non`() {
        assertNull(Validators.usernameError("alice"))
        assertNull(Validators.usernameError("  Alice.B_2  "))   // espaces et casse tolérés
        assertNotNull(Validators.usernameError("ab"))           // trop court
        assertNotNull(Validators.usernameError("alice!"))       // caractère interdit
        assertNotNull(Validators.usernameError("a".repeat(31))) // trop long
    }

    @Test
    fun `le nom d'utilisateur est normalise en minuscules`() {
        assertEquals("alice", Validators.normalizeUsername("  ALice "))
    }

    @Test
    fun `mot de passe de 6 caracteres minimum`() {
        assertNotNull(Validators.passwordError("12345"))
        assertNull(Validators.passwordError("123456"))
    }

    @Test
    fun `nom de zone`() {
        assertNotNull(Validators.zoneNameError("   "))
        assertNotNull(Validators.zoneNameError("x".repeat(41)))
        assertNull(Validators.zoneNameError("Maison"))
    }

    @Test
    fun `liste d'objets`() {
        assertNotNull(Validators.itemsError(emptyList()))
        assertNotNull(Validators.itemsError(listOf("  ", "")))
        assertNull(Validators.itemsError(listOf("clés")))
        assertNotNull(Validators.itemsError(List(31) { "objet $it" }))
    }

    @Test
    fun `rayon dans les bornes`() {
        assertNotNull(Validators.radiusError(0))
        assertNull(Validators.radiusError(1))
        assertNull(Validators.radiusError(500))
        assertNotNull(Validators.radiusError(501))
    }

    @Test
    fun `coordonnees GPS`() {
        assertNull(Validators.latitudeError(48.85))
        assertNotNull(Validators.latitudeError(91.0))
        assertNotNull(Validators.latitudeError(null))
        assertNull(Validators.longitudeError(-180.0))
        assertNotNull(Validators.longitudeError(181.0))
        assertNotNull(Validators.longitudeError(null))
    }
}
