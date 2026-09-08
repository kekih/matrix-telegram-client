package com.mtc.client.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

@Composable
fun ChatScreen(
    roomId: String,
    roomName: String,
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
                Text(roomName.take(1).uppercase(), color = TgTextPrimary, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(roomName, color = TgTextPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text("Matrix room", color = TgTextSecondary, fontSize = 12.sp)
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Чат подключён к Matrix.\nПолная история сообщений — следующий шаг (timeline API).\nRoom: $roomId",
                color = TgTextSecondary,
                fontSize = 14.sp
            )
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
