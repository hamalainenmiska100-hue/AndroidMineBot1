package com.minebot.app

enum class AppTab { BOT, STATUS, SETTINGS }

enum class ConnectionType { ONLINE, OFFLINE }

data class LinkedAccount(
    val id: String,
    val label: String,
    val createdAt: Long? = null,
    val tokenAcquiredAt: Long? = null,
    val lastUsedAt: Long? = null,
    val legacy: Boolean = false
)

data class PendingLink(
    val status: String,
    val verificationUri: String? = null,
    val userCode: String? = null,
    val accountId: String? = null,
    val error: String? = null,
    val createdAt: Long? = null,
    val expiresAt: Long? = null
)

data class ServerRecord(
    val id: String,
    val ip: String,
    val port: Int
) {
    val label: String get() = "$ip:$port"
}

data class BotStatusResponse(
    val sessionId: String? = null,
    val status: String = "offline",
    val connected: Boolean? = null,
    val isReconnecting: Boolean = false,
    val reconnectAttempt: Int = 0,
    val server: String? = null,
    val startedAt: Long? = null,
    val uptimeMs: Long? = null,
    val lastConnectedAt: Long? = null,
    val lastError: String? = null,
    val lastDisconnectReason: String? = null,
    val connectionType: String? = null,
    val accountId: String? = null
)

data class HealthResponse(
    val status: String = "unknown",
    val uptimeSec: Long = 0L,
    val bots: Int = 0,
    val memoryMb: Int = 0,
    val maxBots: Int = 0
)

data class RedeemResponse(
    val token: String,
    val userId: String
)

data class AccountsResponse(
    val linked: List<LinkedAccount> = emptyList(),
    val pendingLink: PendingLink? = null
)

data class LinkStartResponse(
    val status: String,
    val verificationUri: String? = null,
    val userCode: String? = null,
    val accountId: String? = null,
    val error: String? = null,
    val expiresAt: Long? = null
)

data class UiMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String
)
