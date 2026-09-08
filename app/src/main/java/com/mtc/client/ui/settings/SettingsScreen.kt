package com.mtc.client.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtc.client.ui.theme.*

@Composable
fun SettingsScreen(
    userId: String,
    onLogout: () -> Unit
) {
    var showLogout by remember { mutableStateOf(false) }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("Выйти из аккаунта?") },
            text = { Text("Сессия будет удалена с этого устройства.") },
            confirmButton = {
                TextButton(onClick = { showLogout = false; onLogout() }) {
                    Text("Выйти", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) { Text("Отмена") }
            },
            containerColor = TgSidebar,
            titleContentColor = TgTextPrimary,
            textContentColor = TgTextSecondary
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TgBg)
            .statusBarsPadding()
    ) {
        Text(
            "Настройки",
            color = TgTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(20.dp)
        )

        SettingsItem(Icons.Default.Notifications, "Уведомления", "Скоро")
        SettingsItem(Icons.Default.Security, "Конфиденциальность", "E2EE · Olm/Megolm")
        SettingsItem(Icons.Default.Info, "О приложении", "Matrix Telegram · Android")

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showLogout = true }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color(0xFFFF6B6B))
            Spacer(Modifier.width(16.dp))
            Text("Выйти", color = Color(0xFFFF6B6B), fontSize = 16.sp)
        }

        Spacer(Modifier.weight(1f))
        Text(
            userId,
            color = TgTextMeta,
            fontSize = 12.sp,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun SettingsItem(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = TgInput,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = TgAccent, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = TgTextPrimary, fontSize = 16.sp)
            Text(subtitle, color = TgTextSecondary, fontSize = 13.sp)
        }
    }
}
