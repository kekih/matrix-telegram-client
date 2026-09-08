package com.mtc.client.data

import android.content.Context
import com.mtc.client.matrix.MatrixSession

class SessionRepository(context: Context) {

    private val prefs = context.getSharedPreferences("mtc_session", Context.MODE_PRIVATE)

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
