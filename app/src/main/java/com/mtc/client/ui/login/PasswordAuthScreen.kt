package com.mtc.client.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtc.client.ui.theme.TgAccent
import com.mtc.client.ui.theme.TgBg
import com.mtc.client.ui.theme.TgInput
import com.mtc.client.ui.theme.TgTextMeta
import com.mtc.client.ui.theme.TgTextPrimary
import com.mtc.client.ui.theme.TgTextSecondary

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
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TgTextPrimary
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
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

            Spacer(modifier = Modifier.height(12.dp))

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
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = error, color = Color(0xFFFF6B6B), fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onSubmit(username.trim(), password) },
                enabled = username.isNotBlank() && password.isNotEmpty() && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TgAccent)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isRegister) "Зарегистрироваться" else "Войти",
                        color = Color.White
                    )
                }
            }

            if (isRegister) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Регистрация доступна не на всех серверах. На matrix.org часто нужен SSO.",
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
