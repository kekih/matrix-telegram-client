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
    val unread: Int,
    val encrypted: Boolean = false
)

data class ChatMessage(
    val eventId: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val isMine: Boolean,
    val isEncrypted: Boolean = false,
    val isState: Boolean = false
)

data class UserProfile(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?
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
            val msc3861 = obj.optJSONObject("org.matrix.msc3861.authentication")
            oidcIssuer = msc3861?.optString("issuer")?.trimEnd('/')?.takeIf { it.isNotBlank() }
            if (oidcIssuer == null) {
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
        } catch (_: MatrixApiException) {
            emptyList()
        }
    }

    suspend fun loginPassword(
        baseUrl: String, user: String, password: String, deviceName: String = "Matrix Telegram"
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
        baseUrl: String, loginToken: String, deviceName: String = "Matrix Telegram"
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
        baseUrl: String, username: String, password: String, deviceName: String = "Matrix Telegram"
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
                add(parseRoomFromSync(roomId, join.getJSONObject(roomId), session.userId))
            }
        }.sortedByDescending { it.timestamp }
    }

    private fun parseRoomFromSync(roomId: String, room: JSONObject, myUserId: String): RoomSummary {
        var lastMsg = ""
        var ts = 0L
        val timeline = room.optJSONObject("timeline")?.optJSONArray("events")
        if (timeline != null && timeline.length() > 0) {
            val ev = timeline.getJSONObject(timeline.length() - 1)
            ts = ev.optLong("origin_server_ts", 0L)
            lastMsg = previewForEvent(ev)
        }
        val stateEvents = room.optJSONObject("state")?.optJSONArray("events")
        val name = resolveName(roomId, stateEvents, room.optJSONObject("summary"), myUserId)
        val encrypted = hasEncryption(stateEvents)
        val unread = room.optJSONObject("unread_notifications")
            ?.optInt("notification_count", 0) ?: 0
        return RoomSummary(roomId, name, lastMsg, ts, unread, encrypted)
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
                        RoomSummary(roomId, shortRoomId(roomId), "", 0L, 0)
                    }
                }
            }.awaitAll().sortedByDescending { it.timestamp }
        }

    private fun fetchRoomSummary(session: MatrixSession, roomId: String): RoomSummary {
        val encoded = enc(roomId)
        var name = shortRoomId(roomId)
        var encrypted = false
        try {
            val stateBody = get(
                "${session.homeserverUrl}/_matrix/client/v3/rooms/$encoded/state",
                session.accessToken
            )
            val events = JSONArray(stateBody)
            name = resolveName(roomId, events, null, session.userId)
            encrypted = hasEncryption(events)
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
                lastMsg = previewForEvent(ev)
            }
        } catch (_: Exception) { }
        return RoomSummary(roomId, name, lastMsg, ts, 0, encrypted)
    }

    suspend fun isRoomEncrypted(session: MatrixSession, roomId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val encoded = enc(roomId)
                val body = get(
                    "${session.homeserverUrl}/_matrix/client/v3/rooms/$encoded/state/m.room.encryption",
                    session.accessToken
                )
                JSONObject(body).optString("algorithm").isNotBlank()
            } catch (_: Exception) {
                false
            }
        }

    suspend fun loadMessages(
        session: MatrixSession,
        roomId: String,
        limit: Int = 50
    ): List<ChatMessage> = withContext(Dispatchers.IO) {
        val encoded = enc(roomId)
        val body = get(
            "${session.homeserverUrl}/_matrix/client/v3/rooms/$encoded/messages?dir=b&limit=$limit",
            session.accessToken
        )
        val chunk = JSONObject(body).optJSONArray("chunk") ?: JSONArray()
        buildList {
            for (i in 0 until chunk.length()) {
                val ev = chunk.getJSONObject(i)
                val type = ev.optString("type")
                val sender = ev.optString("sender")
                val ts = ev.optLong("origin_server_ts", 0L)
                val eventId = ev.optString("event_id", "$i")
                val content = ev.optJSONObject("content")
                when (type) {
                    "m.room.message" -> {
                        add(
                            ChatMessage(
                                eventId = eventId,
                                sender = sender,
                                body = content?.optString("body").orEmpty(),
                                timestamp = ts,
                                isMine = sender == session.userId
                            )
                        )
                    }
                    "m.room.encrypted" -> {
                        add(
                            ChatMessage(
                                eventId = eventId,
                                sender = sender,
                                body = "🔒 Зашифрованное сообщение",
                                timestamp = ts,
                                isMine = sender == session.userId,
                                isEncrypted = true
                            )
                        )
                    }
                    else -> {
                        val label = stateLabel(type, content)
                        if (label != null) {
                            add(
                                ChatMessage(
                                    eventId = eventId,
                                    sender = sender,
                                    body = label,
                                    timestamp = ts,
                                    isMine = false,
                                    isState = true
                                )
                            )
                        }
                    }
                }
            }
        }.reversed()
    }

    /**
     * Sends plaintext only to non-encrypted rooms.
     * Encrypted rooms require Megolm (Olm) — throws if room is E2EE.
     */
    suspend fun sendText(session: MatrixSession, roomId: String, text: String) =
        withContext(Dispatchers.IO) {
            if (isRoomEncrypted(session, roomId)) {
                throw MatrixApiException(
                    0,
                    "Комната зашифрована (E2EE). Отправка открытым текстом запрещена. Нужен Olm/Megolm (matrix-rust-sdk).",
                    null
                )
            }
            val txn = System.currentTimeMillis().toString()
            val encoded = enc(roomId)
            val req = JSONObject().put("msgtype", "m.text").put("body", text)
            put(
                "${session.homeserverUrl}/_matrix/client/v3/rooms/$encoded/send/m.room.message/$txn",
                req.toString(),
                session.accessToken
            )
        }

    suspend fun getProfile(session: MatrixSession, userId: String = session.userId): UserProfile =
        withContext(Dispatchers.IO) {
            val encoded = enc(userId)
            try {
                val body = get(
                    "${session.homeserverUrl}/_matrix/client/v3/profile/$encoded",
                    session.accessToken
                )
                val o = JSONObject(body)
                UserProfile(
                    userId = userId,
                    displayName = o.optString("displayname").takeIf { it.isNotBlank() },
                    avatarUrl = o.optString("avatar_url").takeIf { it.isNotBlank() }
                )
            } catch (_: Exception) {
                UserProfile(userId, null, null)
            }
        }

    suspend fun setDisplayName(session: MatrixSession, name: String) = withContext(Dispatchers.IO) {
        val encoded = enc(session.userId)
        put(
            "${session.homeserverUrl}/_matrix/client/v3/profile/$encoded/displayname",
            JSONObject().put("displayname", name).toString(),
            session.accessToken
        )
    }

    suspend fun uploadAvatar(
        session: MatrixSession,
        bytes: ByteArray,
        contentType: String = "image/jpeg"
    ): String = withContext(Dispatchers.IO) {
        val mediaType = contentType.toMediaType()
        val req = Request.Builder()
            .url("${session.homeserverUrl}/_matrix/media/v3/upload")
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Content-Type", contentType)
            .post(bytes.toRequestBody(mediaType))
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Upload failed: HTTP ${resp.code}")
            JSONObject(body).getString("content_uri")
        }
    }

    suspend fun setAvatarUrl(session: MatrixSession, mxcUrl: String) = withContext(Dispatchers.IO) {
        val encoded = enc(session.userId)
        put(
            "${session.homeserverUrl}/_matrix/client/v3/profile/$encoded/avatar_url",
            JSONObject().put("avatar_url", mxcUrl).toString(),
            session.accessToken
        )
    }

    private fun hasEncryption(stateEvents: JSONArray?): Boolean {
        if (stateEvents == null) return false
        for (i in 0 until stateEvents.length()) {
            val ev = stateEvents.getJSONObject(i)
            if (ev.optString("type") == "m.room.encryption") return true
        }
        return false
    }

    private fun previewForEvent(ev: JSONObject): String {
        val type = ev.optString("type")
        val content = ev.optJSONObject("content")
        return when (type) {
            "m.room.message" -> content?.optString("body")?.take(120) ?: "Сообщение"
            "m.room.encrypted" -> "🔒 Зашифрованное сообщение"
            "m.room.member" -> {
                val m = content?.optString("membership")
                val dn = content?.optString("displayname")
                when (m) {
                    "join" -> "${dn ?: "Участник"} вступил"
                    "leave" -> "Выход из комнаты"
                    "invite" -> "Приглашение"
                    else -> m ?: "Участник"
                }
            }
            "m.room.name" -> "Название: ${content?.optString("name") ?: ""}"
            else -> stateLabel(type, content) ?: "Событие"
        }
    }

    private fun stateLabel(type: String, content: JSONObject?): String? = when (type) {
        "m.room.encrypted" -> "🔒 Зашифровано"
        "m.room.power_levels" -> "Права изменены"
        "m.room.encryption" -> "Включено шифрование"
        "m.room.create" -> "Комната создана"
        "org.matrix.msc3401.call.member" -> "Звонок"
        else -> null
    }

    private fun resolveName(
        roomId: String,
        stateEvents: JSONArray?,
        summary: JSONObject?,
        myUserId: String
    ): String {
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
            val members = mutableListOf<Pair<String, String>>()
            for (i in 0 until stateEvents.length()) {
                val ev = stateEvents.getJSONObject(i)
                if (ev.optString("type") == "m.room.member" &&
                    ev.optJSONObject("content")?.optString("membership") == "join"
                ) {
                    val uid = ev.optString("state_key")
                    val dn = ev.optJSONObject("content")?.optString("displayname")
                        ?.takeIf { it.isNotBlank() } ?: uid.removePrefix("@").substringBefore(":")
                    if (uid.isNotBlank()) members += uid to dn
                }
            }
            val others = members.filter { it.first != myUserId }
            if (others.size == 1) return others[0].second
            if (others.isNotEmpty() && others.size <= 3) {
                return others.joinToString(", ") { it.second }
            }
        }
        val heroes = summary?.optJSONArray("m.heroes")
        if (heroes != null && heroes.length() > 0) {
            return heroes.getString(0).removePrefix("@").substringBefore(":")
        }
        return shortRoomId(roomId)
    }

    private fun shortRoomId(roomId: String): String =
        if (roomId.length > 18) roomId.take(12) + "…" else roomId

    private fun enc(s: String) = java.net.URLEncoder.encode(s, Charsets.UTF_8.name())

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
