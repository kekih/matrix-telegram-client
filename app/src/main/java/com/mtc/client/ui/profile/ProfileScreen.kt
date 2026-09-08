package com.mtc.client.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtc.client.matrix.UserProfile
import com.mtc.client.ui.theme.*

@Composable
fun ProfileScreen(
    userId: String,
    profile: UserProfile?,
    loading: Boolean,
    saving: Boolean,
    error: String?,
    onSaveName: (String) -> Unit,
    onPickAvatar: () -> Unit
) {
    var name by remember(profile?.displayName) {
        mutableStateOf(profile?.displayName ?: userId.removePrefix("@").substringBefore(":"))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TgBg)
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Профиль",
            color = TgTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(top = 20.dp, bottom = 28.dp)
        )

        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(TgAccent)
                    .clickable { onPickAvatar() },
                contentAlignment = Alignment.Center
            ) {
                val letter = (profile?.displayName ?: userId)
                    .removePrefix("@").take(1).uppercase().ifEmpty { "M" }
                Text(letter, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
            Surface(
                color = TgAccent,
                shape = CircleShape,
                modifier = Modifier
                    .size(34.dp)
                    .clickable { onPickAvatar() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Сменить аватар",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Нажмите на фото, чтобы сменить аватар",
            color = TgTextMeta,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Отображаемое имя") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TgAccent,
                unfocusedBorderColor = TgInput,
                focusedTextColor = TgTextPrimary,
                unfocusedTextColor = TgTextPrimary,
                focusedLabelColor = TgAccent,
                unfocusedLabelColor = TgTextSecondary,
                cursorColor = TgAccent,
                focusedContainerColor = TgSidebar,
                unfocusedContainerColor = TgSidebar
            )
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = userId,
            onValueChange = {},
            label = { Text("Matrix ID") },
            enabled = false,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = TgTextSecondary,
                disabledBorderColor = TgInput,
                disabledLabelColor = TgTextMeta,
                disabledContainerColor = TgSidebar
            )
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onSaveName(name.trim()) },
            enabled = !saving && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TgAccent)
        ) {
            if (saving || loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Сохранить имя", color = Color.White)
            }
        }
    }
}
