package com.mtc.client.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.mtc.client.matrix.ChatMessage
import com.mtc.client.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatScreen(
    roomId: String,
    roomName: String,
    messages: List<ChatMessage>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TgTextPrimary)
            }
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(TgInput),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    roomName.take(1).uppercase().ifEmpty { "#" },
                    color = TgTextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(roomName, color = TgTextPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text("Matrix", color = TgTextSecondary, fontSize = 12.sp)
            }
            IconButton(onClick = onRefresh) {
                Text("⟳", color = TgTextSecondary, fontSize = 18.sp)
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && messages.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = TgAccent
                    )
                }
                error != null && messages.isEmpty() -> {
                    Text(
                        error,
                        color = Color(0xFFFF6B6B),
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
                messages.isEmpty() -> {
                    Text(
                        "Нет сообщений",
                        color = TgTextSecondary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(messages, key = { it.eventId }) { msg ->
                            MessageBubble(msg)
                        }
                    }
                }
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
                onClick = {
                    val t = input.trim()
                    if (t.isNotEmpty()) {
                        onSend(t)
                        input = ""
                    }
                },
                enabled = input.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (input.isNotBlank()) TgAccent else TgInput)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val time = if (msg.timestamp > 0) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
    } else ""

    if (msg.isState) {
        Text(
            msg.body,
            color = TgTextMeta,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.isMine) Alignment.End else Alignment.Start
    ) {
        if (!msg.isMine) {
            Text(
                msg.sender.removePrefix("@").substringBefore(":"),
                color = TgAccent,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }
        Surface(
            color = if (msg.isMine) TgBubbleOut else TgBubbleIn,
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (msg.isMine) 14.dp else 4.dp,
                bottomEnd = if (msg.isMine) 4.dp else 14.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    msg.body,
                    color = if (msg.isEncrypted) TgTextSecondary else TgTextPrimary,
                    fontSize = 15.sp
                )
                Text(
                    time,
                    color = TgTextMeta,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                )
            }
        }
    }
}
