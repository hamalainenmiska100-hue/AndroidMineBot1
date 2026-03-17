package com.minebot.app

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ApiClient(
    private val baseUrl: String,
    private val context: Context,
    private val prefsStore: PrefsStore
) {
    suspend fun redeemCode(code: String): RedeemResponse {
        val data = request(
            path = "/auth/redeem",
            method = "POST",
            body = JSONObject().put("code", code)
        )
        return RedeemResponse(
            token = data.getString("token"),
            userId = data.getString("userId")
        )
    }

    suspend fun fetchAccounts(token: String): AccountsResponse {
        val data = request(path = "/accounts", token = token)
        return AccountsResponse(
            linked = parseLinkedAccounts(data.optJSONArray("linked")),
            pendingLink = data.optJSONObject("pendingLink")?.let(::parsePendingLink)
        )
    }

    suspend fun fetchBotStatus(token: String): BotStatusResponse {
        val data = request(path = "/bots", token = token)
        return parseBotStatus(data)
    }

    suspend fun fetchHealth(): HealthResponse {
        val data = request(path = "/health", token = null)
        return HealthResponse(
            status = data.optString("status", "unknown"),
            uptimeSec = data.optLong("uptimeSec", 0L),
            bots = data.optInt("bots", 0),
            memoryMb = data.optInt("memoryMb", 0),
            maxBots = data.optInt("maxBots", 0)
        )
    }

    suspend fun startMicrosoftLink(token: String): LinkStartResponse {
        val data = request(path = "/accounts/link/start", method = "POST", token = token)
        return parseLinkStart(data)
    }

    suspend fun fetchMicrosoftLinkStatus(token: String): LinkStartResponse {
        val data = request(path = "/accounts/link/status", token = token)
        return parseLinkStart(data)
    }

    suspend fun unlinkAccount(token: String, accountId: String) {
        request(
            path = "/accounts/unlink",
            method = "POST",
            token = token,
            body = JSONObject().put("accountId", accountId)
        )
    }

    suspend fun startBot(
        token: String,
        server: ServerRecord,
        connectionType: ConnectionType,
        offlineUsername: String
    ) {
        val body = JSONObject().apply {
            put("ip", server.ip)
            put("port", server.port)
            put("connectionType", if (connectionType == ConnectionType.OFFLINE) "offline" else "online")
            if (offlineUsername.isNotBlank()) put("offlineUsername", offlineUsername)
        }
        request(path = "/bots/start", method = "POST", token = token, body = body)
    }

    suspend fun stopBot(token: String) {
        request(path = "/bots/stop", method = "POST", token = token)
    }

    suspend fun reconnectBot(token: String) {
        request(path = "/bots/reconnect", method = "POST", token = token)
    }

    suspend fun logout(token: String) {
        request(path = "/auth/logout", method = "POST", token = token)
    }

    private suspend fun request(
        path: String,
        method: String = "GET",
        token: String? = null,
        body: JSONObject? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = URL(baseUrl.trimEnd('/') + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            useCaches = false
            doInput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            attachDeviceHeaders(this)
        }

        if (body != null) {
            connection.doOutput = true
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }
        }

        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        connection.disconnect()

        val json = if (text.isNotBlank()) JSONObject(text) else JSONObject()
        if (!json.optBoolean("success", responseCode in 200..299)) {
            throw IllegalStateException(json.optString("error", "Request failed ($responseCode)"))
        }
        json.optJSONObject("data") ?: JSONObject()
    }

    private fun attachDeviceHeaders(connection: HttpURLConnection) {
        connection.setRequestProperty("X-Client-Device-ID", prefsStore.getDeviceId(context))
        connection.setRequestProperty("X-Client-Device-Name", "${Build.MANUFACTURER} ${Build.MODEL}")
        connection.setRequestProperty("X-Client-Platform", "Android")
        connection.setRequestProperty("X-Client-Model", Build.MODEL ?: "Unknown")
    }

    private fun parseLinkStart(data: JSONObject): LinkStartResponse {
        return LinkStartResponse(
            status = data.optString("status", "none"),
            verificationUri = data.optStringOrNull("verificationUri"),
            userCode = data.optStringOrNull("userCode"),
            accountId = data.optStringOrNull("accountId"),
            error = data.optStringOrNull("error"),
            expiresAt = data.optLongOrNull("expiresAt")
        )
    }

    private fun parseLinkedAccounts(array: JSONArray?): List<LinkedAccount> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    LinkedAccount(
                        id = item.optString("id"),
                        label = item.optString("label", "Account ${i + 1}"),
                        createdAt = item.optLongOrNull("createdAt"),
                        tokenAcquiredAt = item.optLongOrNull("tokenAcquiredAt"),
                        lastUsedAt = item.optLongOrNull("lastUsedAt"),
                        legacy = item.optBoolean("legacy", false)
                    )
                )
            }
        }
    }

    private fun parsePendingLink(json: JSONObject): PendingLink {
        return PendingLink(
            status = json.optString("status", "none"),
            verificationUri = json.optStringOrNull("verificationUri"),
            userCode = json.optStringOrNull("userCode"),
            accountId = json.optStringOrNull("accountId"),
            error = json.optStringOrNull("error"),
            createdAt = json.optLongOrNull("createdAt"),
            expiresAt = json.optLongOrNull("expiresAt")
        )
    }

    private fun parseBotStatus(data: JSONObject): BotStatusResponse {
        return BotStatusResponse(
            sessionId = data.optStringOrNull("sessionId"),
            status = data.optString("status", "offline"),
            connected = if (data.has("connected")) data.optBoolean("connected") else null,
            isReconnecting = data.optBoolean("isReconnecting", false),
            reconnectAttempt = data.optInt("reconnectAttempt", 0),
            server = data.optStringOrNull("server"),
            startedAt = data.optLongOrNull("startedAt"),
            uptimeMs = data.optLongOrNull("uptimeMs"),
            lastConnectedAt = data.optLongOrNull("lastConnectedAt"),
            lastError = data.optStringOrNull("lastError"),
            lastDisconnectReason = data.optStringOrNull("lastDisconnectReason"),
            connectionType = data.optStringOrNull("connectionType"),
            accountId = data.optStringOrNull("accountId")
        )
    }
}

private fun JSONObject.optStringOrNull(key: String): String? {
    val value = optString(key, "")
    return value.ifBlank { null }
}

private fun JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return optLong(key)
}
