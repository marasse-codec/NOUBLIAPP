package com.noubli.app.ui.zone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.noubli.app.AppContainer
import com.noubli.app.data.SessionStore
import com.noubli.app.data.ZoneRepository
import com.noubli.app.domain.Validators
import com.noubli.app.domain.model.ReminderItem
import com.noubli.app.domain.model.Zone
import com.noubli.app.location.CurrentLocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/** Contenu du formulaire. Latitude/longitude restent du texte tant qu'elles ne sont pas validées. */
data class ZoneForm(
    val name: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val radius: Float = Validators.DEFAULT_RADIUS_M.toFloat(),
    val items: List<String> = emptyList(),
    val isActive: Boolean = true
)

/** État complet de l'écran de création / modification d'une zone. */
data class ZoneEditUiState(
    val form: ZoneForm = ZoneForm(),
    val isNew: Boolean = true,
    val locating: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

/** Logique du formulaire de zone : chargement, saisie, géolocalisation et enregistrement. */
class ZoneEditViewModel(
    private val zones: ZoneRepository,
    private val session: SessionStore,
    private val locationProvider: CurrentLocationProvider,
    private val zoneId: Long
) : ViewModel() {

    private val _state = MutableStateFlow(ZoneEditUiState(isNew = zoneId == NEW_ZONE_ID))
    val state: StateFlow<ZoneEditUiState> = _state.asStateFlow()

    /** Zone d'origine en cas de modification (conserve id et date de création). */
    private var original: Zone? = null

    init {
        if (zoneId != NEW_ZONE_ID) loadExistingZone()
    }

    private fun loadExistingZone() {
        val userId = session.userId.value ?: return
        viewModelScope.launch {
            val zone = zones.findZone(zoneId, userId) ?: return@launch
            original = zone
            _state.update {
                it.copy(
                    form = ZoneForm(
                        name = zone.name,
                        latitude = formatCoordinate(zone.latitude),
                        longitude = formatCoordinate(zone.longitude),
                        radius = zone.radiusM.toFloat(),
                        items = zone.items.map { item -> item.label },
                        isActive = zone.isActive
                    )
                )
            }
        }
    }

    // --- Saisie : chaque modification efface l'erreur affichée ---

    private fun updateForm(transform: (ZoneForm) -> ZoneForm) {
        _state.update { it.copy(form = transform(it.form), error = null) }
    }

    fun onNameChange(value: String) = updateForm { it.copy(name = value) }
    fun onLatitudeChange(value: String) = updateForm { it.copy(latitude = value) }
    fun onLongitudeChange(value: String) = updateForm { it.copy(longitude = value) }
    fun onRadiusChange(value: Float) = updateForm { it.copy(radius = value) }
    fun onActiveChange(value: Boolean) = updateForm { it.copy(isActive = value) }

    /** Ajoute un objet ; l'erreur est affichée si le libellé est invalide. */
    fun addItem(label: String) {
        val error = Validators.itemLabelError(label)
        if (error != null) return showError(error)
        updateForm { it.copy(items = it.items + label.trim()) }
    }

    fun removeItem(index: Int) = updateForm { form ->
        form.copy(items = form.items.filterIndexed { i, _ -> i != index })
    }

    fun showError(message: String) {
        _state.update { it.copy(error = message) }
    }

    /** Remplit latitude/longitude avec la position actuelle du téléphone. */
    fun useCurrentLocation() {
        if (_state.value.locating) return
        viewModelScope.launch {
            _state.update { it.copy(locating = true, error = null) }
            val fix = locationProvider.getCurrent()
            _state.update { current ->
                if (fix == null) {
                    current.copy(
                        locating = false,
                        error = "Position indisponible : active le GPS, sors à l'air libre puis réessaie."
                    )
                } else {
                    current.copy(
                        locating = false,
                        form = current.form.copy(
                            latitude = formatCoordinate(fix.latitude),
                            longitude = formatCoordinate(fix.longitude)
                        )
                    )
                }
            }
        }
    }

    /** Valide puis enregistre la zone ; `saved` passe à vrai pour fermer l'écran. */
    fun save() {
        val userId = session.userId.value ?: return
        val form = _state.value.form
        val latitude = parseCoordinate(form.latitude)
        val longitude = parseCoordinate(form.longitude)

        val error = Validators.zoneNameError(form.name)
            ?: Validators.itemsError(form.items)
            ?: Validators.latitudeError(latitude)
            ?: Validators.longitudeError(longitude)
            ?: Validators.radiusError(form.radius.roundToInt())
        if (error != null || latitude == null || longitude == null) {
            showError(error ?: "Position invalide")
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            val base = original
            zones.save(
                Zone(
                    id = base?.id ?: 0,
                    userId = userId,
                    name = form.name.trim(),
                    latitude = latitude,
                    longitude = longitude,
                    radiusM = form.radius.roundToInt(),
                    isActive = form.isActive,
                    createdAt = base?.createdAt ?: 0,
                    items = form.items.map { ReminderItem(label = it) }
                )
            )
            _state.update { it.copy(saving = false, saved = true) }
        }
    }

    /** Accepte la virgule ou le point comme séparateur décimal. */
    private fun parseCoordinate(raw: String): Double? =
        raw.trim().replace(',', '.').toDoubleOrNull()

    /** 6 décimales (~10 cm) ; Locale.US garantit le point comme séparateur. */
    private fun formatCoordinate(value: Double): String = "%.6f".format(Locale.US, value)

    companion object {
        /** Valeur de zoneId signalant une création (aucune zone existante). */
        const val NEW_ZONE_ID = -1L

        fun factory(container: AppContainer, zoneId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ZoneEditViewModel(
                    container.zoneRepository,
                    container.session,
                    container.locationProvider,
                    zoneId
                )
            }
        }
    }
}
