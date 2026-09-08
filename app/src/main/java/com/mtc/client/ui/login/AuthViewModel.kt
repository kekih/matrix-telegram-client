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
    private val prefs = app.getSharedPreferences("mtc_oidc_v3", Context.MODE_PRIVATE)

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
            startOidc(context, forRegistration = false)
            return
        }
        val url = matrix.ssoRedirectUrl(hs.baseUrl, "mtc://login", idp?.id)
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
    }

    /** Opens system browser / Custom Tabs — no WebView */
    fun startOidc(context: Context, forRegistration: Boolean = false) {
        val hs = _state.value.homeserver ?: return
        val issuer = hs.oidcIssuer ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val config = OidcAuth.discoverOidc(issuer)
                val cacheKey = "client_id_${issuer}_v3"
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
                savePending(pending)
                // Persist homeserver for callback if process is killed
                prefs.edit()
                    .putString("pending_hs", hs.baseUrl)
                    .putString("pending_issuer", issuer)
                    .apply()
                _state.value = _state.value.copy(isLoading = false)
                CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                    .launchUrl(context, Uri.parse(authUrl))
            } catch (e: Exception) {
                prefs.edit().remove("client_id_${issuer}_v3").apply()
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "OIDC login failed"
                )
            }
        }
    }

    /** Deep link mtc://login?code=...&state=... (from HTTPS bridge page) */
    fun handleSsoCallback(uri: Uri?) {
        if (uri == null || uri.scheme != "mtc") return

        val error = uri.getQueryParameter("error")
        if (error != null) {
            _state.value = _state.value.copy(error = error)
            clearPending()
            return
        }

        val code = uri.getQueryParameter("code")
        if (code != null) {
            val pending = loadPending()
            if (pending == null) {
                _state.value = _state.value.copy(error = "Сессия OIDC истекла — нажмите вход ещё раз")
                return
            }
            val state = uri.getQueryParameter("state")
            if (state != null && state != pending.state) {
                _state.value = _state.value.copy(error = "OIDC state mismatch")
                clearPending()
                return
            }
            viewModelScope.launch {
                _state.value = _state.value.copy(isLoading = true, error = null)
                try {
                    val tokens = OidcAuth.exchangeCode(pending, code)
                    val userId = OidcAuth.whoami(tokens.homeserverUrl, tokens.accessToken)
                    clearPending()
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

        // Classic SSO loginToken
        val token = uri.getQueryParameter("loginToken") ?: return
        val hs = _state.value.homeserver
        if (hs == null) {
            _state.value = _state.value.copy(error = "Нет homeserver для SSO")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                onLoggedIn(matrix.loginWithToken(hs.baseUrl, token))
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "SSO failed")
            }
        }
    }

    private fun savePending(p: OidcPendingAuth) {
        prefs.edit()
            .putString("p_verifier", p.codeVerifier)
            .putString("p_state", p.state)
            .putString("p_device", p.deviceId)
            .putString("p_hs", p.homeserverUrl)
            .putString("p_token_ep", p.tokenEndpoint)
            .putString("p_client", p.clientId)
            .putString("p_redirect", p.redirectUri)
            .apply()
    }

    private fun loadPending(): OidcPendingAuth? {
        val verifier = prefs.getString("p_verifier", null) ?: return null
        val state = prefs.getString("p_state", null) ?: return null
        val device = prefs.getString("p_device", null) ?: return null
        val hs = prefs.getString("p_hs", null) ?: return null
        val tokenEp = prefs.getString("p_token_ep", null) ?: return null
        val client = prefs.getString("p_client", null) ?: return null
        return OidcPendingAuth(
            codeVerifier = verifier,
            state = state,
            deviceId = device,
            homeserverUrl = hs,
            tokenEndpoint = tokenEp,
            clientId = client,
            redirectUri = prefs.getString("p_redirect", OIDC_REDIRECT_URI) ?: OIDC_REDIRECT_URI
        )
    }

    private fun clearPending() {
        prefs.edit()
            .remove("p_verifier").remove("p_state").remove("p_device")
            .remove("p_hs").remove("p_token_ep").remove("p_client").remove("p_redirect")
            .apply()
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
        clearPending()
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
