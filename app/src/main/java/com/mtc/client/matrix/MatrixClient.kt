package com.mtc.client.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class HomeserverConfig(
    val baseUrl: String,
    val serverName: String
)

data class LoginFlow(
    val type: String,
    val identityProviders: List<IdentityProvider> = emptyList()
)

data class IdentityProvider(
    val id: String,
    val name: String,
    val icon: String? = null
)

data class MatrixSession(
    val userId: String,
    val accessToken: String,
    val deviceId: String,
    val homeserverUrl: String
)

data class RoomSummary(
    val roomId: String,
    val name: String,
    val lastMessage: String,
    val timestamp: Long,
    val unread: Int
)

class MatrixClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** Resolve account provider → homeserver base URL via .well-known */
    suspend fun discoverHomeserver(provider: String): HomeserverConfig = withContext(Dispatchers.IO) {
        val cleaned = provider.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')

        // 1) Try well-known
        try {
            val wkUrl = "https://$cleaned/.well-known/matrix/client"
            val body = get(wkUrl)
            val obj = JSONObject(body)
            val hs = obj.optJSONObject("m.homeserver")?.optString("base_url")?.trimEnd('/')
            if (!hs.isNullOrBlank()) {
                return@withContext HomeserverConfig(baseUrl = hs, serverName = cleaned)
            }
        } catch (_: Exception) { /* fall through */ }

        // 2) Fallback: assume https://provider is the CS API
        HomeserverConfig(baseUrl = "https://$cleaned", serverName = cleaned)
    }

    /** GET /_matrix/client/v3/login */
    suspend fun getLoginFlows(baseUrl: String): List<LoginFlow> = withContext(Dispatchers.IO) {
        val body = get("$baseUrl/_matrix/client/v3/login")
        val flows = JSONObject(body).optJSONArray("flows") ?: JSONArray()
        buildList {
            for (i in 0 until flows.length()) {
                val f = flows.getJSONObject(i)
                val type = f.getString("type")
                val idps = mutableListOf<IdentityProvider>()
                val idpArr = f.optJSONArray("identity_providers")
                if (idpArr != null) {
                    for (j in 0 until idpArr.length()) {
                        val p = idpArr.getJSONObject(j)
                        idps += IdentityProvider(
                            id = p.optString("id", ""),
                            name = p.optString("name", p.optString("id", "SSO")),
                            icon = p.optString("icon").takeIf { it.isNotBlank() }
                        )
                    }
                }
                add(LoginFlow(type = type, identityProviders = idps))
            }
        }
    }

    /** Password login */
    suspend fun loginPassword(
        baseUrl: String,
        user: String,
        password: String,
        deviceName: String = "Matrix Telegram"
    ): MatrixSession = withContext(Dispatchers.IO) {
        val identifier = if (user.startsWith("@")) {
            JSONObject().put("type", "m.id.user").put("user", user.substringBefore(":").removePrefix("@"))
        } else {
            JSONObject().put("type", "m.id.user").put("user", user)
        }
        val req = JSONObject()
            .put("type", "m.login.password")
            .put("identifier", identifier)
            .put("password", password)
            .put("initial_device_display_name", deviceName)

        val body = post("$baseUrl/_matrix/client/v3/login", req.toString())
        parseSession(body, baseUrl)
    }

    /** Exchange SSO loginToken for session */
    suspend fun loginWithToken(
        baseUrl: String,
        loginToken: String,
        deviceName: String = "Matrix Telegram"
    ): MatrixSession = withContext(Dispatchers.IO) {
        val req = JSONObject()
            .put("type", "m.login.token")
            .put("token", loginToken)
            .put("initial_device_display_name", deviceName)
        val body = post("$baseUrl/_matrix/client/v3/login", req.toString())
        parseSession(body, baseUrl)
    }

    /** Build classic Matrix SSO redirect URL */
    fun ssoRedirectUrl(baseUrl: String, redirectUrl: String, idpId: String? = null): String {
        val encoded = java.net.URLEncoder.encode(redirectUrl, Charsets.UTF_8.name())
        return if (idpId.isNullOrBlank()) {
            "$baseUrl/_matrix/client/v3/login/sso/redirect?redirectUrl=$encoded"
        } else {
            "$baseUrl/_matrix/client/v3/login/sso/redirect/$idpId?redirectUrl=$encoded"
        }
    }

    /**
     * Register with password.
     * Handles dummy UIA stage commonly required by Synapse.
     */
    suspend fun register(
        baseUrl: String,
        username: String,
        password: String,
        deviceName: String = "Matrix Telegram"
    ): MatrixSession = withContext(Dispatchers.IO) {
        // First attempt without auth → get session + flows
        val initial = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("initial_device_display_name", deviceName)
            .put("auth", JSONObject().put("type", "m.login.dummy"))

        try {
            val body = post("$baseUrl/_matrix/client/v3/register", initial.toString())
            return@withContext parseSession(body, baseUrl)
        } catch (e: MatrixApiException) {
            // 401 with session → complete dummy auth
            val err = e.json
            val session = err?.optString("session")
            if (e.httpCode == 401 && !session.isNullOrBlank()) {
                val retry = JSONObject()
                    .put("username", username)
                    .put("password", password)
                    .put("initial_device_display_name", deviceName)
                    .put(
                        "auth",
                        JSONObject()
                            .put("type", "m.login.dummy")
                            .put("session", session)
                    )
                val body = post("$baseUrl/_matrix/client/v3/register", retry.toString())
                return@withContext parseSession(body, baseUrl)
            }
            throw e
        }
    }

    /** Basic sync → room list */
    suspend fun syncRooms(session: MatrixSession): List<RoomSummary> = withContext(Dispatchers.IO) {
        val url = "${session.homeserverUrl}/_matrix/client/v3/sync?timeout=0&filter=" +
            java.net.URLEncoder.encode(
                """{"room":{"timeline":{"limit":1},"state":{"lazy_load_members":true}}}""",
                Charsets.UTF_8.name()
            )
        val body = get(url, session.accessToken)
        val root = JSONObject(body)
        val join = root.optJSONObject("rooms")?.optJSONObject("join") ?: return@withContext emptyList()

        buildList {
            val keys = join.keys()
            while (keys.hasNext()) {
                val roomId = keys.next()
                val room = join.getJSONObject(roomId)
                val timeline = room.optJSONObject("timeline")?.optJSONArray("events")
                var lastMsg = ""
                var ts = 0L
                if (timeline != null && timeline.length() > 0) {
                    val ev = timeline.getJSONObject(timeline.length() - 1)
                    ts = ev.optLong("origin_server_ts", 0L)
                    val content = ev.optJSONObject("content")
                    lastMsg = content?.optString("body")
                        ?: content?.optString("membership")
                        ?: ev.optString("type", "")
                }
                val name = room.optJSONObject("state")?.optJSONArray("events")?.let { arr ->
                    (0 until arr.length()).map { arr.getJSONObject(it) }
                        .firstOrNull { it.optString("type") == "m.room.name" }
                        ?.optJSONObject("content")?.optString("name")
                } ?: roomId

                val unread = room.optJSONObject("unread_notifications")
                    ?.optInt("notification_count", 0) ?: 0

                add(
                    RoomSummary(
                        roomId = roomId,
                        name = name.ifBlank { roomId },
                        lastMessage = lastMsg,
                        timestamp = ts,
                        unread = unread
                    )
                )
            }
        }.sortedByDescending { it.timestamp }
    }

    suspend fun sendText(session: MatrixSession, roomId: String, text: String) =
        withContext(Dispatchers.IO) {
            val txn = System.currentTimeMillis().toString()
            val req = JSONObject()
                .put("msgtype", "m.text")
                .put("body", text)
            put(
                "${session.homeserverUrl}/_matrix/client/v3/rooms/$roomId/send/m.room.message/$txn",
                req.toString(),
                session.accessToken
            )
        }

    // ── HTTP helpers ──────────────────────────────────────────────

    private fun get(url: String, token: String? = null): String {
        val b = Request.Builder().url(url).get()
        if (token != null) b.header("Authorization", "Bearer $token")
        return execute(b.build())
    }

    private fun post(url: String, json: String, token: String? = null): String {
        val b = Request.Builder().url(url).post(json.toRequestBody(jsonMedia))
        if (token != null) b.header("Authorization", "Bearer $token")
        return execute(b.build())
    }

    private fun put(url: String, json: String, token: String? = null): String {
        val b = Request.Builder().url(url).put(json.toRequestBody(jsonMedia))
        if (token != null) b.header("Authorization", "Bearer $token")
        return execute(b.build())
    }

    private fun execute(request: Request): String {
        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                val msg = json?.optString("error")
                    ?: json?.optString("errcode")
                    ?: "HTTP ${resp.code}"
                throw MatrixApiException(resp.code, msg, json)
            }
            return body
        }
    }

    private fun parseSession(body: String, baseUrl: String): MatrixSession {
        val o = JSONObject(body)
        return MatrixSession(
            userId = o.getString("user_id"),
            accessToken = o.getString("access_token"),
            deviceId = o.optString("device_id", ""),
            homeserverUrl = baseUrl.trimEnd('/')
        )
    }
}

class MatrixApiException(
    val httpCode: Int,
    override val message: String,
    val json: JSONObject?
) : Exception(message)
