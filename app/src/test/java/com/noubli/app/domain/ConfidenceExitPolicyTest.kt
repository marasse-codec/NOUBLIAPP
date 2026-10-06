package com.noubli.app.domain

import com.noubli.app.domain.model.ReminderItem
import com.noubli.app.domain.model.Zone
import com.noubli.app.domain.policy.ConfidenceExitPolicy
import com.noubli.app.domain.policy.ZoneState
import com.noubli.app.domain.sensing.PositionEstimate
import com.noubli.app.domain.sensing.SensorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests de la règle de sortie fondée sur la confiance (distance − rayon > k × σ). */
class ConfidenceExitPolicyTest {

    private val lat = 48.8566
    private val lon = 2.3522

    // Zone de 5 m ; avec σ = 3 m et k = 2, le seuil de déclenchement est à 11 m du centre.
    private val zone = Zone(
        id = 1, userId = 1, name = "Maison", latitude = lat, longitude = lon, radiusM = 5,
        items = listOf(ReminderItem(label = "clés"))
    )

    private fun estimateAt(northM: Double, sigma: Double = 3.0) = PositionEstimate(
        latitude = lat + northM / 111_195.0, longitude = lon,
        sigmaM = sigma, timeMs = 0, sources = setOf(SensorKind.GPS)
    )

    private fun policy() = ConfidenceExitPolicy(k = 2.0, confirmations = 3, minSigmaM = 1.0)

    @Test
    fun `le seuil de declenchement vaut rayon plus k fois sigma`() {
        val verdict = policy().evaluate(listOf(zone), estimateAt(0.0)).single()
        assertEquals(11.0, verdict.triggerDistanceM, 0.0001)
        assertEquals(ZoneState.INSIDE, verdict.state)
    }

    @Test
    fun `etat ambigu entre le rayon et le seuil`() {
        val verdict = policy().evaluate(listOf(zone), estimateAt(8.0)).single()
        assertEquals(ZoneState.AMBIGUOUS, verdict.state)
        assertEquals(3.0, verdict.marginM, 0.05)
    }

    @Test
    fun `sortie confirmee apres trois mesures clairement dehors`() {
        val p = policy()
        p.evaluate(listOf(zone), estimateAt(0.0))                          // armée
        assertFalse(p.evaluate(listOf(zone), estimateAt(15.0)).single().exited)
        assertFalse(p.evaluate(listOf(zone), estimateAt(15.0)).single().exited)
        assertTrue(p.evaluate(listOf(zone), estimateAt(15.0)).single().exited)
    }

    @Test
    fun `une mesure ambigue casse la serie de confirmations`() {
        val p = policy()
        p.evaluate(listOf(zone), estimateAt(0.0))
        p.evaluate(listOf(zone), estimateAt(15.0))
        p.evaluate(listOf(zone), estimateAt(15.0))
        p.evaluate(listOf(zone), estimateAt(8.0)) // retour en zone ambiguë : compteur remis à zéro
        assertFalse(p.evaluate(listOf(zone), estimateAt(15.0)).single().exited)
    }

    @Test
    fun `une seule alerte par sortie`() {
        val p = policy()
        p.evaluate(listOf(zone), estimateAt(0.0))
        val exits = (1..10).count { p.evaluate(listOf(zone), estimateAt(20.0)).single().exited }
        assertEquals(1, exits)
    }

    @Test
    fun `pas d'alerte quand l'application demarre deja dehors`() {
        val p = policy()
        val exits = (1..10).count { p.evaluate(listOf(zone), estimateAt(30.0)).single().exited }
        assertEquals(0, exits)
    }

    @Test
    fun `l'alerte est rearmee apres un retour dans la zone`() {
        val p = policy()
        p.evaluate(listOf(zone), estimateAt(0.0))
        repeat(3) { p.evaluate(listOf(zone), estimateAt(20.0)) } // 1re alerte
        p.evaluate(listOf(zone), estimateAt(0.0))                // retour
        val second = (1..3).count { p.evaluate(listOf(zone), estimateAt(20.0)).single().exited }
        assertEquals(1, second)
    }

    @Test
    fun `plus le GPS est mauvais plus le seuil est loin`() {
        val good = policy().evaluate(listOf(zone), estimateAt(0.0, sigma = 2.0)).single()
        val bad = policy().evaluate(listOf(zone), estimateAt(0.0, sigma = 15.0)).single()
        assertTrue(bad.triggerDistanceM > good.triggerDistanceM)
        assertEquals(35.0, bad.triggerDistanceM, 0.0001)
    }

    @Test
    fun `sigma est plafonne par le bas`() {
        val verdict = policy().evaluate(listOf(zone), estimateAt(0.0, sigma = 0.1)).single()
        assertEquals(1.0, verdict.sigmaM, 0.0001)
        assertEquals(7.0, verdict.triggerDistanceM, 0.0001)
    }

    @Test
    fun `un rayon de 1 metre est accepte`() {
        val tiny = zone.copy(radiusM = 1)
        val verdict = policy().evaluate(listOf(tiny), estimateAt(0.0, sigma = 1.5)).single()
        assertEquals(4.0, verdict.triggerDistanceM, 0.0001)
    }
}
