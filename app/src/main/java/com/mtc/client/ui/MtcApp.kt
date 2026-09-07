package com.mtc.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mtc.client.ui.chat.ChatListScreen
import com.mtc.client.ui.chat.ChatScreen
import com.mtc.client.ui.login.LoginScreen
import com.mtc.client.ui.login.LoginViewModel

@Composable
fun MtcApp(
    loginViewModel: LoginViewModel = hiltViewModel()
) {
    val isLoggedIn by loginViewModel.isLoggedIn.collectAsState()
    val navController = rememberNavController()

    if (!isLoggedIn) {
        LoginScreen(viewModel = loginViewModel)
    } else {
        NavHost(
            navController = navController,
            startDestination = "chats"
        ) {
            composable("chats") {
                ChatListScreen(
                    onChatClick = { roomId ->
                        navController.navigate("chat/$roomId")
                    },
                    onLogout = { loginViewModel.logout() }
                )
            }
            composable("chat/{roomId}") { backStackEntry ->
                val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
                ChatScreen(
                    roomId = roomId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
