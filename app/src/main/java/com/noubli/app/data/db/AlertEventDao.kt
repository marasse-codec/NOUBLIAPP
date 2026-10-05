package com.noubli.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Accès à l'historique des alertes. */
@Dao
interface AlertEventDao {

    @Insert
    suspend fun insert(event: AlertEventEntity): Long

    /** Alertes d'un utilisateur (via ses zones), de la plus récente à la plus ancienne. */
    @Query(
        """
        SELECT a.*, z.name AS zone_name
        FROM alert_event a
        INNER JOIN zone z ON z.id = a.zone_id
        WHERE z.user_id = :userId
        ORDER BY a.triggered_at DESC
        LIMIT :limit
        """
    )
    fun observeByUser(userId: Long, limit: Int): Flow<List<AlertEventWithZone>>

    /** Ne modifie que les alertes pas encore acquittées (opération idempotente). */
    @Query("UPDATE alert_event SET acknowledged_at = :at WHERE id = :id AND acknowledged_at IS NULL")
    suspend fun acknowledge(id: Long, at: Long)

    @Query("DELETE FROM alert_event WHERE zone_id IN (SELECT id FROM zone WHERE user_id = :userId)")
    suspend fun clearForUser(userId: Long)
}
