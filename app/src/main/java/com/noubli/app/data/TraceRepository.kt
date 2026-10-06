package com.noubli.app.data

import com.noubli.app.data.db.TraceDao
import com.noubli.app.data.db.TraceEntity
import com.noubli.app.data.db.TracePointEntity
import com.noubli.app.domain.model.TracePoint
import kotlinx.coroutines.flow.Flow

/** Enregistrement des trajets de test (voir [com.noubli.app.replay.TraceRecorder]). */
class TraceRepository(
    private val dao: TraceDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /** Crée un trajet vide pour [zoneId] et renvoie son identifiant. */
    suspend fun begin(zoneId: Long, simulated: Boolean): Long =
        dao.insertTrace(TraceEntity(zoneId = zoneId, startedAt = clock(), simulated = simulated))

    suspend fun add(traceId: Long, point: TracePoint) = dao.insertPoint(
        TracePointEntity(
            traceId = traceId,
            timeMs = point.timeMs,
            latitude = point.latitude,
            longitude = point.longitude,
            sigmaM = point.sigmaM,
            distanceM = point.distanceM,
            pathM = point.pathM
        )
    )

    fun observeTraceCount(zoneId: Long): Flow<Int> = dao.observeTraceCount(zoneId)
}
