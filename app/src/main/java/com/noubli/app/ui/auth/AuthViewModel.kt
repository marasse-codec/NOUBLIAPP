package com.noubli.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.noubli.app.AppContainer
import com.noubli.app.data.AuthRepository
import com.noubli.app.domain.model.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de l'écran de connexion / inscription. */
data class AuthUiState(
    val isRegisterMode: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
)

/**
 * Logique de l'écran d'authentification. En cas de succès, la session est mise à jour
 * par le dépôt et la navigation réagit toute seule (voir NoubliNavHost).
 */
class AuthViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    /** Bascule entre « Se connecter » et « Créer un compte ». */
    fun toggleMode() {
        _state.update { it.copy(isRegisterMode = !it.isRegisterMode, error = null) }
    }

    /** Valide le formulaire : inscription ou connexion selon le mode courant. */
    fun submit(username: String, password: String) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = if (_state.value.isRegisterMode) {
                auth.register(username, password)
            } else {
                auth.login(username, password)
            }
            _state.update {
                when (result) {
                    is AuthResult.Success -> it.copy(loading = false)
                    is AuthResult.Failure -> it.copy(loading = false, error = result.error.message)
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(container.authRepository) }
        }
    }
}
