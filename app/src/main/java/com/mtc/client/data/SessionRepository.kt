package com.mtc.client.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mtc.client.matrix.MatrixSession

class SessionRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "mtc_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(session: MatrixSession) {
        prefs.edit()
            .putString("user_id", session.userId)
            .putString("access_token", session.accessToken)
            .putString("device_id", session.deviceId)
            .putString("homeserver", session.homeserverUrl)
            .apply()
    }

    fun load(): MatrixSession? {
        val token = prefs.getString("access_token", null) ?: return null
        val userId = prefs.getString("user_id", null) ?: return null
        val hs = prefs.getString("homeserver", null) ?: return null
        return MatrixSession(
            userId = userId,
            accessToken = token,
            deviceId = prefs.getString("device_id", "") ?: "",
            homeserverUrl = hs
        )
    }

    fun hasSession(): Boolean = prefs.contains("access_token")

    fun clear() {
        prefs.edit().clear().apply()
    }
}
