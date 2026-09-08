package com.mtc.client.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    val serverName: String,
    /** MSC3861 OIDC issuer, if homeserver uses modern auth */
    val oidcIssuer: String? = null
) {
    val usesOidc: Boolean get() = !oidcIssuer.isNullOrBlank()
}

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
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun discoverHomeserver(provider: String): HomeserverConfig = withContext(Dispatchers.IO) {
        val cleaned = provider.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')

        var baseUrl = "https://$cleaned"
        var oidcIssuer: String? = null

        try {
            val body = get("https://$cleaned/.well-known/matrix/client")
            val obj = JSONObject(body)
            val hs = obj.optJSONObject("m.homeserver")?.optString("base_url")?.trimEnd('/')
            if (!hs.isNullOrBlank()) baseUrl = hs

            // Element X / MAS style
            val msc3861 = obj.optJSONObject("org.matrix.msc3861.authentication")
            oidcIssuer = msc3861?.optString("issuer")?.trimEnd('/')?.takeIf { it.isNotBlank() }

            if (oidcIssuer == null) {
                // Try unstable auth_issuer
                try {
                    val issuerBody = get("$baseUrl/_matrix/client/unstable/org.matrix.msc2965/auth_issuer")
                    oidcIssuer = JSONObject(issuerBody).optString("issuer")
                        .trimEnd('/').takeIf { it.isNotBlank() }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }

        HomeserverConfig(baseUrl = baseUrl, serverName = cleaned, oidcIssuer = oidcIssuer)
    }

    suspend fun getLoginFlows(baseUrl: String): List<LoginFlow> = withContext(Dispatchers.IO) {
        try {
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
        } catch (e: MatrixApiException) {
            // M_UNRECOGNIZED = classic login disabled (OIDC-only homeserver)
            emptyList()
        }
    }

    suspend fun loginPassword(
        baseUrl: String,
        user: String,
        password: String,
        deviceName: String = "Matrix Telegram"
    ): MatrixSession = withContext(Dispatchers.IO) {
        val localpart = user.removePrefix("@").substringBefore(":")
        val identifier = JSONObject().put("type", "m.id.user").put("user", localpart)
        val req = JSONObject()
            .put("type", "m.login.password")
            .put("identifier", identifier)
            .put("password", password)
            .put("initial_device_display_name", deviceName)
        parseSession(post("$baseUrl/_matrix/client/v3/login", req.toString()), baseUrl)
    }

    suspend fun loginWithToken(
        baseUrl: String,
        loginToken: String,
        deviceName: String = "Matrix Telegram"
    ): MatrixSession = withContext(Dispatchers.IO) {
        val req = JSONObject()
            .put("type", "m.login.token")
            .put("token", loginToken)
            .put("initial_device_display_name", deviceName)
        parseSession(post("$baseUrl/_matrix/client/v3/login", req.toString()), baseUrl)
    }

    fun ssoRedirectUrl(baseUrl: String, redirectUrl: String, idpId: String? = null): String {
        val encoded = java.net.URLEncoder.encode(redirectUrl, Charsets.UTF_8.name())
        return if (idpId.isNullOrBlank()) {
            "$baseUrl/_matrix/client/v3/login/sso/redirect?redirectUrl=$encoded"
        } else {
            "$baseUrl/_matrix/client/v3/login/sso/redirect/$idpId?redirectUrl=$encoded"
        }
    }

    suspend fun register(
        baseUrl: String,
        username: String,
        password: String,
        deviceName: String = "Matrix Telegram"
    ): MatrixSession = withContext(Dispatchers.IO) {
        val initial = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("initial_device_display_name", deviceName)
            .put("auth", JSONObject().put("type", "m.login.dummy"))
        try {
            parseSession(post("$baseUrl/_matrix/client/v3/register", initial.toString()), baseUrl)
        } catch (e: MatrixApiException) {
            val session = e.json?.optString("session")
            if (e.httpCode == 401 && !session.isNullOrBlank()) {
                val retry = JSONObject()
                    .put("username", username)
                    .put("password", password)
                    .put("initial_device_display_name", deviceName)
                    .put("auth", JSONObject().put("type", "m.login.dummy").put("session", session))
                parseSession(post("$baseUrl/_matrix/client/v3/register", retry.toString()), baseUrl)
            } else throw e
        }
    }

    suspend fun loadRooms(session: MatrixSession): List<RoomSummary> = withContext(Dispatchers.IO) {
        try {
            val fromSync = syncRooms(session)
            if (fromSync.isNotEmpty()) return@withContext fromSync
        } catch (_: Exception) { }
        loadRoomsViaJoined(session)
    }

    private fun syncRooms(session: MatrixSession): List<RoomSummary> {
        val url = "${session.homeserverUrl}/_matrix/client/v3/sync?timeout=0"
        val body = get(url, session.accessToken)
        val join = JSONObject(body).optJSONObject("rooms")?.optJSONObject("join")
            ?: return emptyList()
        return buildList {
            val keys = join.keys()
            while (keys.hasNext()) {
                val roomId = keys.next()
                add(parseRoomFromSync(roomId, join.getJSONObject(roomId)))
            }
        }.sortedByDescending { it.timestamp }
    }

    private fun parseRoomFromSync(roomId: String, room: JSONObject): RoomSummary {
        var lastMsg = ""
        var ts = 0L
        val timeline = room.optJSONObject("timeline")?.optJSONArray("events")
        if (timeline != null && timeline.length() > 0) {
            val ev = timeline.getJSONObject(timeline.length() - 1)
            ts = ev.optLong("origin_server_ts", 0L)
            val content = ev.optJSONObject("content")
            lastMsg = when {
                content == null -> ev.optString("type", "")
                content.has("body") -> content.optString("body")
                content.has("membership") -> content.optString("membership")
                else -> ev.optString("type", "")
            }
        }
        val stateEvents = room.optJSONObject("state")?.optJSONArray("events")
        val name = resolveName(roomId, stateEvents, room.optJSONObject("summary"))
        val unread = room.optJSONObject("unread_notifications")
            ?.optInt("notification_count", 0) ?: 0
        return RoomSummary(roomId, name, lastMsg, ts, unread)
    }

    private suspend fun loadRoomsViaJoined(session: MatrixSession): List<RoomSummary> =
        coroutineScope {
            val body = get(
                "${session.homeserverUrl}/_matrix/client/v3/joined_rooms",
                session.accessToken
            )
            val arr = JSONObject(body).optJSONArray("joined_rooms") ?: JSONArray()
            val ids = (0 until arr.length()).map { arr.getString(it) }
            ids.take(80).map { roomId ->
                async {
                    try {
                        fetchRoomSummary(session, roomId)
                    } catch (_: Exception) {
                        RoomSummary(roomId, roomId, "", 0L, 0)
                    }
                }
            }.awaitAll().sortedByDescending { it.timestamp }
        }

    private fun fetchRoomSummary(session: MatrixSession, roomId: String): RoomSummary {
        val encoded = java.net.URLEncoder.encode(roomId, Charsets.UTF_8.name())
        var name = roomId
        try {
            val stateBody = get(
                "${session.homeserverUrl}/_matrix/client/v3/rooms/$encoded/state",
                session.accessToken
            )
            name = resolveName(roomId, JSONArray(stateBody), null)
        } catch (_: Exception) { }
        var lastMsg = ""
        var ts = 0L
        try {
            val msgBody = get(
                "${session.homeserverUrl}/_matrix/client/v3/rooms/$encoded/messages?dir=b&limit=1",
                session.accessToken
            )
            val chunk = JSONObject(msgBody).optJSONArray("chunk")
            if (chunk != null && chunk.length() > 0) {
                val ev = chunk.getJSONObject(0)
                ts = ev.optLong("origin_server_ts", 0L)
                lastMsg = ev.optJSONObject("content")?.optString("body")
                    ?: ev.optString("type", "")
            }
        } catch (_: Exception) { }
        return RoomSummary(roomId, name, lastMsg, ts, 0)
    }

    private fun resolveName(roomId: String, stateEvents: JSONArray?, summary: JSONObject?): String {
        if (stateEvents != null) {
            for (i in 0 until stateEvents.length()) {
                val ev = stateEvents.getJSONObject(i)
                if (ev.optString("type") == "m.room.name") {
                    val n = ev.optJSONObject("content")?.optString("name")
                    if (!n.isNullOrBlank()) return n
                }
            }
            for (i in 0 until stateEvents.length()) {
                val ev = stateEvents.getJSONObject(i)
                if (ev.optString("type") == "m.room.canonical_alias") {
                    val a = ev.optJSONObject("content")?.optString("alias")
                    if (!a.isNullOrBlank()) return a
                }
            }
        }
        val heroes = summary?.optJSONArray("m.heroes")
        if (heroes != null && heroes.length() > 0) return heroes.getString(0)
        return roomId
    }

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
