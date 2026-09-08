package com.mtc.client.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtc.client.ui.theme.*

@Composable
fun PasswordAuthScreen(
    isRegister: Boolean,
    serverName: String,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSubmit: (username: String, password: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TgBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TgTextPrimary)
        }

        Column(Modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = if (isRegister) "Создать аккаунт" else "Войти",
                color = TgTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = serverName,
                color = TgTextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(if (isRegister) "Username" else "Username / MXID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onSubmit(username.trim(), password) },
                enabled = username.isNotBlank() && password.length >= 1 && !isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TgAccent)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(if (isRegister) "Зарегистрироваться" else "Войти", color = Color.White)
                }
            }

            if (isRegister) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Регистрация доступна не на всех серверах. " +
                        "На matrix.org часто нужен SSO или токен.",
                    color = TgTextMeta,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TgTextPrimary,
    unfocusedTextColor = TgTextPrimary,
    focusedBorderColor = TgAccent,
    unfocusedBorderColor = TgTextMeta.copy(alpha = 0.3f),
    focusedLabelColor = TgAccent,
    unfocusedLabelColor = TgTextSecondary,
    cursorColor = TgAccent,
    focusedContainerColor = TgInput,
    unfocusedContainerColor = TgInput
)
