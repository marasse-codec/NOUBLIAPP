package com.noubli.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Accès aux zones et à leurs objets.
 * Toutes les requêtes filtrent sur user_id : un utilisateur ne voit jamais les zones d'un autre.
 */
@Dao
abstract class ZoneDao {

    /** Flux réactif : l'écran se met à jour automatiquement à chaque modification. */
    @Transaction
    @Query("SELECT * FROM zone WHERE user_id = :userId ORDER BY name COLLATE NOCASE")
    abstract fun observeByUser(userId: Long): Flow<List<ZoneWithItemsEntity>>

    /** Zones actives uniquement : c'est ce que le service de surveillance observe. */
    @Transaction
    @Query("SELECT * FROM zone WHERE user_id = :userId AND is_active = 1")
    abstract fun observeActiveByUser(userId: Long): Flow<List<ZoneWithItemsEntity>>

    @Transaction
    @Query("SELECT * FROM zone WHERE id = :zoneId AND user_id = :userId LIMIT 1")
    abstract suspend fun findById(zoneId: Long, userId: Long): ZoneWithItemsEntity?

    /** Insère ou met à jour (sans supprimer la ligne, donc sans cascade sur l'historique). */
    @Upsert
    abstract suspend fun upsertZone(zone: ZoneEntity): Long

    @Insert
    abstract suspend fun insertItems(items: List<ReminderItemEntity>)

    @Query("DELETE FROM reminder_item WHERE zone_id = :zoneId")
    abstract suspend fun deleteItemsOf(zoneId: Long)

    @Query("UPDATE zone SET is_active = :active WHERE id = :zoneId AND user_id = :userId")
    abstract suspend fun setActive(zoneId: Long, userId: Long, active: Boolean)

    @Query("DELETE FROM zone WHERE id = :zoneId AND user_id = :userId")
    abstract suspend fun delete(zoneId: Long, userId: Long)

    /**
     * Enregistre une zone et remplace ses objets dans une seule transaction :
     * soit tout est écrit, soit rien. Renvoie l'identifiant de la zone.
     */
    @Transaction
    open suspend fun saveWithItems(zone: ZoneEntity, labels: List<String>): Long {
        // @Upsert renvoie -1 lors d'une mise à jour : on reprend alors l'id existant.
        val insertedId = upsertZone(zone)
        val zoneId = if (zone.id == 0L) insertedId else zone.id
        deleteItemsOf(zoneId)
        insertItems(labels.mapIndexed { index, label ->
            ReminderItemEntity(zoneId = zoneId, label = label, position = index)
        })
        return zoneId
    }
}
