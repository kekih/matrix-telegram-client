package com.mtc.client.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TgBg)
            .padding(24.dp),
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
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(TgAccent),
                contentAlignment = Alignment.Center
            ) {
                Text("M", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
            Text("Matrix Telegram", color = TgTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("E2EE Matrix + Vault (Argon2id)", color = TgTextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))

            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = state.homeserver,
                onValueChange = viewModel::updateHomeserver,
                label = { Text("Homeserver") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = telegramTextFieldColors()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::updateUsername,
                label = { Text("Username / MXID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = telegramTextFieldColors()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::updatePassword,
                label = { Text("Пароль Matrix") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                colors = telegramTextFieldColors()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.masterPassword,
                onValueChange = viewModel::updateMasterPassword,
                label = { Text("Мастер-пароль vault") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = {
                    Text("Argon2id → AES-256-GCM. Не уходит на сервер.", color = TgTextMeta, fontSize = 11.sp)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = telegramTextFieldColors()
            )

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(state.error!!, color = Color(0xFFFF6B6B), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::login,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TgAccent)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Войти", fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
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
private fun telegramTextFieldColors() = OutlinedTextFieldDefaults.colors(
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
