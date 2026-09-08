package com.mtc.client.ui.login

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtc.client.data.SessionRepository
import com.mtc.client.matrix.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthStep {
    PROVIDER, METHODS, PASSWORD, REGISTER, LOGGED_IN
}

data class AuthUiState(
    val step: AuthStep = AuthStep.PROVIDER,
    val providerInput: String = "matrix.org",
    val homeserver: HomeserverConfig? = null,
    val flows: List<LoginFlow> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val session: MatrixSession? = null,
    val rooms: List<RoomSummary> = emptyList()
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val matrix = MatrixClient()
    private val sessions = SessionRepository(app.applicationContext)

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        sessions.load()?.let { session ->
            _state.value = _state.value.copy(step = AuthStep.LOGGED_IN, session = session)
            refreshRooms()
        }
    }

    fun discover(provider: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, providerInput = provider)
            try {
                val hs = matrix.discoverHomeserver(provider)
                val flows = matrix.getLoginFlows(hs.baseUrl)
                _state.value = _state.value.copy(
                    isLoading = false,
                    homeserver = hs,
                    flows = flows,
                    step = AuthStep.METHODS
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Не удалось подключиться к серверу"
                )
            }
        }
    }

    fun backToProvider() {
        _state.value = _state.value.copy(step = AuthStep.PROVIDER, error = null)
    }

    fun goPassword() {
        _state.value = _state.value.copy(step = AuthStep.PASSWORD, error = null)
    }

    fun goRegister() {
        _state.value = _state.value.copy(step = AuthStep.REGISTER, error = null)
    }

    fun backToMethods() {
        _state.value = _state.value.copy(step = AuthStep.METHODS, error = null)
    }

    fun loginPassword(username: String, password: String) {
        val hs = _state.value.homeserver ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val session = matrix.loginPassword(hs.baseUrl, username, password)
                onLoggedIn(session)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка входа"
                )
            }
        }
    }

    fun register(username: String, password: String) {
        val hs = _state.value.homeserver ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val session = matrix.register(hs.baseUrl, username, password)
                onLoggedIn(session)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Регистрация недоступна на этом сервере"
                )
            }
        }
    }

    /** Open Custom Tab for classic Matrix SSO */
    fun startSso(context: Context, idp: IdentityProvider?) {
        val hs = _state.value.homeserver ?: return
        val redirect = "mtc://login"
        val url = matrix.ssoRedirectUrl(hs.baseUrl, redirect, idp?.id)
        val intent = CustomTabsIntent.Builder().build()
        intent.launchUrl(context, Uri.parse(url))
    }

    /** Handle deep link mtc://login?loginToken=... */
    fun handleSsoCallback(uri: Uri?) {
        if (uri == null || uri.scheme != "mtc") return
        val token = uri.getQueryParameter("loginToken") ?: return
        val hs = _state.value.homeserver
        if (hs == null) {
            _state.value = _state.value.copy(error = "Нет homeserver для SSO callback")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val session = matrix.loginWithToken(hs.baseUrl, token)
                onLoggedIn(session)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "SSO login failed"
                )
            }
        }
    }

    private fun onLoggedIn(session: MatrixSession) {
        sessions.save(session)
        _state.value = _state.value.copy(
            isLoading = false,
            session = session,
            step = AuthStep.LOGGED_IN,
            error = null
        )
        refreshRooms()
    }

    fun refreshRooms() {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            try {
                val rooms = matrix.syncRooms(session)
                _state.value = _state.value.copy(rooms = rooms)
            } catch (_: Exception) {
                // keep previous list
            }
        }
    }

    fun logout() {
        sessions.clear()
        _state.value = AuthUiState()
    }

    fun hasPasswordFlow(): Boolean =
        _state.value.flows.any { it.type == "m.login.password" }

    fun hasSsoFlow(): Boolean =
        _state.value.flows.any { it.type == "m.login.sso" || it.type == "m.login.token" }

    fun identityProviders(): List<IdentityProvider> =
        _state.value.flows
            .filter { it.type == "m.login.sso" }
            .flatMap { it.identityProviders }
}
