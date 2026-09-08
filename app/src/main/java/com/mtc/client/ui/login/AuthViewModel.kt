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
    val rooms: List<RoomSummary> = emptyList(),
    val activeRoomId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val messagesLoading: Boolean = false,
    val messagesError: String? = null,
    val profile: UserProfile? = null,
    val profileLoading: Boolean = false,
    val profileSaving: Boolean = false,
    val profileError: String? = null
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val matrix = MatrixClient()
    private val sessions = SessionRepository(app.applicationContext)
    private val prefs = app.getSharedPreferences("mtc_oidc_v4", Context.MODE_PRIVATE)

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

    fun startOidc(context: Context, forRegistration: Boolean = false) {
        val hs = _state.value.homeserver ?: return
        val issuer = hs.oidcIssuer ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val config = OidcAuth.discoverOidc(issuer)
                val cacheKey = "client_id_${issuer}_v4"
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
                OidcAuth.startLoopbackServer()
                CustomTabsIntent.Builder().setShowTitle(true).build()
                    .launchUrl(context, Uri.parse(authUrl))

                val query = OidcAuth.awaitLoopbackQuery(180_000L)
                val params = OidcAuth.parseQuery(query)
                val err = params["error_description"] ?: params["error"]
                if (err != null) {
                    _state.value = _state.value.copy(isLoading = false, error = err)
                    return@launch
                }
                val code = params["code"]
                    ?: run {
                        _state.value = _state.value.copy(isLoading = false, error = "Нет code в callback")
                        return@launch
                    }
                val state = params["state"]
                if (state != null && state != pending.state) {
                    _state.value = _state.value.copy(isLoading = false, error = "OIDC state mismatch")
                    return@launch
                }
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
                OidcAuth.stopLoopbackServer()
                prefs.edit().remove("client_id_${issuer}_v4").apply()
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "OIDC login failed"
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

    fun openRoom(roomId: String) {
        _state.value = _state.value.copy(activeRoomId = roomId, messages = emptyList())
        loadMessages()
    }

    fun loadMessages() {
        val session = _state.value.session ?: return
        val roomId = _state.value.activeRoomId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(messagesLoading = true, messagesError = null)
            try {
                val msgs = matrix.loadMessages(session, roomId)
                _state.value = _state.value.copy(messages = msgs, messagesLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    messagesLoading = false,
                    messagesError = e.message ?: "Не удалось загрузить сообщения"
                )
            }
        }
    }

    fun sendMessage(text: String) {
        val session = _state.value.session ?: return
        val roomId = _state.value.activeRoomId ?: return
        viewModelScope.launch {
            try {
                matrix.sendText(session, roomId, text)
                loadMessages()
                refreshRooms()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    messagesError = e.message ?: "Не удалось отправить"
                )
            }
        }
    }

    fun loadProfile() {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(profileLoading = true, profileError = null)
            try {
                val p = matrix.getProfile(session)
                _state.value = _state.value.copy(profile = p, profileLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    profileLoading = false,
                    profileError = e.message ?: "Не удалось загрузить профиль"
                )
            }
        }
    }

    fun saveDisplayName(name: String) {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(profileSaving = true, profileError = null)
            try {
                matrix.setDisplayName(session, name)
                val p = matrix.getProfile(session)
                _state.value = _state.value.copy(profile = p, profileSaving = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    profileSaving = false,
                    profileError = e.message ?: "Не удалось сохранить имя"
                )
            }
        }
    }

    fun uploadAvatarBytes(bytes: ByteArray, mime: String = "image/jpeg") {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(profileSaving = true, profileError = null)
            try {
                val mxc = matrix.uploadAvatar(session, bytes, mime)
                matrix.setAvatarUrl(session, mxc)
                val p = matrix.getProfile(session)
                _state.value = _state.value.copy(profile = p, profileSaving = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    profileSaving = false,
                    profileError = e.message ?: "Не удалось загрузить аватар"
                )
            }
        }
    }

    fun logout() {
        sessions.clear()
        oidcPending = null
        OidcAuth.stopLoopbackServer()
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
