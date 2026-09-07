package com.mtc.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mtc.client.ui.MtcApp
import com.mtc.client.ui.theme.MatrixTelegramTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MatrixTelegramTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MtcApp()
                }
            }
        }
    }
}
