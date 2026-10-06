package com.noubli.app

import android.content.Context
import com.noubli.app.data.AlertRepository
import com.noubli.app.data.AuthRepository
import com.noubli.app.data.SessionStore
import com.noubli.app.data.TraceRepository
import com.noubli.app.data.ZoneRepository
import com.noubli.app.data.db.NoubliDatabase
import com.noubli.app.domain.sensing.DetectionConfig
import com.noubli.app.domain.sensing.PositionSource
import com.noubli.app.engine.TrackingBus
import com.noubli.app.engine.TrackingEngine
import com.noubli.app.engine.TrackingPipeline
import com.noubli.app.location.CurrentLocationProvider
import com.noubli.app.notification.NotificationHelper
import com.noubli.app.sensors.NotificationAlertChannel
import com.noubli.app.sensors.SystemLocationSource

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

    // --- Moteur de suivi (v2) ---

    /** Réglages de détection : un seul endroit à modifier pour ajuster le comportement. */
    val detectionConfig = DetectionConfig()

    /** Trajets enregistrés depuis l'écran « Suivi en direct ». */
    val traceRepository: TraceRepository by lazy { TraceRepository(database.traceDao()) }

    /** Canal où le moteur réel publie son état ; l'écran « Suivi en direct » l'observe. */
    val trackingBus: TrackingBus by lazy { TrackingBus() }

    /** Source de positions réelle (GPS Android). Point de remplacement pour Fused Location. */
    val positionSource: PositionSource by lazy { SystemLocationSource(appContext) }

    /** Assemble un moteur réel : vrai GPS, vraies alertes (historique + notification). */
    fun createEngine(onError: (Throwable) -> Unit = {}): TrackingEngine = TrackingEngine(
        source = positionSource,
        pipeline = TrackingPipeline.create(detectionConfig),
        bus = trackingBus,
        alerts = NotificationAlertChannel(alertRepository, notifier),
        config = detectionConfig,
        onError = onError
    )
}
