package com.noubli.app

import android.content.Context
import com.noubli.app.data.AlertRepository
import com.noubli.app.data.AuthRepository
import com.noubli.app.data.SessionStore
import com.noubli.app.data.ZoneRepository
import com.noubli.app.data.db.NoubliDatabase
import com.noubli.app.location.CurrentLocationProvider
import com.noubli.app.notification.NotificationHelper

/**
 * Conteneur de dépendances « fait main » : il construit chaque objet partagé une
 * seule fois et le fournit à l'UI, au service et au récepteur de notifications.
 * Suffisant pour un MVP ; Hilt/Koin pourront le remplacer sans toucher au reste.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: NoubliDatabase by lazy { NoubliDatabase.create(appContext) }

    val session: SessionStore by lazy { SessionStore(appContext) }

    val authRepository: AuthRepository by lazy { AuthRepository(database.userDao(), session) }
    val zoneRepository: ZoneRepository by lazy { ZoneRepository(database.zoneDao()) }
    val alertRepository: AlertRepository by lazy { AlertRepository(database.alertEventDao()) }

    val notifier: NotificationHelper by lazy { NotificationHelper(appContext) }
    val locationProvider: CurrentLocationProvider by lazy { CurrentLocationProvider(appContext) }
}
