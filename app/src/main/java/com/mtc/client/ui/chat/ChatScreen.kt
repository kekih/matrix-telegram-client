package com.mtc.client.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtc.client.ui.theme.*

data class Message(
    val id: String,
    val body: String,
    val isMine: Boolean,
    val time: String,
    val sender: String = ""
)

private val demoMessages = listOf(
    Message("1", "Привет! Это демо-чат Matrix Telegram Client.", false, "12:30", "alice"),
    Message("2", "UI сделан в стиле Telegram, а под капотом — Matrix E2EE.", false, "12:31", "alice"),
    Message("3", "Супер! Локальный vault на Argon2id + AES-GCM тоже работает.", true, "12:32"),
    Message("4", "Да, как в Bitwarden. Мастер-пароль никогда не уходит на сервер.", false, "12:33", "alice"),
    Message("5", "Когда подключим matrix-rust-sdk — будет полноценный Element X-like клиент.", true, "12:34"),
)

@Composable
fun ChatScreen(
    roomId: String,
    onBack: () -> Unit
) {
    var input by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TgChat)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TgSidebar)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TgTextPrimary)
            }
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(TgInput),
                contentAlignment = Alignment.Center
            ) {
                Text(roomId.take(1).uppercase(), color = TgTextPrimary, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(roomId.take(20) + if (roomId.length > 20) "…" else "", color = TgTextPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text("Matrix room", color = TgTextSecondary, fontSize = 12.sp)
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            items(demoMessages) { msg ->
                MessageBubble(msg)
                Spacer(Modifier.height(6.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TgSidebar)
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Сообщение", color = TgTextMeta) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = TgInput,
                    unfocusedContainerColor = TgInput,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = TgTextPrimary,
                    unfocusedTextColor = TgTextPrimary,
                    cursorColor = TgAccent
                ),
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { input = "" },
                enabled = input.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (input.isNotBlank()) TgAccent else TgInput)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = Color.White)
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (msg.isMine) 16.dp else 4.dp,
                        bottomEnd = if (msg.isMine) 4.dp else 16.dp
                    )
                )
                .background(if (msg.isMine) TgBubbleOut else TgBubbleIn)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (!msg.isMine && msg.sender.isNotEmpty()) {
                Text(msg.sender, color = TgAccent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
            }
            Text(msg.body, color = if (msg.isMine) Color.White else TgTextPrimary, fontSize = 15.sp)
            Text(
                msg.time,
                color = if (msg.isMine) Color.White.copy(alpha = 0.7f) else TgTextMeta,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}
