package com.minebot.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val MineBotColors = darkColorScheme(
    primary = Color(0xFF6EA8FF),
    secondary = Color(0xFF9BB8FF),
    tertiary = Color(0xFF7DE0C3),
    surface = Color(0xFF111318),
    surfaceContainer = Color(0xFF171A20),
    background = Color(0xFF090B0E)
)

@Composable
fun MineBotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MineBotColors,
        typography = MaterialTheme.typography,
        content = content
    )
}

@Composable
fun MineBotApp(viewModel: MineBotViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val latestMessage by rememberUpdatedState(viewModel.snackbar)

    LaunchedEffect(viewModel.isLoggedIn) {
        if (viewModel.isLoggedIn) {
            viewModel.refreshAll(showTransitionFeedback = false)
            while (viewModel.isLoggedIn) {
                delay(30_000)
                viewModel.refreshAll(showTransitionFeedback = true)
                viewModel.pollPendingLinkIfNeeded()
            }
        }
    }

    LaunchedEffect(latestMessage?.id) {
        val payload = latestMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(payload.text)
        viewModel.dismissSnackbar(payload.id)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !viewModel.tutorialSeen -> TutorialScreen(onContinue = viewModel::markTutorialSeen)
            !viewModel.isLoggedIn -> LoginScreen(viewModel)
            else -> MainScaffold(viewModel, snackbarHostState)
        }
    }
}

@Composable
private fun MainScaffold(
    viewModel: MineBotViewModel,
    snackbarHostState: SnackbarHostState
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = viewModel.selectedTab == AppTab.BOT,
                    onClick = { viewModel.selectTab(AppTab.BOT) },
                    icon = { Icon(Icons.Rounded.Android, contentDescription = null) },
                    label = { Text("Bot") },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    )
                )
                NavigationBarItem(
                    selected = viewModel.selectedTab == AppTab.STATUS,
                    onClick = { viewModel.selectTab(AppTab.STATUS) },
                    icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                    label = { Text("Status") },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    )
                )
                NavigationBarItem(
                    selected = viewModel.selectedTab == AppTab.SETTINGS,
                    onClick = { viewModel.selectTab(AppTab.SETTINGS) },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    )
                )
            }
        }
    ) { padding ->
        when (viewModel.selectedTab) {
            AppTab.BOT -> BotScreen(viewModel, padding)
            AppTab.STATUS -> StatusScreen(viewModel, padding)
            AppTab.SETTINGS -> SettingsScreen(viewModel, padding)
        }
    }
}

