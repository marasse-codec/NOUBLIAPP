package com.noubli.app.data

import com.noubli.app.data.db.AlertEventDao
import com.noubli.app.data.db.AlertEventEntity
import com.noubli.app.domain.model.AlertEvent
import com.noubli.app.domain.model.Zone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Historique des alertes émises. */
class AlertRepository(
    private val dao: AlertEventDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /** Enregistre une alerte pour [zone] et renvoie l'événement créé (avec son id). */
    suspend fun record(zone: Zone, distanceM: Int): AlertEvent {
        val now = clock()
        // Capture figée des objets au moment de l'alerte (voir AlertEventEntity).
        val summary = zone.items.joinToString(", ") { it.label }
        val id = dao.insert(
            AlertEventEntity(
                zoneId = zone.id,
                triggeredAt = now,
                distanceM = distanceM,
                itemsSummary = summary
            )
        )
        return AlertEvent(
            id = id,
            zoneId = zone.id,
            zoneName = zone.name,
            triggeredAt = now,
            distanceM = distanceM,
            itemsSummary = summary
        )
    }

    /** Dernières alertes de l'utilisateur (100 maximum), les plus récentes d'abord. */
    fun observe(userId: Long): Flow<List<AlertEvent>> =
        dao.observeByUser(userId, HISTORY_LIMIT).map { rows -> rows.map { it.toDomain() } }

    /** Marque l'alerte comme acquittée (bouton « J'ai tout »). */
    suspend fun acknowledge(eventId: Long) = dao.acknowledge(eventId, clock())

    suspend fun clear(userId: Long) = dao.clearForUser(userId)

    private companion object {
        const val HISTORY_LIMIT = 100
    }
}
