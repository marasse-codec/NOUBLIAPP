package com.noubli.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

// ---------------------------------------------------------------------------
// Entités Room = tables SQLite (voir le MLD dans docs/diagrams/mld.puml).
// Toutes les clés étrangères utilisent ON DELETE CASCADE : supprimer un
// utilisateur supprime ses zones, et supprimer une zone supprime ses objets
// et son historique d'alertes.
// ---------------------------------------------------------------------------

/** Compte local. Le nom est unique (stocké en minuscules). */
@Entity(tableName = "app_user", indices = [Index(value = ["username"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String,
    val salt: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

/** Zone surveillée appartenant à un utilisateur. */
@Entity(
    tableName = "zone",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["user_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("user_id")]
)
data class ZoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "radius_m") val radiusM: Int,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

/** Objet à ne pas oublier, rattaché à une zone ; [position] conserve l'ordre de saisie. */
@Entity(
    tableName = "reminder_item",
    foreignKeys = [ForeignKey(
        entity = ZoneEntity::class,
        parentColumns = ["id"],
        childColumns = ["zone_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("zone_id")]
)
data class ReminderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "zone_id") val zoneId: Long,
    val label: String,
    val position: Int
)

/**
 * Historique des alertes émises.
 * [itemsSummary] est volontairement dénormalisé : il fige la liste des objets
 * au moment de l'alerte, même si la zone est modifiée ensuite.
 */
@Entity(
    tableName = "alert_event",
    foreignKeys = [ForeignKey(
        entity = ZoneEntity::class,
        parentColumns = ["id"],
        childColumns = ["zone_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("zone_id")]
)
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "zone_id") val zoneId: Long,
    @ColumnInfo(name = "triggered_at") val triggeredAt: Long,
    @ColumnInfo(name = "distance_m") val distanceM: Int,
    @ColumnInfo(name = "items_summary") val itemsSummary: String,
    @ColumnInfo(name = "acknowledged_at") val acknowledgedAt: Long? = null
)

// --- Objets de lecture (jointures), non stockés comme tables ---

/** Une zone et ses objets (relation 1-N résolue par Room). */
data class ZoneWithItemsEntity(
    @Embedded val zone: ZoneEntity,
    @Relation(parentColumn = "id", entityColumn = "zone_id")
    val items: List<ReminderItemEntity>
)

/** Une alerte avec le nom de sa zone (résultat d'une jointure SQL). */
data class AlertEventWithZone(
    @Embedded val event: AlertEventEntity,
    @ColumnInfo(name = "zone_name") val zoneName: String
)
