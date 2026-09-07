package com.mtc.client.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Local Vault — Bitwarden-style layer on top of Matrix E2EE.
 *
 * Master password → Argon2id → AES-256-GCM
 * Optionally wraps the vault key with Android Keystore for biometric unlock later.
 *
 * This protects local secrets (tokens, device keys). 
 * Message encryption remains Matrix Olm/Megolm.
 */
class Vault {

    private val argon2 = Argon2Kt()
    private val secureRandom = SecureRandom()

    companion object {
        private const val ARGON2_T_COST = 3
        private const val ARGON2_M_COST = 65536 // 64 MB
        private const val ARGON2_PARALLELISM = 4
        private const val KEY_LEN = 32
        private const val SALT_LEN = 16
        private const val IV_LEN = 12
        private const val GCM_TAG_BITS = 128
        private const val KEYSTORE_ALIAS = "mtc_vault_master"
    }

    data class SealedBlob(
        val saltB64: String,
        val ivB64: String,
        val ciphertextB64: String
    )

    fun deriveKey(masterPassword: CharArray, salt: ByteArray? = null): Pair<ByteArray, ByteArray> {
        val usedSalt = salt ?: ByteArray(SALT_LEN).also { secureRandom.nextBytes(it) }

        val hash = argon2.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = masterPassword.concatToString().toByteArray(Charsets.UTF_8),
            salt = usedSalt,
            tCostInIterations = ARGON2_T_COST,
            mCostInKibibyte = ARGON2_M_COST,
            parallelism = ARGON2_PARALLELISM
        )

        val keyBytes = hash.rawHash.copyOf(KEY_LEN)
        return keyBytes to usedSalt
    }

    fun seal(masterPassword: CharArray, plaintext: ByteArray): SealedBlob {
        val (keyBytes, salt) = deriveKey(masterPassword)
        try {
            val iv = ByteArray(IV_LEN).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(keyBytes, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(plaintext)

            return SealedBlob(
                saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP),
                ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP),
                ciphertextB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            )
        } finally {
            keyBytes.fill(0)
        }
    }

    fun unseal(masterPassword: CharArray, blob: SealedBlob): ByteArray {
        val salt = Base64.decode(blob.saltB64, Base64.NO_WRAP)
        val iv = Base64.decode(blob.ivB64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(blob.ciphertextB64, Base64.NO_WRAP)

        val (keyBytes, _) = deriveKey(masterPassword, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(keyBytes, "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
            return cipher.doFinal(ciphertext)
        } finally {
            keyBytes.fill(0)
        }
    }

    fun sealString(masterPassword: CharArray, text: String): SealedBlob =
        seal(masterPassword, text.toByteArray(Charsets.UTF_8))

    fun unsealString(masterPassword: CharArray, blob: SealedBlob): String =
        unseal(masterPassword, blob).toString(Charsets.UTF_8)

    fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(KEYSTORE_ALIAS)) {
            return (ks.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
