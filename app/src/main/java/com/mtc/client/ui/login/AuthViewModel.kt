package com.mtc.client.ui.login

import android.app.Application
import android.content.Context
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
    PROVIDER, METHODS, PASSWORD, REGISTER, OIDC_WEB, LOGGED_IN
}

data class AuthUiState(
    val step: AuthStep = AuthStep.PROVIDER,
    val providerInput: String = "matrix.org",
    val homeserver: HomeserverConfig? = null,
    val flows: List<LoginFlow> = emptyList(),
    val isLoading: Boolean = false,
    val roomsLoading: Boolean = false,
    val error: String? = null,
    val roomsError: String? = null,
    val session: MatrixSession? = null,
    val rooms: List<RoomSummary> = emptyList(),
    val oidcAuthUrl: String? = null
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val matrix = MatrixClient()
    private val sessions = SessionRepository(app.applicationContext)
    private val prefs = app.getSharedPreferences("mtc_oidc_v2", Context.MODE_PRIVATE)

    private var oidcPending: OidcPendingAuth? = null

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
                val flows = if (hs.usesOidc) emptyList() else matrix.getLoginFlows(hs.baseUrl)
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
        _state.value = _state.value.copy(step = AuthStep.PROVIDER, error = null, oidcAuthUrl = null)
    }

    fun goPassword() {
        _state.value = _state.value.copy(step = AuthStep.PASSWORD, error = null)
    }

    fun goRegister() {
        _state.value = _state.value.copy(step = AuthStep.REGISTER, error = null)
    }

    fun backToMethods() {
        oidcPending = null
        _state.value = _state.value.copy(step = AuthStep.METHODS, error = null, oidcAuthUrl = null)
    }

    fun loginPassword(username: String, password: String) {
        val hs = _state.value.homeserver ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                onLoggedIn(matrix.loginPassword(hs.baseUrl, username, password))
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Ошибка входа")
            }
        }
    }

    fun register(username: String, password: String) {
        val hs = _state.value.homeserver ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                onLoggedIn(matrix.register(hs.baseUrl, username, password))
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Регистрация недоступна на этом сервере"
                )
            }
        }
    }

    fun startSso(context: Context, idp: IdentityProvider?) {
        val hs = _state.value.homeserver ?: return
        if (hs.usesOidc) {
            startOidc(forRegistration = false)
            return
        }
        val url = matrix.ssoRedirectUrl(hs.baseUrl, "mtc://login", idp?.id)
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
    }

    fun startOidc(forRegistration: Boolean = false) {
        val hs = _state.value.homeserver ?: return
        val issuer = hs.oidcIssuer ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val config = OidcAuth.discoverOidc(issuer)
                val cacheKey = "client_id_${issuer}_v2"
                var clientId = prefs.getString(cacheKey, null)
                if (clientId == null) {
                    clientId = OidcAuth.registerClient(config).clientId
                    prefs.edit().putString(cacheKey, clientId).apply()
                }
                val (authUrl, pending) = OidcAuth.beginAuth(
                    config = config,
                    clientId = clientId,
                    homeserverUrl = hs.baseUrl,
                    forRegistration = forRegistration
                )
                oidcPending = pending
                _state.value = _state.value.copy(
                    isLoading = false,
                    step = AuthStep.OIDC_WEB,
                    oidcAuthUrl = authUrl
                )
            } catch (e: Exception) {
                // Drop bad cached client_id so next try re-registers
                prefs.edit().remove("client_id_${issuer}_v2").apply()
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "OIDC login failed"
                )
            }
        }
    }

    fun onOidcRedirect(url: String) {
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error_description")
            ?: uri.getQueryParameter("error")

        if (error != null) {
            _state.value = _state.value.copy(
                step = AuthStep.METHODS,
                oidcAuthUrl = null,
                error = error
            )
            return
        }
        if (code == null) return

        val pending = oidcPending
        if (pending == null) {
            _state.value = _state.value.copy(
                step = AuthStep.METHODS,
                oidcAuthUrl = null,
                error = "Нет pending OIDC сессии"
            )
            return
        }
        if (state != null && state != pending.state) {
            _state.value = _state.value.copy(
                step = AuthStep.METHODS,
                oidcAuthUrl = null,
                error = "OIDC state mismatch"
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, oidcAuthUrl = null)
            try {
                val tokens = OidcAuth.exchangeCode(pending, code)
                val userId = OidcAuth.whoami(tokens.homeserverUrl, tokens.accessToken)
                oidcPending = null
                onLoggedIn(
                    MatrixSession(
                        userId = userId,
                        accessToken = tokens.accessToken,
                        deviceId = tokens.deviceId,
                        homeserverUrl = tokens.homeserverUrl
                    )
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    step = AuthStep.METHODS,
                    error = e.message ?: "OIDC token exchange failed"
                )
            }
        }
    }

    fun handleSsoCallback(uri: Uri?) {
        if (uri == null || uri.scheme != "mtc") return
        val token = uri.getQueryParameter("loginToken") ?: return
        val hs = _state.value.homeserver ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                onLoggedIn(matrix.loginWithToken(hs.baseUrl, token))
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "SSO failed")
            }
        }
    }

    private fun onLoggedIn(session: MatrixSession) {
        sessions.save(session)
        _state.value = _state.value.copy(
            isLoading = false,
            session = session,
            step = AuthStep.LOGGED_IN,
            error = null,
            oidcAuthUrl = null
        )
        refreshRooms()
    }

    fun refreshRooms() {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(roomsLoading = true, roomsError = null)
            try {
                val rooms = matrix.loadRooms(session)
                _state.value = _state.value.copy(
                    rooms = rooms,
                    roomsLoading = false,
                    roomsError = if (rooms.isEmpty()) "Комнат не найдено" else null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    roomsLoading = false,
                    roomsError = e.message ?: "Не удалось загрузить чаты"
                )
            }
        }
    }

    fun logout() {
        sessions.clear()
        oidcPending = null
        _state.value = AuthUiState()
    }

    fun hasPasswordFlow(): Boolean =
        _state.value.flows.any { it.type == "m.login.password" }

    fun hasSsoFlow(): Boolean =
        _state.value.flows.any { it.type == "m.login.sso" || it.type == "m.login.token" }

    fun usesOidc(): Boolean = _state.value.homeserver?.usesOidc == true

    fun identityProviders(): List<IdentityProvider> =
        _state.value.flows.filter { it.type == "m.login.sso" }.flatMap { it.identityProviders }
}
