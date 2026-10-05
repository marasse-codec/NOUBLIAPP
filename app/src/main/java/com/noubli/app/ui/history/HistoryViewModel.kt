package com.noubli.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.noubli.app.AppContainer
import com.noubli.app.data.AlertRepository
import com.noubli.app.data.SessionStore
import com.noubli.app.domain.model.AlertEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Logique de l'écran d'historique : liste des alertes émises et purge. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val alerts: AlertRepository,
    private val session: SessionStore
) : ViewModel() {

    /** Alertes de l'utilisateur connecté, les plus récentes d'abord. */
    val events: StateFlow<List<AlertEvent>> = session.userId
        .flatMapLatest { userId ->
            if (userId == null) flowOf(emptyList<AlertEvent>()) else alerts.observe(userId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Supprime tout l'historique de l'utilisateur. */
    fun clear() {
        val userId = session.userId.value ?: return
        viewModelScope.launch { alerts.clear(userId) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(container.alertRepository, container.session) }
        }
    }
}
