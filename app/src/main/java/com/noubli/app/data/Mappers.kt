package com.noubli.app.data

import com.noubli.app.data.db.AlertEventWithZone
import com.noubli.app.data.db.UserEntity
import com.noubli.app.data.db.ZoneEntity
import com.noubli.app.data.db.ZoneWithItemsEntity
import com.noubli.app.domain.model.AlertEvent
import com.noubli.app.domain.model.ReminderItem
import com.noubli.app.domain.model.User
import com.noubli.app.domain.model.Zone

// Conversions entités Room <-> modèles métier.
// Elles isolent le reste de l'application du schéma de la base de données.

internal fun UserEntity.toDomain() = User(id = id, username = username)

internal fun ZoneWithItemsEntity.toDomain() = Zone(
    id = zone.id,
    userId = zone.userId,
    name = zone.name,
    latitude = zone.latitude,
    longitude = zone.longitude,
    radiusM = zone.radiusM,
    isActive = zone.isActive,
    createdAt = zone.createdAt,
    // Les objets sont renvoyés dans l'ordre de saisie.
    items = items.sortedBy { it.position }.map { ReminderItem(id = it.id, label = it.label) }
)

/** [now] sert de date de création pour une nouvelle zone (createdAt == 0). */
internal fun Zone.toEntity(now: Long) = ZoneEntity(
    id = id,
    userId = userId,
    name = name.trim(),
    latitude = latitude,
    longitude = longitude,
    radiusM = radiusM,
    isActive = isActive,
    createdAt = if (createdAt == 0L) now else createdAt
)

internal fun AlertEventWithZone.toDomain() = AlertEvent(
    id = event.id,
    zoneId = event.zoneId,
    zoneName = zoneName,
    triggeredAt = event.triggeredAt,
    distanceM = event.distanceM,
    itemsSummary = event.itemsSummary,
    acknowledgedAt = event.acknowledgedAt
)
