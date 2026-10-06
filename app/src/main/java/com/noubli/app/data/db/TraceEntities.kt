package com.noubli.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ---------------------------------------------------------------------------
// Trajets enregistrés depuis l'écran « Suivi en direct » (ajoutés en version 2 de la base).
// Ils servent au réglage terrain : chaque trajet est une suite de mesures qu'on pourra rejouer.
// Supprimer une zone supprime ses trajets (ON DELETE CASCADE), comme pour l'historique.
// ---------------------------------------------------------------------------

/** En-tête d'un trajet : la zone suivie, le début, et s'il vient de la simulation. */
@Entity(
    tableName = "trace",
    foreignKeys = [ForeignKey(
        entity = ZoneEntity::class,
        parentColumns = ["id"],
        childColumns = ["zone_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("zone_id")]
)
data class TraceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "zone_id") val zoneId: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    val simulated: Boolean
)

/** Une mesure du trajet : position estimée, incertitude, distance au centre, déplacement cumulé. */
@Entity(
    tableName = "trace_point",
    foreignKeys = [ForeignKey(
        entity = TraceEntity::class,
        parentColumns = ["id"],
        childColumns = ["trace_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("trace_id")]
)
data class TracePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "trace_id") val traceId: Long,
    @ColumnInfo(name = "time_ms") val timeMs: Long,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "sigma_m") val sigmaM: Double,
    @ColumnInfo(name = "distance_m") val distanceM: Double,
    @ColumnInfo(name = "path_m") val pathM: Double
)
