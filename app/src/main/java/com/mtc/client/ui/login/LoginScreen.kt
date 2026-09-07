package com.mtc.client.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtc.client.ui.theme.*

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var homeserver by remember { mutableStateOf("https://matrix.org") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var masterPassword by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize().background(TgBg).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(TgSidebar)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(TgAccent),
                contentAlignment = Alignment.Center
            ) {
                Text("M", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Text("Matrix Telegram", color = TgTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("E2EE Matrix + Vault (Argon2id)", color = TgTextSecondary, fontSize = 13.sp)

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = homeserver,
                onValueChange = { homeserver = it },
                label = { Text("Homeserver") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username / MXID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль Matrix") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = masterPassword,
                onValueChange = { masterPassword = it },
                label = { Text("Мастер-пароль vault") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = {
                    Text("Argon2id → AES-256-GCM. Не уходит на сервер.", color = TgTextMeta, fontSize = 11.sp)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onLoginSuccess,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TgAccent)
            ) {
                Text("Войти", fontSize = 16.sp)
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Основное шифрование — Matrix Olm/Megolm\nЛокальный vault — как в Bitwarden",
                color = TgTextMeta,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TgTextPrimary,
    unfocusedTextColor = TgTextPrimary,
    focusedBorderColor = TgAccent,
    unfocusedBorderColor = TgTextMeta.copy(alpha = 0.4f),
    focusedLabelColor = TgAccent,
    unfocusedLabelColor = TgTextSecondary,
    cursorColor = TgAccent,
    focusedContainerColor = TgInput,
    unfocusedContainerColor = TgInput
)
