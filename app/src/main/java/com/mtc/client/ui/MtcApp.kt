package com.mtc.client.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.mtc.client.ui.login.AccountProviderScreen
import com.mtc.client.ui.login.AuthStep
import com.mtc.client.ui.login.AuthViewModel
import com.mtc.client.ui.login.LoginMethodsScreen
import com.mtc.client.ui.login.PasswordAuthScreen
import com.mtc.client.ui.main.MainShell

@Composable
fun MtcApp(authViewModel: AuthViewModel) {
    val state by authViewModel.state.collectAsState()
    val context = LocalContext.current

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                authViewModel.uploadAvatarBytes(bytes, mime)
            }
        } catch (_: Exception) { }
    }

    when (state.step) {
        AuthStep.PROVIDER -> AccountProviderScreen(
            isLoading = state.isLoading,
            error = state.error,
            onContinue = { authViewModel.discover(it) }
        )

        AuthStep.METHODS -> LoginMethodsScreen(
            serverName = state.homeserver?.serverName ?: state.providerInput,
            hasPassword = authViewModel.hasPasswordFlow(),
            hasSso = authViewModel.hasSsoFlow(),
            usesOidc = authViewModel.usesOidc(),
            identityProviders = authViewModel.identityProviders(),
            isLoading = state.isLoading,
            error = state.error,
            onBack = { authViewModel.backToProvider() },
            onPasswordLogin = { authViewModel.goPassword() },
            onRegister = { authViewModel.goRegister() },
            onSso = { idp -> authViewModel.startSso(context, idp) },
            onOidcLogin = { authViewModel.startOidc(context, forRegistration = false) },
            onOidcRegister = { authViewModel.startOidc(context, forRegistration = true) }
        )

        AuthStep.PASSWORD -> PasswordAuthScreen(
            isRegister = false,
            serverName = state.homeserver?.serverName ?: "",
            isLoading = state.isLoading,
            error = state.error,
            onBack = { authViewModel.backToMethods() },
            onSubmit = { u, p -> authViewModel.loginPassword(u, p) }
        )

        AuthStep.REGISTER -> PasswordAuthScreen(
            isRegister = true,
            serverName = state.homeserver?.serverName ?: "",
            isLoading = state.isLoading,
            error = state.error,
            onBack = { authViewModel.backToMethods() },
            onSubmit = { u, p -> authViewModel.register(u, p) }
        )

        AuthStep.LOGGED_IN -> MainShell(
            userId = state.session?.userId ?: "",
            rooms = state.rooms,
            roomsLoading = state.roomsLoading,
            roomsError = state.roomsError,
            profile = state.profile,
            profileLoading = state.profileLoading,
            profileError = state.profileError,
            profileSaving = state.profileSaving,
            messages = state.messages,
            messagesLoading = state.messagesLoading,
            messagesError = state.messagesError,
            onRefreshRooms = { authViewModel.refreshRooms() },
            onOpenRoom = { authViewModel.openRoom(it) },
            onSendMessage = { authViewModel.sendMessage(it) },
            onRefreshMessages = { authViewModel.loadMessages() },
            onLoadProfile = { authViewModel.loadProfile() },
            onSaveDisplayName = { authViewModel.saveDisplayName(it) },
            onPickAvatar = { avatarPicker.launch("image/*") },
            onLogout = { authViewModel.logout() }
        )
    }
}
