package com.noubli.app.data

import com.noubli.app.data.db.ZoneDao
import com.noubli.app.domain.model.Zone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Point d'accès unique aux zones et à leurs objets pour le reste de l'application. */
class ZoneRepository(
    private val zoneDao: ZoneDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /** Toutes les zones de l'utilisateur (flux mis à jour automatiquement). */
    fun observeZones(userId: Long): Flow<List<Zone>> =
        zoneDao.observeByUser(userId).map { rows -> rows.map { it.toDomain() } }

    /** Zones actives uniquement, observées par le service de surveillance. */
    fun observeActiveZones(userId: Long): Flow<List<Zone>> =
        zoneDao.observeActiveByUser(userId).map { rows -> rows.map { it.toDomain() } }

    suspend fun findZone(zoneId: Long, userId: Long): Zone? =
        zoneDao.findById(zoneId, userId)?.toDomain()

    /**
     * Crée ou met à jour une zone (id == 0 pour une création) avec ses objets.
     * Les libellés vides sont ignorés. Renvoie l'identifiant de la zone.
     */
    suspend fun save(zone: Zone): Long {
        val labels = zone.items.map { it.label.trim() }.filter { it.isNotEmpty() }
        return zoneDao.saveWithItems(zone.toEntity(clock()), labels)
    }

    suspend fun setActive(zoneId: Long, userId: Long, active: Boolean) =
        zoneDao.setActive(zoneId, userId, active)

    suspend fun delete(zoneId: Long, userId: Long) = zoneDao.delete(zoneId, userId)
}
