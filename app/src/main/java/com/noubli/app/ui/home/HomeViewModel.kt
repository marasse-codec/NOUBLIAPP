package com.noubli.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.noubli.app.AppContainer
import com.noubli.app.data.AuthRepository
import com.noubli.app.data.SessionStore
import com.noubli.app.data.ZoneRepository
import com.noubli.app.domain.model.Zone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Logique de l'écran d'accueil : liste des zones, activation, suppression, déconnexion. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val zonesRepository: ZoneRepository,
    private val auth: AuthRepository,
    private val session: SessionStore
) : ViewModel() {

    /** Zones de l'utilisateur connecté, mises à jour automatiquement depuis la base. */
    val zones: StateFlow<List<Zone>> = session.userId
        .flatMapLatest { userId ->
            if (userId == null) flowOf(emptyList<Zone>()) else zonesRepository.observeZones(userId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Active ou désactive la surveillance d'une zone (sans la supprimer). */
    fun setZoneActive(zone: Zone, active: Boolean) {
        val userId = session.userId.value ?: return
        viewModelScope.launch { zonesRepository.setActive(zone.id, userId, active) }
    }

    fun deleteZone(zone: Zone) {
        val userId = session.userId.value ?: return
        viewModelScope.launch { zonesRepository.delete(zone.id, userId) }
    }

    fun logout() = auth.logout()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(container.zoneRepository, container.authRepository, container.session)
            }
        }
    }
}
