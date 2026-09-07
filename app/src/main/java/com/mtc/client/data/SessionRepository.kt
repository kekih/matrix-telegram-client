package com.mtc.client.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mtc.client.crypto.Vault
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "mtc_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSealedSession(blob: Vault.SealedBlob) {
        prefs.edit()
            .putString("salt", blob.saltB64)
            .putString("iv", blob.ivB64)
            .putString("ct", blob.ciphertextB64)
            .apply()
    }

    fun getSealedSession(): Vault.SealedBlob? {
        val salt = prefs.getString("salt", null) ?: return null
        val iv = prefs.getString("iv", null) ?: return null
        val ct = prefs.getString("ct", null) ?: return null
        return Vault.SealedBlob(salt, iv, ct)
    }

    fun hasActiveSession(): Boolean = prefs.contains("ct")

    fun clear() {
        prefs.edit().clear().apply()
    }
}
