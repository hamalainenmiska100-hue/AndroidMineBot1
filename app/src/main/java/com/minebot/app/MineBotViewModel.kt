package com.minebot.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class MineBotViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PrefsStore(application)
    private val api = ApiClient(BuildConfig.BACKEND_URL, application, prefs)

    var tutorialSeen by mutableStateOf(prefs.getTutorialSeen())
        private set

    var isLoggedIn by mutableStateOf(prefs.getToken() != null)
        private set

    private var token: String? = prefs.getToken()

    var selectedTab by mutableStateOf(AppTab.BOT)
        private set
    var linkedAccounts by mutableStateOf<List<LinkedAccount>>(emptyList())
        private set
    var pendingLink by mutableStateOf<PendingLink?>(null)
        private set
    var botStatus by mutableStateOf<BotStatusResponse?>(null)
        private set
    var health by mutableStateOf<HealthResponse?>(null)
        private set
    var serverLatencyMs by mutableStateOf<Int?>(null)
        private set
    var snackbar by mutableStateOf<UiMessage?>(null)
        private set

    var servers by mutableStateOf(prefs.getServers())
        private set
    var selectedServerId by mutableStateOf(
        prefs.getSelectedServerId().ifBlank { servers.firstOrNull()?.id.orEmpty() }
    )
        private set
    var connectionType by mutableStateOf(prefs.getConnectionType())
        private set
    var offlineUsername by mutableStateOf(prefs.getOfflineUsername())
        private set

    var isBusy by mutableStateOf(false)
        private set
    var isRefreshingStatus by mutableStateOf(false)
        private set

    private var lastStatusValue: String? = null

    val maxGlobalMemoryMb = 512

    init {
        if (isLoggedIn) {
            viewModelScope.launch {
                refreshAll(showTransitionFeedback = false)
            }
        }
    }

    val selectedServer: ServerRecord?
        get() = servers.firstOrNull { it.id == selectedServerId }

    val firstLinkedAccount: LinkedAccount?
        get() = linkedAccounts.firstOrNull()

    val isBotRunning: Boolean
        get() {
            val status = botStatus ?: return false
            if (status.connected == true) return true
            return status.status.lowercase() in setOf("connected", "starting", "reconnecting", "disconnected")
        }

    fun markTutorialSeen() {
        tutorialSeen = true
        prefs.setTutorialSeen(true)
    }

    fun selectTab(tab: AppTab) {
        selectedTab = tab
    }

    private fun completeLogin(newToken: String) {
        token = newToken
        isLoggedIn = true
        prefs.setToken(newToken)
    }

    fun dismissSnackbar(messageId: Long) {
        if (snackbar?.id == messageId) snackbar = null
    }

    fun login(code: String) {
        val cleaned = code.trim().uppercase()
        if (cleaned.length != 14) {
            postSnackbar("Please enter a valid access code.")
            return
        }

        if (isBusy) return
        viewModelScope.launch {
            isBusy = true
            try {
                val response = api.redeemCode(cleaned)
                completeLogin(response.token)
                postSnackbar("Login successful.")
                refreshAll(showTransitionFeedback = false)
            } catch (e: Exception) {
                postSnackbar(e.message ?: "Login failed.")
            } finally {
                isBusy = false
            }
        }
    }

    fun logout() {
        val currentToken = token
        viewModelScope.launch {
            isBusy = true
            try {
                currentToken?.let { api.logout(it) }
            } catch (_: Exception) {
            } finally {
                token = null
                isLoggedIn = false
                linkedAccounts = emptyList()
                pendingLink = null
                botStatus = null
                health = null
                serverLatencyMs = null
                lastStatusValue = null
                prefs.setToken(null)
                isBusy = false
                postSnackbar("Signed out.")
            }
        }
    }

    suspend fun refreshAll(showTransitionFeedback: Boolean = false) {
        if (token == null) return
        isRefreshingStatus = true
        try {
            refreshBotStatus(showTransitionFeedback)
            refreshHealth()
            refreshAccounts()
        } finally {
            isRefreshingStatus = false
        }
    }

    suspend fun refreshAccounts() {
        val currentToken = token ?: return
        try {
            val response = api.fetchAccounts(currentToken)
            linkedAccounts = response.linked
            pendingLink = if (
                response.pendingLink?.status?.lowercase() == "success" &&
                response.linked.isNotEmpty()
            ) {
                null
            } else {
                response.pendingLink
            }
        } catch (_: Exception) {
        }
    }

    suspend fun refreshHealth() {
        val start = System.currentTimeMillis()
        try {
            val response = api.fetchHealth()
            health = response
            serverLatencyMs = (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
        }
    }

    suspend fun refreshBotStatus(showTransitionFeedback: Boolean = false) {
        val currentToken = token ?: return
        try {
            val response = api.fetchBotStatus(currentToken)
            val previous = lastStatusValue
            botStatus = response
            lastStatusValue = response.status.lowercase()

            if (showTransitionFeedback && previous != response.status.lowercase()) {
                when (response.status.lowercase()) {
                    "connected" -> postSnackbar("Bot connected.")
                    "reconnecting" -> postSnackbar("Bot reconnecting...")
                    "error" -> postSnackbar(response.lastError ?: "Bot error.")
                    "offline" -> postSnackbar("Bot offline.")
                }
            }
        } catch (e: Exception) {
            if (showTransitionFeedback) postSnackbar(e.message ?: "Could not refresh status.")
        }
    }

    fun pollPendingLinkIfNeeded() {
        val currentPending = pendingLink ?: return
        if (currentPending.status !in listOf("starting", "pending")) return

        viewModelScope.launch {
            val currentToken = token ?: return@launch
            try {
                val response = api.fetchMicrosoftLinkStatus(currentToken)
                pendingLink = PendingLink(
                    status = response.status,
                    verificationUri = response.verificationUri,
                    userCode = response.userCode,
                    accountId = response.accountId,
                    error = response.error,
                    createdAt = pendingLink?.createdAt,
                    expiresAt = response.expiresAt
                )
                if (response.status == "success") {
                    postSnackbar("Microsoft account linked.")
                    refreshAccounts()
                } else if (response.status == "error") {
                    postSnackbar(response.error ?: "Microsoft link failed.")
                }
            } catch (_: Exception) {
            }
        }
    }

    fun beginMicrosoftLink() {
        val currentToken = token ?: return
        if (isBusy) return

        viewModelScope.launch {
            isBusy = true
            try {
                val response = api.startMicrosoftLink(currentToken)
                pendingLink = PendingLink(
                    status = response.status,
                    verificationUri = response.verificationUri,
                    userCode = response.userCode,
                    accountId = response.accountId,
                    error = response.error,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = response.expiresAt
                )
                postSnackbar("Microsoft login started.")
            } catch (e: Exception) {
                postSnackbar(e.message ?: "Could not start Microsoft login.")
            } finally {
                isBusy = false
            }
        }
    }

    fun refreshMicrosoftLinkStatus() {
        val currentToken = token ?: return
        viewModelScope.launch {
            try {
                val response = api.fetchMicrosoftLinkStatus(currentToken)
                pendingLink = PendingLink(
                    status = response.status,
                    verificationUri = response.verificationUri,
                    userCode = response.userCode,
                    accountId = response.accountId,
                    error = response.error,
                    createdAt = pendingLink?.createdAt,
                    expiresAt = response.expiresAt
                )
                if (response.status == "success") {
                    postSnackbar("Microsoft account linked.")
                    refreshAccounts()
                } else if (response.status == "error") {
                    postSnackbar(response.error ?: "Microsoft link failed.")
                }
            } catch (e: Exception) {
                postSnackbar(e.message ?: "Could not refresh link status.")
            }
        }
    }

    fun unlinkFirstAccount() {
        val currentToken = token ?: return
        val account = firstLinkedAccount ?: return
        if (isBusy) return

        viewModelScope.launch {
            isBusy = true
            try {
                api.unlinkAccount(currentToken, account.id)
                postSnackbar("Account unlinked.")
                refreshAccounts()
            } catch (e: Exception) {
                postSnackbar(e.message ?: "Could not unlink account.")
            } finally {
                isBusy = false
            }
        }
    }

    fun addServer(ip: String, port: Int) {
        val trimmedIp = ip.trim()
        if (trimmedIp.isEmpty()) {
            postSnackbar("Please enter an IP address.")
            return
        }

        val server = ServerRecord(
            id = UUID.randomUUID().toString(),
            ip = trimmedIp,
            port = port
        )
        servers = servers + server
        prefs.setServers(servers)

        if (selectedServerId.isBlank()) {
            selectedServerId = server.id
            prefs.setSelectedServerId(server.id)
        }

        postSnackbar("Server added.")
    }

    fun removeServer(serverId: String) {
        val wasSelected = selectedServerId == serverId
        servers = servers.filterNot { it.id == serverId }
        prefs.setServers(servers)

        if (wasSelected) {
            selectedServerId = servers.firstOrNull()?.id.orEmpty()
            prefs.setSelectedServerId(selectedServerId)
        }
        postSnackbar("Server removed.")
    }

    fun selectServer(serverId: String) {
        selectedServerId = serverId
        prefs.setSelectedServerId(serverId)
    }

    fun setConnectionType(type: ConnectionType) {
        connectionType = type
        prefs.setConnectionType(type)
    }

    fun setOfflineUsername(value: String) {
        offlineUsername = value
        prefs.setOfflineUsername(value)
    }

    fun startBot() {
        val currentToken = token ?: return
        val server = selectedServer
        if (server == null) {
            selectedTab = AppTab.SETTINGS
            postSnackbar("Add a server first in Settings.")
            return
        }

        if (isBusy) return

        if (connectionType == ConnectionType.ONLINE && linkedAccounts.isEmpty()) {
            postSnackbar("Link a Microsoft account first.")
            return
        }

        viewModelScope.launch {
            isBusy = true
            try {
                api.startBot(
                    token = currentToken,
                    server = server,
                    connectionType = connectionType,
                    offlineUsername = offlineUsername
                )
                postSnackbar("Bot starting...")
                delay(400)
                refreshAll(showTransitionFeedback = false)
            } catch (e: Exception) {
                postSnackbar(e.message ?: "Could not start bot.")
            } finally {
                isBusy = false
            }
        }
    }

    fun stopBot() {
        val currentToken = token ?: return
        if (isBusy) return

        viewModelScope.launch {
            isBusy = true
            try {
                api.stopBot(currentToken)
                postSnackbar("Bot stopped.")
                delay(300)
                refreshAll(showTransitionFeedback = false)
            } catch (e: Exception) {
                postSnackbar(e.message ?: "Could not stop bot.")
            } finally {
                isBusy = false
            }
        }
    }

    fun reconnectBot() {
        val currentToken = token ?: return
        if (isBusy) return

        viewModelScope.launch {
            isBusy = true
            try {
                api.reconnectBot(currentToken)
                postSnackbar("Reconnect requested.")
                delay(300)
                refreshAll(showTransitionFeedback = false)
            } catch (e: Exception) {
                postSnackbar(e.message ?: "Could not reconnect bot.")
            } finally {
                isBusy = false
            }
        }
    }

    fun openDiscord() {
        val context = getApplication<Application>()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/CNZsQDBYvw"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openPendingLink() {
        val url = pendingLink?.verificationUri ?: return
        val context = getApplication<Application>()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun postSnackbar(message: String) {
        snackbar = UiMessage(text = message)
    }
}
