package com.minebot.app

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PrefsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("minebot_prefs", Context.MODE_PRIVATE)

    fun getToken(): String? = prefs.getString("token", null)

    fun setToken(token: String?) {
        prefs.edit().putString("token", token).apply()
    }

    fun getTutorialSeen(): Boolean = prefs.getBoolean("tutorial_seen", false)

    fun setTutorialSeen(seen: Boolean) {
        prefs.edit().putBoolean("tutorial_seen", seen).apply()
    }

    fun getServers(): List<ServerRecord> {
        val raw = prefs.getString("servers_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val ip = obj.optString("ip")
                    val port = obj.optInt("port", 19132)
                    if (id.isNotBlank() && ip.isNotBlank()) {
                        add(ServerRecord(id = id, ip = ip, port = port))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setServers(servers: List<ServerRecord>) {
        val arr = JSONArray()
        servers.forEach { server ->
            arr.put(
                JSONObject().apply {
                    put("id", server.id)
                    put("ip", server.ip)
                    put("port", server.port)
                }
            )
        }
        prefs.edit().putString("servers_json", arr.toString()).apply()
    }

    fun getSelectedServerId(): String = prefs.getString("selected_server_id", "") ?: ""

    fun setSelectedServerId(value: String) {
        prefs.edit().putString("selected_server_id", value).apply()
    }

    fun getConnectionType(): ConnectionType {
        return when (prefs.getString("connection_type", ConnectionType.ONLINE.name)) {
            ConnectionType.OFFLINE.name -> ConnectionType.OFFLINE
            else -> ConnectionType.ONLINE
        }
    }

    fun setConnectionType(type: ConnectionType) {
        prefs.edit().putString("connection_type", type.name).apply()
    }

    fun getOfflineUsername(): String = prefs.getString("offline_username", "") ?: ""

    fun setOfflineUsername(value: String) {
        prefs.edit().putString("offline_username", value).apply()
    }

    fun getDeviceId(context: Context): String {
        val saved = prefs.getString("device_id", null)
        if (!saved.isNullOrBlank()) return saved

        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )?.takeIf { it.isNotBlank() }

        val resolved = androidId ?: UUID.randomUUID().toString()
        prefs.edit().putString("device_id", resolved).apply()
        return resolved
    }
}
