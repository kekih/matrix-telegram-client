package com.mtc.client.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtc.client.ui.theme.*

/**
 * Element X style: "Enter your account provider"
 */
@Composable
fun AccountProviderScreen(
    isLoading: Boolean,
    error: String?,
    onContinue: (String) -> Unit
) {
    var provider by remember { mutableStateOf("matrix.org") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TgBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.15f))

        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = TgTextSecondary,
            modifier = Modifier
                .size(64.dp)
                .background(TgInput, RoundedCornerShape(16.dp))
                .padding(16.dp)
        )

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Enter your account provider",
            color = TgTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Account provider",
            color = TgTextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = provider,
            onValueChange = { provider = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TgTextPrimary,
                unfocusedTextColor = TgTextPrimary,
                focusedBorderColor = TgAccent,
                unfocusedBorderColor = TgTextMeta.copy(alpha = 0.3f),
                cursorColor = TgAccent,
                focusedContainerColor = TgInput,
                unfocusedContainerColor = TgInput
            ),
            trailingIcon = {
                if (provider.isNotEmpty()) {
                    TextButton(onClick = { provider = "" }) {
                        Text("✕", color = TgTextSecondary)
                    }
                }
            }
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = "If your account is provided by your company or a community, " +
                "then change the account provider (example: companyname.com).",
            color = TgTextMeta,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth()
        )

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp)
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { onContinue(provider.trim()) },
            enabled = provider.isNotBlank() && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
                disabledContainerColor = Color.White.copy(alpha = 0.5f)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = Color.Black
                )
            } else {
                Text("Продолжить", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
