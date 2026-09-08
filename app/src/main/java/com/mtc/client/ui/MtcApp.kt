package com.mtc.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mtc.client.ui.chat.ChatListScreen
import com.mtc.client.ui.chat.ChatScreen
import com.mtc.client.ui.login.AccountProviderScreen
import com.mtc.client.ui.login.AuthStep
import com.mtc.client.ui.login.AuthViewModel
import com.mtc.client.ui.login.LoginMethodsScreen
import com.mtc.client.ui.login.PasswordAuthScreen

@Composable
fun MtcApp(authViewModel: AuthViewModel) {
    val state by authViewModel.state.collectAsState()
    val context = LocalContext.current

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
            identityProviders = authViewModel.identityProviders(),
            isLoading = state.isLoading,
            error = state.error,
            onBack = { authViewModel.backToProvider() },
            onPasswordLogin = { authViewModel.goPassword() },
            onRegister = { authViewModel.goRegister() },
            onSso = { idp -> authViewModel.startSso(context, idp) }
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

        AuthStep.LOGGED_IN -> {
            val navController = rememberNavController()
            val session = state.session
            NavHost(navController = navController, startDestination = "chats") {
                composable("chats") {
                    ChatListScreen(
                        userId = session?.userId ?: "",
                        rooms = state.rooms,
                        roomsLoading = state.roomsLoading,
                        roomsError = state.roomsError,
                        onChatClick = { roomId -> navController.navigate("chat/$roomId") },
                        onLogout = { authViewModel.logout() },
                        onRefresh = { authViewModel.refreshRooms() }
                    )
                }
                composable("chat/{roomId}") { backStackEntry ->
                    val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
                    val room = state.rooms.find { it.roomId == roomId }
                    ChatScreen(
                        roomId = roomId,
                        roomName = room?.name ?: roomId,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
