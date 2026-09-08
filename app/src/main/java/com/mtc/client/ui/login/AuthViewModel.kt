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
    PROVIDER, METHODS, PASSWORD, REGISTER, LOGGED_IN
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
    val rooms: List<RoomSummary> = emptyList()
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val matrix = MatrixClient()
    private val sessions = SessionRepository(app.applicationContext)
    private val prefs = app.getSharedPreferences("mtc_oidc", Context.MODE_PRIVATE)

    /** Pending OIDC auth (PKCE) while browser is open */
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
                onLoggedIn(matrix.loginPassword(hs.baseUrl, username, password))
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
                onLoggedIn(matrix.register(hs.baseUrl, username, password))
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Регистрация недоступна на этом сервере"
                )
            }
        }
    }

    /** Classic Matrix SSO */
    fun startSso(context: Context, idp: IdentityProvider?) {
        val hs = _state.value.homeserver ?: return
        if (hs.usesOidc) {
            startOidc(context, forRegistration = false)
            return
        }
        val url = matrix.ssoRedirectUrl(hs.baseUrl, OidcAuth.REDIRECT_URI, idp?.id)
        CustomTabsIntent.Builder().build().launchUrl(context, android.net.Uri.parse(url))
    }

    /** Modern OIDC / MAS login (matrix.nevetime.ru → auth.matrix.nevetime.ru) */
    fun startOidc(context: Context, forRegistration: Boolean = false) {
        val hs = _state.value.homeserver ?: return
        val issuer = hs.oidcIssuer ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val config = OidcAuth.discoverOidc(issuer)
                val clientId = prefs.getString("client_id_$issuer", null)
                    ?: OidcAuth.registerClient(config).clientId.also {
                        prefs.edit().putString("client_id_$issuer", it).apply()
                    }
                val (authUrl, pending) = OidcAuth.beginAuth(
                    config = config,
                    clientId = clientId,
                    homeserverUrl = hs.baseUrl,
                    forRegistration = forRegistration
                )
                oidcPending = pending
                _state.value = _state.value.copy(isLoading = false)
                CustomTabsIntent.Builder().build()
                    .launchUrl(context, Uri.parse(authUrl))
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "OIDC login failed"
                )
            }
        }
    }

    /** Handle deep link mtc://login?... */
    fun handleSsoCallback(uri: Uri?) {
        if (uri == null || uri.scheme != "mtc") return

        // OIDC: ?code=...&state=...
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        if (code != null) {
            val pending = oidcPending
            if (pending == null) {
                _state.value = _state.value.copy(error = "Нет pending OIDC сессии. Повторите вход.")
                return
            }
            if (state != null && state != pending.state) {
                _state.value = _state.value.copy(error = "OIDC state mismatch")
                return
            }
            viewModelScope.launch {
                _state.value = _state.value.copy(isLoading = true, error = null)
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
                        error = e.message ?: "OIDC token exchange failed"
                    )
                }
            }
            return
        }

        // Classic SSO: ?loginToken=...
        val token = uri.getQueryParameter("loginToken") ?: return
        val hs = _state.value.homeserver
        if (hs == null) {
            _state.value = _state.value.copy(error = "Нет homeserver для SSO callback")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                onLoggedIn(matrix.loginWithToken(hs.baseUrl, token))
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
        _state.value.flows
            .filter { it.type == "m.login.sso" }
            .flatMap { it.identityProviders }
}
