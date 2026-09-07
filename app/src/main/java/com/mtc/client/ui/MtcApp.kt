package com.mtc.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mtc.client.ui.chat.ChatListScreen
import com.mtc.client.ui.chat.ChatScreen
import com.mtc.client.ui.login.LoginScreen

@Composable
fun MtcApp() {
    var isLoggedIn by remember { mutableStateOf(false) }
    val navController = rememberNavController()

    if (!isLoggedIn) {
        LoginScreen(onLoginSuccess = { isLoggedIn = true })
    } else {
        NavHost(navController = navController, startDestination = "chats") {
            composable("chats") {
                ChatListScreen(
                    onChatClick = { roomId -> navController.navigate("chat/$roomId") },
                    onLogout = { isLoggedIn = false }
                )
            }
            composable("chat/{roomId}") { backStackEntry ->
                val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
                ChatScreen(roomId = roomId, onBack = { navController.popBackStack() })
            }
        }
    }
}
