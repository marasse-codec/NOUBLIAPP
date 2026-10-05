package com.noubli.app.domain

import com.noubli.app.domain.model.GeoFix
import com.noubli.app.domain.model.ReminderItem
import com.noubli.app.domain.model.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests de la détection d'éloignement : le coeur métier de l'application. */
class ProximityEvaluatorTest {

    private val baseLat = 48.8566
    private val baseLon = 2.3522

    // Zone de 50 m de rayon centrée sur (baseLat, baseLon), avec un objet à ne pas oublier.
    private val zone = Zone(
        id = 1, userId = 1, name = "Maison",
        latitude = baseLat, longitude = baseLon, radiusM = 50,
        items = listOf(ReminderItem(label = "clés"))
    )

    /** Position située à [meters] mètres au nord du centre (1° de latitude ≈ 111 195 m). */
    private fun fixAt(meters: Double, accuracy: Float = 5f) =
        GeoFix(baseLat + meters / 111_195.0, baseLon, accuracy, 0L)

    @Test
    fun `aucune alerte tant que l'utilisateur reste dans la zone`() {
        val evaluator = ProximityEvaluator()
        listOf(0.0, 10.0, 30.0, 49.0).forEach {
            assertTrue(evaluator.onLocation(listOf(zone), fixAt(it)).isEmpty())
        }
    }

    @Test
    fun `alerte apres deux mesures consecutives a l'exterieur`() {
        val evaluator = ProximityEvaluator()
        evaluator.onLocation(listOf(zone), fixAt(0.0))                    // dedans
        assertTrue(evaluator.onLocation(listOf(zone), fixAt(70.0)).isEmpty()) // 1re mesure dehors
        val exits = evaluator.onLocation(listOf(zone), fixAt(75.0))         // 2e mesure dehors
        assertEquals(1, exits.size)
        assertEquals(zone.id, exits[0].zone.id)
        assertEquals(75, exits[0].distanceM)
    }

    @Test
    fun `une seule alerte tant que l'utilisateur reste dehors`() {
        val evaluator = ProximityEvaluator()
        evaluator.onLocation(listOf(zone), fixAt(0.0))
        evaluator.onLocation(listOf(zone), fixAt(70.0))
        assertEquals(1, evaluator.onLocation(listOf(zone), fixAt(80.0)).size)
        assertTrue(evaluator.onLocation(listOf(zone), fixAt(120.0)).isEmpty())
        assertTrue(evaluator.onLocation(listOf(zone), fixAt(200.0)).isEmpty())
    }

    @Test
    fun `pas d'alerte si l'application demarre alors que l'utilisateur est deja dehors`() {
        val evaluator = ProximityEvaluator()
        assertTrue(evaluator.onLocation(listOf(zone), fixAt(300.0)).isEmpty())
        assertTrue(evaluator.onLocation(listOf(zone), fixAt(310.0)).isEmpty())
    }

    @Test
    fun `l'alerte est rearmee apres un retour dans la zone`() {
        val evaluator = ProximityEvaluator()
        evaluator.onLocation(listOf(zone), fixAt(0.0))
        evaluator.onLocation(listOf(zone), fixAt(70.0))
        assertEquals(1, evaluator.onLocation(listOf(zone), fixAt(80.0)).size) // 1re alerte
        evaluator.onLocation(listOf(zone), fixAt(10.0))                       // retour dedans
        evaluator.onLocation(listOf(zone), fixAt(70.0))
        assertEquals(1, evaluator.onLocation(listOf(zone), fixAt(80.0)).size) // 2e alerte
    }

    @Test
    fun `une mesure isolee a l'exterieur ne declenche rien`() {
        val evaluator = ProximityEvaluator()
        evaluator.onLocation(listOf(zone), fixAt(0.0))
        evaluator.onLocation(listOf(zone), fixAt(70.0))          // saut du GPS
        evaluator.onLocation(listOf(zone), fixAt(20.0))          // retour dedans
        assertTrue(evaluator.onLocation(listOf(zone), fixAt(70.0)).isEmpty()) // compteur remis a zero
    }

    @Test
    fun `les mesures trop imprecises sont ignorees`() {
        val evaluator = ProximityEvaluator()
        evaluator.onLocation(listOf(zone), fixAt(0.0))
        assertTrue(evaluator.onLocation(listOf(zone), fixAt(500.0, accuracy = 150f)).isEmpty())
        assertTrue(evaluator.onLocation(listOf(zone), fixAt(500.0, accuracy = 150f)).isEmpty())
    }

    @Test
    fun `la bande ambigue autour du rayon ne compte pas comme sortie`() {
        val evaluator = ProximityEvaluator()
        evaluator.onLocation(listOf(zone), fixAt(0.0))
        // 55 m avec une precision de 20 m : 55 < 50 + 20*0,5 = 60 -> ambigu, ni dedans ni dehors.
        repeat(5) {
            assertTrue(evaluator.onLocation(listOf(zone), fixAt(55.0, accuracy = 20f)).isEmpty())
        }
    }

    @Test
    fun `chaque zone est suivie independamment`() {
        val far = zone.copy(id = 2, name = "Bureau", latitude = baseLat + 0.01) // ~1,1 km au nord
        val evaluator = ProximityEvaluator()
        // Au bureau : dedans pour "far", dehors (sans alerte) pour "zone".
        evaluator.onLocation(listOf(zone, far), GeoFix(far.latitude, far.longitude, 5f, 0L))
        // Retour a la maison : "zone" devient INSIDE.
        evaluator.onLocation(listOf(zone, far), fixAt(0.0))
        evaluator.onLocation(listOf(zone, far), fixAt(70.0))
        val exits = evaluator.onLocation(listOf(zone, far), fixAt(80.0))
        assertEquals(listOf(1L), exits.map { it.zone.id })
    }

    @Test
    fun `retainOnly oublie les zones supprimees`() {
        val evaluator = ProximityEvaluator()
        evaluator.onLocation(listOf(zone), fixAt(0.0))     // zone 1 : INSIDE
        evaluator.retainOnly(emptyList())                  // zone supprimee
        // La zone revient : etat UNKNOWN, donc pas d'alerte meme apres 2 mesures dehors.
        evaluator.onLocation(listOf(zone), fixAt(70.0))
        assertTrue(evaluator.onLocation(listOf(zone), fixAt(80.0)).isEmpty())
    }
}