@Composable
private fun TutorialScreen(onContinue: () -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "MineBot",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "Welcome.",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AppCard {
            Text(
                text = "This app lets you run a Minecraft AFK bot remotely from your Android phone.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.defaultMinSize(minHeight = 8.dp))
            TutorialStep("1", "Enter your access code")
            TutorialStep("2", "Link your Microsoft account")
            TutorialStep("3", "Start and manage your bot")
            Spacer(modifier = Modifier.defaultMinSize(minHeight = 8.dp))
            Text(
                text = "Your bot can stay online even when the app is closed.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun TutorialStep(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(number, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LoginScreen(viewModel: MineBotViewModel) {
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "MineBot",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.defaultMinSize(minHeight = 12.dp))
        Text(
            text = "Enter your access code to continue.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.defaultMinSize(minHeight = 18.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { input ->
                code = input.uppercase()
                    .replace(Regex("[^A-Z0-9]"), "")
                    .take(12)
                    .chunked(4)
                    .joinToString("-")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Access code") },
            placeholder = { Text("XXXX-XXXX-XXXX") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii
            )
        )
        Spacer(modifier = Modifier.defaultMinSize(minHeight = 16.dp))
        Button(
            onClick = { viewModel.login(code) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
            enabled = !viewModel.isBusy
        ) {
            Text(if (viewModel.isBusy) "Signing in..." else "Sign In")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BotScreen(viewModel: MineBotViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    var serverMenuExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenTitle("Bot", "Quick controls and account setup.")
        }

        item {
            HeroStatusCard(viewModel.botStatus)
        }

        item {
            AppCard {
                SectionTitle("Microsoft Account")
                if (viewModel.linkedAccounts.isEmpty()) {
                    Text(
                        text = "No linked account yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    viewModel.linkedAccounts.forEach { account ->
                        ServerRow(title = account.label, value = account.id)
                    }
                }

                viewModel.pendingLink?.let { pending ->
                    Spacer(modifier = Modifier.defaultMinSize(minHeight = 12.dp))
                    Text("Login status: ${pending.status}", fontWeight = FontWeight.SemiBold)
                    pending.userCode?.let { code ->
                        Spacer(modifier = Modifier.defaultMinSize(minHeight = 8.dp))
                        ServerRow(title = "Code", value = code)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(onClick = { viewModel.openPendingLink() }) {
                                Icon(Icons.Rounded.Link, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open link")
                            }
                            FilledTonalButton(onClick = {
                                clipboard.setPrimaryClip(ClipData.newPlainText("Microsoft code", code))
                                viewModel.refreshMicrosoftLinkStatus()
                            }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Copy")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.defaultMinSize(minHeight = 12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(onClick = { viewModel.beginMicrosoftLink() }, enabled = !viewModel.isBusy) {
                        Icon(Icons.Rounded.Link, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Link account")
                    }
                    OutlinedButton(
                        onClick = { viewModel.unlinkFirstAccount() },
                        enabled = !viewModel.isBusy && viewModel.firstLinkedAccount != null
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlink")
                    }
                    OutlinedButton(
                        onClick = { viewModel.refreshMicrosoftLinkStatus() },
                        enabled = !viewModel.isBusy && viewModel.pendingLink != null
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refresh link")
                    }
                }
            }
        }

        item {
            AppCard {
                SectionTitle("Server")
                Box {
                    OutlinedButton(
                        onClick = { serverMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = viewModel.selectedServer?.label ?: "Select a saved server",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = serverMenuExpanded,
                        onDismissRequest = { serverMenuExpanded = false }
                    ) {
                        viewModel.servers.forEach { server ->
                            DropdownMenuItem(
                                text = { Text(server.label) },
                                onClick = {
                                    serverMenuExpanded = false
                                    viewModel.selectServer(server.id)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.defaultMinSize(minHeight = 12.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = viewModel.connectionType == ConnectionType.ONLINE,
                        onClick = { viewModel.setConnectionType(ConnectionType.ONLINE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Online")
                    }
                    SegmentedButton(
                        selected = viewModel.connectionType == ConnectionType.OFFLINE,
                        onClick = { viewModel.setConnectionType(ConnectionType.OFFLINE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Offline")
                    }
                }

                if (viewModel.connectionType == ConnectionType.OFFLINE) {
                    Spacer(modifier = Modifier.defaultMinSize(minHeight = 12.dp))
                    OutlinedTextField(
                        value = viewModel.offlineUsername,
                        onValueChange = { viewModel.setOfflineUsername(it.take(16)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Offline username") }
                    )
                }

                Spacer(modifier = Modifier.defaultMinSize(minHeight = 14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.startBot() },
                        enabled = !viewModel.isBusy
                    ) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start")
                    }
                    FilledTonalButton(
                        onClick = { viewModel.reconnectBot() },
                        enabled = !viewModel.isBusy && viewModel.botStatus?.sessionId != null
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reconnect")
                    }
                    OutlinedButton(
                        onClick = { viewModel.stopBot() },
                        enabled = !viewModel.isBusy && viewModel.botStatus?.sessionId != null
                    ) {
                        Text("Stop")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusScreen(viewModel: MineBotViewModel, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenTitle("Status", "Backend health and bot details.") }

        item {
            AppCard {
                SectionTitle("Bot status")
                ServerRow("State", statusTitle(viewModel.botStatus?.status))
                ServerRow("Connected", if (viewModel.botStatus?.connected == true) "Yes" else "No")
                ServerRow("Server", viewModel.botStatus?.server ?: "-")
                ServerRow("Uptime", formatUptime(viewModel.botStatus?.uptimeMs))
                ServerRow("Last connected", formatTimestamp(viewModel.botStatus?.lastConnectedAt))
                ServerRow("Reconnect attempt", "${viewModel.botStatus?.reconnectAttempt ?: 0}")
                viewModel.botStatus?.lastError?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.defaultMinSize(minHeight = 8.dp))
                    Text("Last error", fontWeight = FontWeight.SemiBold)
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item {
            AppCard {
                SectionTitle("Server health")
                val health = viewModel.health
                ServerRow("Backend", health?.status ?: "-")
                ServerRow("Latency", viewModel.serverLatencyMs?.let { "$it ms" } ?: "-")
                ServerRow("Memory", health?.memoryMb?.let { "$it MB" } ?: "-")
                ServerRow("Bots", if (health != null) "${health.bots} / ${health.maxBots}" else "-")
                val progress = ((health?.memoryMb ?: 0).toFloat() / viewModel.maxGlobalMemoryMb.toFloat()).coerceIn(0f, 1f)
                Spacer(modifier = Modifier.defaultMinSize(minHeight = 10.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SettingsScreen(viewModel: MineBotViewModel, padding: PaddingValues) {
    var showAddServer by remember { mutableStateOf(false) }
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("19132") }

    if (showAddServer) {
        AlertDialog(
            onDismissRequest = { showAddServer = false },
            title = { Text("Add server") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it },
                        singleLine = true,
                        label = { Text("IP address") }
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        singleLine = true,
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsedPort = port.toIntOrNull() ?: 19132
                    viewModel.addServer(ip, parsedPort)
                    ip = ""
                    port = "19132"
                    showAddServer = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddServer = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenTitle("Settings", "Servers, community and session.") }

        item {
            AppCard {
                SectionTitle("Servers")
                if (viewModel.servers.isEmpty()) {
                    Text("No saved servers yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    viewModel.servers.forEach { server ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(server.ip, fontWeight = FontWeight.SemiBold)
                                Text("Port ${server.port}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { viewModel.selectServer(server.id) }) {
                                Text(if (viewModel.selectedServerId == server.id) "Selected" else "Select")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(onClick = { viewModel.removeServer(server.id) }) {
                                Text("Remove")
                            }
                        }
                        Spacer(modifier = Modifier.defaultMinSize(minHeight = 8.dp))
                    }
                }
                Button(onClick = { showAddServer = true }) {
                    Icon(Icons.Rounded.Storage, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add server")
                }
            }
        }

        item {
            AppCard {
                SectionTitle("Community")
                FilledTonalButton(onClick = { viewModel.openDiscord() }) {
                    Icon(Icons.Rounded.Link, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Join our Discord")
                }
            }
        }

        item {
            AppCard {
                SectionTitle("About")
                Text("Made with love ❤️")
                Text("Developer", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("@ilovecatssm2")
            }
        }

        item {
            AppCard {
                SectionTitle("Session")
                Button(
                    onClick = { viewModel.logout() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign out")
                }
            }
        }
    }
}

@Composable
private fun HeroStatusCard(botStatus: BotStatusResponse?) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Bot Status", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.defaultMinSize(minHeight = 12.dp))
                StatusPill(botStatus?.status ?: "offline")
            }
            Icon(
                imageVector = if ((botStatus?.status ?: "offline").lowercase() == "connected") {
                    Icons.Rounded.CheckCircle
                } else {
                    Icons.Rounded.Terminal
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.defaultMinSize(minHeight = 14.dp))
        ServerRow("Server", botStatus?.server ?: "-")
        ServerRow("Uptime", formatUptime(botStatus?.uptimeMs))
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = when (status.lowercase()) {
        "connected" -> Color(0xFF58C27D)
        "starting" -> Color(0xFFF6C453)
        "reconnecting" -> Color(0xFFFFA94D)
        "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Text(
            text = statusTitle(status),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.defaultMinSize(minHeight = 12.dp))
}

@Composable
private fun AppCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            content = content
        )
    }
}

@Composable
private fun ServerRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(0.42f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.58f),
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun statusTitle(raw: String?): String {
    return when ((raw ?: "offline").lowercase()) {
        "connected" -> "Online"
        "starting" -> "Starting"
        "reconnecting" -> "Reconnecting"
        "disconnected" -> "Disconnected"
        "error" -> "Error"
        else -> "Offline"
    }
}

private fun formatUptime(value: Long?): String {
    val ms = value ?: return "-"
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

private fun formatTimestamp(value: Long?): String {
    val millis = value ?: return "-"
    return java.text.DateFormat.getDateTimeInstance().format(java.util.Date(millis))
}
