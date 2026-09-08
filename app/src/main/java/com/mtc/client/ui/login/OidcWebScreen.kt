package com.mtc.client.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mtc.client.matrix.OIDC_REDIRECT_URI
import com.mtc.client.ui.theme.TgBg
import com.mtc.client.ui.theme.TgTextPrimary

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OidcWebScreen(
    authUrl: String,
    onRedirect: (url: String) -> Unit,
    onCancel: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = TgTextPrimary)
        }
        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(
            text = "Вход через auth-сервер…",
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(0xFF0E1621.toInt())
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                if (url.startsWith(OIDC_REDIRECT_URI) ||
                                    url.startsWith("http://127.0.0.1:8787/") ||
                                    url.startsWith("http://localhost:8787/")
                                ) {
                                    onRedirect(url)
                                    return true
                                }
                                return false
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loading = true
                                // Also catch redirect on page start (some WebViews)
                                if (url != null && (
                                        url.startsWith(OIDC_REDIRECT_URI) ||
                                            url.startsWith("http://127.0.0.1:8787/")
                                        )
                                ) {
                                    onRedirect(url)
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                            }
                        }
                        loadUrl(authUrl)
                    }
                }
            )
        }
    }
}
