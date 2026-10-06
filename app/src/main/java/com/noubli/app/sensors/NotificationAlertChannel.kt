package com.noubli.app.sensors

import com.noubli.app.data.AlertRepository
import com.noubli.app.domain.model.ZoneExit
import com.noubli.app.engine.AlertChannel
import com.noubli.app.notification.NotificationHelper

/** Alerte réelle : enregistre la sortie dans l'historique puis affiche la notification (son + vibration). */
class NotificationAlertChannel(
    private val alertRepository: AlertRepository,
    private val notifier: NotificationHelper
) : AlertChannel {

    override suspend fun alert(exit: ZoneExit) {
        val event = alertRepository.record(exit.zone, exit.distanceM)
        notifier.showForgetAlert(event, exit.zone)
    }
}
