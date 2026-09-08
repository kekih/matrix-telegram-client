package com.mtc.client.matrix

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

const val OIDC_REDIRECT_URI = "mtc://login"

data class OidcConfig(
    val issuer: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val registrationEndpoint: String?
)

data class OidcClient(
    val clientId: String
)

data class OidcPendingAuth(
    val codeVerifier: String,
    val state: String,
    val deviceId: String,
    val homeserverUrl: String,
    val tokenEndpoint: String,
    val clientId: String,
    val redirectUri: String = OIDC_REDIRECT_URI
)

data class OidcTokens(
    val accessToken: String,
    val refreshToken: String?,
    val deviceId: String,
    val homeserverUrl: String
)

object OidcAuth {
    const val REDIRECT_URI = OIDC_REDIRECT_URI

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun discoverOidc(issuer: String): OidcConfig = withContext(Dispatchers.IO) {
        val base = issuer.trimEnd('/')
        val body = get("$base/.well-known/openid-configuration")
        val o = JSONObject(body)
        OidcConfig(
            issuer = o.optString("issuer", base),
            authorizationEndpoint = o.getString("authorization_endpoint"),
            tokenEndpoint = o.getString("token_endpoint"),
            registrationEndpoint = o.optString("registration_endpoint").takeIf { it.isNotBlank() }
        )
    }

    suspend fun registerClient(config: OidcConfig): OidcClient = withContext(Dispatchers.IO) {
        val endpoint = config.registrationEndpoint
            ?: error("Сервер не поддерживает dynamic client registration")

        val meta = JSONObject()
            .put("application_type", "native")
            .put("client_name", "Matrix Telegram")
            .put("redirect_uris", org.json.JSONArray().put(OIDC_REDIRECT_URI))
            .put("token_endpoint_auth_method", "none")
            .put("grant_types", org.json.JSONArray().put("authorization_code").put("refresh_token"))
            .put("response_types", org.json.JSONArray().put("code"))

        val req = Request.Builder()
            .url(endpoint)
            .post(meta.toString().toRequestBody(jsonMedia))
            .header("Content-Type", "application/json")
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val err = try { JSONObject(body).optString("error_description") } catch (_: Exception) { body }
                error("OIDC registration failed: ${err.ifBlank { resp.code.toString() }}")
            }
            OidcClient(clientId = JSONObject(body).getString("client_id"))
        }
    }

    fun generateDeviceId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val rnd = SecureRandom()
        return (1..12).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }

    fun beginAuth(
        config: OidcConfig,
        clientId: String,
        homeserverUrl: String,
        forRegistration: Boolean = false
    ): Pair<String, OidcPendingAuth> {
        val verifier = randomUrlSafe(64)
        val challenge = pkceChallenge(verifier)
        val state = randomUrlSafe(24)
        val deviceId = generateDeviceId()

        val scope = listOf(
            "openid",
            "urn:matrix:client:api:*",
            "urn:matrix:client:device:$deviceId"
        ).joinToString(" ")

        val params = linkedMapOf(
            "response_type" to "code",
            "client_id" to clientId,
            "redirect_uri" to OIDC_REDIRECT_URI,
            "scope" to scope,
            "state" to state,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256"
        )
        if (forRegistration) {
            params["prompt"] = "create"
        }

        val query = params.entries.joinToString("&") { (k, v) ->
            "${enc(k)}=${enc(v)}"
        }
        val authUrl = "${config.authorizationEndpoint}?$query"

        val pending = OidcPendingAuth(
            codeVerifier = verifier,
            state = state,
            deviceId = deviceId,
            homeserverUrl = homeserverUrl.trimEnd('/'),
            tokenEndpoint = config.tokenEndpoint,
            clientId = clientId
        )
        return authUrl to pending
    }

    suspend fun exchangeCode(
        pending: OidcPendingAuth,
        code: String
    ): OidcTokens = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", pending.redirectUri)
            .add("client_id", pending.clientId)
            .add("code_verifier", pending.codeVerifier)
            .build()

        val req = Request.Builder()
            .url(pending.tokenEndpoint)
            .post(form)
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val err = try {
                    val o = JSONObject(body)
                    o.optString("error_description").ifBlank { o.optString("error") }
                } catch (_: Exception) { body }
                error("Token exchange failed: ${err.ifBlank { resp.code.toString() }}")
            }
            val o = JSONObject(body)
            OidcTokens(
                accessToken = o.getString("access_token"),
                refreshToken = o.optString("refresh_token").takeIf { it.isNotBlank() },
                deviceId = pending.deviceId,
                homeserverUrl = pending.homeserverUrl
            )
        }
    }

    suspend fun whoami(homeserverUrl: String, accessToken: String): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${homeserverUrl.trimEnd('/')}/_matrix/client/v3/account/whoami")
                .get()
                .header("Authorization", "Bearer $accessToken")
                .build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("whoami failed: HTTP ${resp.code}")
                JSONObject(body).getString("user_id")
            }
        }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}: $body")
            return body
        }
    }

    private fun randomUrlSafe(bytes: Int): String {
        val buf = ByteArray(bytes)
        SecureRandom().nextBytes(buf)
        return Base64.encodeToString(buf, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun pkceChallenge(verifier: String): String {
        val dig = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(dig, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun enc(s: String): String =
        java.net.URLEncoder.encode(s, Charsets.UTF_8.name())
}
