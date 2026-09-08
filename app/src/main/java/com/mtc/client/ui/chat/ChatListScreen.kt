package com.mtc.client.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtc.client.matrix.RoomSummary
import com.mtc.client.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatListScreen(
    userId: String,
    rooms: List<RoomSummary>,
    roomsLoading: Boolean = false,
    roomsError: String? = null,
    onChatClick: (String) -> Unit,
    onLogout: () -> Unit,
    onRefresh: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Выйти из аккаунта?") },
            text = {
                Text("Сессия будет удалена с этого устройства. Вы сможете войти снова.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Выйти", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Отмена")
                }
            },
            containerColor = TgSidebar,
            titleContentColor = TgTextPrimary,
            textContentColor = TgTextSecondary
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TgSidebar)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(TgAccent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userId.removePrefix("@").take(1).uppercase().ifEmpty { "M" },
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userId.ifBlank { "Matrix Telegram" },
                    color = TgTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("online", color = TgTextSecondary, fontSize = 12.sp)
            }
            IconButton(onClick = onRefresh) {
                Text("⟳", color = TgTextSecondary, fontSize = 18.sp)
            }
            IconButton(onClick = { showLogoutDialog = true }) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Выйти",
                    tint = TgTextSecondary
                )
            }
        }

        OutlinedTextField(
            value = "",
            onValueChange = {},
            placeholder = { Text("Поиск", color = TgTextMeta) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TgTextMeta) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TgInput,
                unfocusedContainerColor = TgInput,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = TgAccent
            )
        )

        when {
            roomsLoading && rooms.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = TgAccent)
                        Spacer(Modifier.height(12.dp))
                        Text("Загрузка чатов…", color = TgTextSecondary, fontSize = 14.sp)
                    }
                }
            }
            roomsError != null && rooms.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(roomsError, color = Color(0xFFFF6B6B), fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onRefresh) {
                            Text("Повторить", color = TgAccent)
                        }
                    }
                }
            }
            rooms.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Нет комнат\nНажмите ⟳ чтобы обновить",
                        color = TgTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                    items(rooms, key = { it.roomId }) { room ->
                        RoomItem(room, onClick = { onChatClick(room.roomId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomItem(room: RoomSummary, onClick: () -> Unit) {
    val timeLabel = if (room.timestamp > 0) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(room.timestamp))
    } else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(TgInput),
            contentAlignment = Alignment.Center
        ) {
            Text(
                room.name.take(1).uppercase().ifEmpty { "#" },
                color = TgTextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    room.name,
                    color = TgTextPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(timeLabel, color = TgTextMeta, fontSize = 12.sp)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    room.lastMessage.ifBlank { "Нет сообщений" },
                    color = TgTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (room.unread > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(CircleShape)
                            .background(TgAccent)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            room.unread.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
