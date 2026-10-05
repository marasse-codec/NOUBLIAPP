package com.noubli.app

import android.app.Application

/**
 * Point d'entrée du processus : crée le conteneur de dépendances et les canaux
 * de notification (nécessaires avant toute notification, y compris celle du service).
 */
class NoubliApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
    }
}
