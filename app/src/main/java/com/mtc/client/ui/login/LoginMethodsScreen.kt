package com.mtc.client.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtc.client.matrix.IdentityProvider
import com.mtc.client.ui.theme.*

@Composable
fun LoginMethodsScreen(
    serverName: String,
    hasPassword: Boolean,
    hasSso: Boolean,
    usesOidc: Boolean = false,
    identityProviders: List<IdentityProvider>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onPasswordLogin: () -> Unit,
    onRegister: () -> Unit,
    onSso: (IdentityProvider?) -> Unit,
    onOidcLogin: () -> Unit = {},
    onOidcRegister: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TgBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TgTextPrimary)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = serverName,
                color = TgTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (usesOidc) "Вход через OIDC (как Element X)" else "Выберите способ входа",
                color = TgTextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(32.dp))

            if (usesOidc) {
                AuthButton(
                    text = "Продолжить (SSO / OIDC)",
                    onClick = onOidcLogin,
                    enabled = !isLoading
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOidcRegister,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TgTextPrimary)
                ) {
                    Text("Создать аккаунт")
                }
            } else {
                if (hasSso) {
                    if (identityProviders.isEmpty()) {
                        AuthButton(
                            text = "Continue with SSO",
                            onClick = { onSso(null) },
                            enabled = !isLoading
                        )
                        Spacer(Modifier.height(12.dp))
                    } else {
                        identityProviders.forEach { idp ->
                            AuthButton(
                                text = "Continue with ${idp.name}",
                                onClick = { onSso(idp) },
                                enabled = !isLoading
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                if (hasPassword) {
                    AuthButton(
                        text = "Войти с паролем",
                        onClick = onPasswordLogin,
                        enabled = !isLoading,
                        outlined = hasSso
                    )
                    Spacer(Modifier.height(12.dp))
                }

                TextButton(onClick = onRegister, enabled = !isLoading) {
                    Text("Создать аккаунт", color = TgAccent)
                }
            }

            if (error != null) {
                Spacer(Modifier.height(16.dp))
                Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp)
            }

            if (isLoading) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(color = TgAccent)
            }
        }
    }
}

@Composable
private fun AuthButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    outlined: Boolean = false
) {
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TgTextPrimary)
        ) {
            Text(text)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TgAccent)
        ) {
            Text(text, color = Color.White)
        }
    }
}
