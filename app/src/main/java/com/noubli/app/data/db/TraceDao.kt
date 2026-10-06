package com.noubli.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Accès aux trajets enregistrés. */
@Dao
interface TraceDao {

    @Insert
    suspend fun insertTrace(trace: TraceEntity): Long

    @Insert
    suspend fun insertPoint(point: TracePointEntity)

    /** Nombre de trajets enregistrés pour une zone (affiché sur l'écran de suivi). */
    @Query("SELECT COUNT(*) FROM trace WHERE zone_id = :zoneId")
    fun observeTraceCount(zoneId: Long): Flow<Int>

    @Query("DELETE FROM trace WHERE zone_id = :zoneId")
    suspend fun deleteForZone(zoneId: Long)
}
