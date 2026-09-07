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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtc.client.ui.theme.*

data class ChatPreview(
    val roomId: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unread: Int = 0
)

private val demoChats = listOf(
    ChatPreview("!room1:matrix.org", "Matrix HQ", "Welcome to the community!", "12:40", 3),
    ChatPreview("!room2:matrix.org", "Element X", "Sliding Sync is amazing", "11:15"),
    ChatPreview("!room3:matrix.org", "Crypto chat", "Olm + Megolm discussion", "Вчера", 1),
    ChatPreview("!room4:matrix.org", "Telegram design", "How close can we get?", "Пн"),
)

@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit,
    onLogout: () -> Unit
) {
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
                Text("M", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Matrix Telegram", color = TgTextPrimary, fontWeight = FontWeight.SemiBold)
                Text("online", color = TgTextSecondary, fontSize = 12.sp)
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Выйти", tint = TgTextSecondary)
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

        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(demoChats) { chat ->
                ChatListItem(chat = chat, onClick = { onChatClick(chat.roomId) })
            }
        }
    }
}

@Composable
private fun ChatListItem(chat: ChatPreview, onClick: () -> Unit) {
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
            Text(chat.name.take(1).uppercase(), color = TgTextPrimary, fontWeight = FontWeight.Medium, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chat.name, color = TgTextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(chat.time, color = TgTextMeta, fontSize = 12.sp)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chat.lastMessage, color = TgTextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (chat.unread > 0) {
                    Box(
                        modifier = Modifier.padding(start = 6.dp).clip(CircleShape).background(TgAccent).padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(chat.unread.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
