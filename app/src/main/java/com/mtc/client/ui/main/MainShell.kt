package com.mtc.client.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mtc.client.matrix.ChatMessage
import com.mtc.client.matrix.RoomSummary
import com.mtc.client.matrix.UserProfile
import com.mtc.client.ui.chat.ChatListScreen
import com.mtc.client.ui.chat.ChatScreen
import com.mtc.client.ui.profile.ProfileScreen
import com.mtc.client.ui.settings.SettingsScreen
import com.mtc.client.ui.theme.*
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    CHATS("chats", "Чаты", Icons.Default.ChatBubble),
    SETTINGS("settings", "Настройки", Icons.Default.Settings),
    PROFILE("profile", "Профиль", Icons.Default.Person)
}

@Composable
fun MainShell(
    userId: String,
    rooms: List<RoomSummary>,
    roomsLoading: Boolean,
    roomsError: String?,
    profile: UserProfile?,
    profileLoading: Boolean,
    profileError: String?,
    profileSaving: Boolean,
    messages: List<ChatMessage>,
    messagesLoading: Boolean,
    messagesError: String?,
    onRefreshRooms: () -> Unit,
    onOpenRoom: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onRefreshMessages: () -> Unit,
    onLoadProfile: () -> Unit,
    onSaveDisplayName: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Tab.CHATS.route
    val showBottomBar = currentRoute in Tab.entries.map { it.route }

    Scaffold(
        containerColor = TgBg,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = TgSidebar,
                    tonalElevation = 0.dp
                ) {
                    Tab.entries.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Tab.CHATS.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                BadgedBox(badge = {
                                    if (tab == Tab.CHATS && rooms.sumOf { it.unread } > 0) {
                                        Badge { Text("${rooms.sumOf { it.unread }}") }
                                    }
                                }) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TgAccent,
                                selectedTextColor = TgAccent,
                                unselectedIconColor = TgTextSecondary,
                                unselectedTextColor = TgTextSecondary,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.CHATS.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Tab.CHATS.route) {
                ChatListScreen(
                    userId = userId,
                    rooms = rooms,
                    roomsLoading = roomsLoading,
                    roomsError = roomsError,
                    onChatClick = { roomId ->
                        onOpenRoom(roomId)
                        val encoded = URLEncoder.encode(roomId, StandardCharsets.UTF_8.toString())
                        navController.navigate("chat/$encoded")
                    },
                    onLogout = onLogout,
                    onRefresh = onRefreshRooms
                )
            }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(userId = userId, onLogout = onLogout)
            }
            composable(Tab.PROFILE.route) {
                LaunchedEffect(Unit) { onLoadProfile() }
                ProfileScreen(
                    userId = userId,
                    profile = profile,
                    loading = profileLoading,
                    saving = profileSaving,
                    error = profileError,
                    onSaveName = onSaveDisplayName,
                    onPickAvatar = onPickAvatar
                )
            }
            composable(
                route = "chat/{roomId}",
                arguments = listOf(navArgument("roomId") { type = NavType.StringType })
            ) { entry ->
                val raw = entry.arguments?.getString("roomId") ?: return@composable
                val roomId = URLDecoder.decode(raw, StandardCharsets.UTF_8.toString())
                val room = rooms.find { it.roomId == roomId }
                ChatScreen(
                    roomId = roomId,
                    roomName = room?.name ?: roomId,
                    encrypted = room?.encrypted == true,
                    messages = messages,
                    loading = messagesLoading,
                    error = messagesError,
                    onBack = { navController.popBackStack() },
                    onSend = onSendMessage,
                    onRefresh = onRefreshMessages
                )
            }
        }
    }
}
