package com.mtc.client.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtc.client.crypto.Vault
import com.mtc.client.data.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val homeserver: String = "https://matrix.org",
    val username: String = "",
    val password: String = "",
    val masterPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val vault: Vault
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        viewModelScope.launch {
            _isLoggedIn.value = sessionRepository.hasActiveSession()
        }
    }

    fun updateHomeserver(v: String) {
        _uiState.value = _uiState.value.copy(homeserver = v)
    }

    fun updateUsername(v: String) {
        _uiState.value = _uiState.value.copy(username = v)
    }

    fun updatePassword(v: String) {
        _uiState.value = _uiState.value.copy(password = v)
    }

    fun updateMasterPassword(v: String) {
        _uiState.value = _uiState.value.copy(masterPassword = v)
    }

    fun login() {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank() || state.masterPassword.length < 8) {
            _uiState.value = state.copy(error = "Заполните все поля (мастер-пароль ≥ 8 символов)")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            try {
                val masterChars = state.masterPassword.toCharArray()

                // Skeleton: simulate Matrix login. Replace with matrix-rust-sdk.
                val fakeAccessToken = "syt_DEMO_${System.currentTimeMillis()}"
                val fakeUserId = if (state.username.startsWith("@")) state.username
                else "@${state.username}:${state.homeserver.removePrefix("https://").removePrefix("http://")}"

                val sessionJson = """
                    {
                      "accessToken": "$fakeAccessToken",
                      "userId": "$fakeUserId",
                      "homeserver": "${state.homeserver}",
                      "deviceId": "MTCDEMO"
                    }
                """.trimIndent()

                val sealed = vault.sealString(masterChars, sessionJson)
                sessionRepository.saveSealedSession(sealed)
                masterChars.fill('0')

                _isLoggedIn.value = true
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Ошибка входа"
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            sessionRepository.clear()
            _isLoggedIn.value = false
        }
    }
}
