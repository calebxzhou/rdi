package calebxzau.rdi.client

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import calebxzau.rdi.client.ui.AppBackgroundProvider
import calebxzau.rdi.client.ui.LocalAppBackgroundPainter
import calebxzhou.mykotutils.std.decodeBase64
import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.mykotutils.std.jarResource
import calebxzhou.rdi.client.auth.AccountSessionStore
import calebxzhou.rdi.client.auth.LocalCredentials
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.service.ClientDirs
import calebxzhou.rdi.client.service.ClientTaskManager
// import calebxzhou.rdi.client.service.LocalMinecraftReuseService
import calebxzhou.rdi.client.service.NodeRefreshCoordinator
import calebxzhou.rdi.client.service.PlayerService
import calebxzhou.rdi.client.service.UpdateService
import calebxzhou.rdi.client.service.UpdaterUpdateResult
import calebxzhou.rdi.client.service.warmUpHwSpecCache
import calebxzau.rdi.client.modcatalog.createModCatalog
import calebxzhou.rdi.client.ui.AppNavigation
import calebxzhou.rdi.client.ui.McGameSession
import calebxzhou.rdi.client.ui.McPlayStore
import calebxzau.rdi.client.ui.RTheme
import calebxzhou.rdi.client.ui.navigateRoot
import calebxzhou.rdi.client.ui.screen.*
import calebxzau.rdi.client.ui.window.WindowChrome
import calebxzau.rdi.client.ui.window.activeMcSessions
import calebxzhou.rdi.client.Const
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.model.Task2Entry
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.net.ktorClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.awt.Toolkit

lateinit var ScreenSize: Pair<Dp, Dp>

fun main() {
    if (DEBUG) {
        System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT")
    }
    clearIncompleteModDownloadsOnStartup()
    clearPackProcDirOnStartup()
    // LocalMinecraftReuseService.start()
    GlobalScope.launch(Dispatchers.IO) {
        warmUpHwSpecCache()
    }
    GlobalScope.launch(Dispatchers.IO) {
        NodeRefreshCoordinator.refreshCurrent()
    }
    initializeLoggedAccountOnStartup()
    val modCatalog = createModCatalog(
        httpClient = ktorClient,
        identityDatabaseMaterializationDir = ClientDirs.toolsDir.resolve("mod-catalog").toPath(),
        onWarning = { cause -> lgr.warn(cause) { "模组目录本地索引不可用或请求降级" } }
    )
    try {
        application {
        val windowIcon = remember {
            jarResource("icon.png").use { stream ->
                BitmapPainter(stream.readAllBytes().decodeToImageBitmap())
            }
        }

        // 设置窗口初始大小为屏幕的2/3，并居中显示
        val screen = remember { Toolkit.getDefaultToolkit().screenSize }
        ScreenSize = (screen.width * 2 / 3).dp to (screen.height * 2 / 3).dp
        val windowState = rememberWindowState(
            width = ScreenSize.first,
            height = ScreenSize.second,
            position = WindowPosition(Alignment.Center)
        )
        val minimumSize = remember(screen) { Dimension(screen.width / 3, screen.height / 3) }
        val taskEntries by ClientTaskManager.entries.collectAsState()
        val activeTasks = remember(taskEntries) { taskEntries.filter(::isActiveTask) }
        val runningMcSessions = activeMcSessions(McPlayStore.sessions)
        var showExitConfirm by remember { mutableStateOf(false) }
        val globalSnackbar = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            if (!Const.NO_UPDATE) {
                UpdateService.updateUpdater(
                    onStatus = { lgr.info { it } },
                    onDetail = { if (it.isNotBlank()) lgr.info { it } }
                ).onSuccess {
                    if (it == UpdaterUpdateResult.UPDATED) {
                        globalSnackbar.showSnackbar(
                            message = "启动程序已更新，下次启动生效",
                            duration = SnackbarDuration.Short
                        )
                    }
                }.onFailure {
                    lgr.warn(it) { "启动程序后台更新失败" }
                }
            }
        }

        fun performExit(terminateActiveWork: Boolean) {
            if (terminateActiveWork) {
                activeTasks.forEach { entry ->
                    ClientTaskManager.cancel(entry.runId, "应用关闭，任务已终止")
                }
                runningMcSessions.forEach { it.requestStop(force = true) }
            }
            exitApplication()
        }

        val requestClose = {
            if (activeTasks.isNotEmpty() || runningMcSessions.isNotEmpty()) {
                showExitConfirm = true
            } else {
                performExit(false)
            }
        }

        Window(
            onCloseRequest = requestClose,
            title = "RDI ${Const.VERSION_NUMBER}",
            icon = windowIcon,
            state = windowState,
            transparent = Const.WINDOW_TRANSPARENT,
            decoration = if(Const.WINDOW_TRANSPARENT) WindowDecoration.Undecorated(resizerThickness = 0.dp) else WindowDecoration.SystemDefault,
            onPreviewKeyEvent = { event ->
                event.type == KeyEventType.KeyDown && event.key == Key.Escape
            }
        ) {
            RTheme {
                AppBackgroundProvider {


                    val navController = rememberNavController()
                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    val settingsOpen = currentBackStackEntry?.destination?.hasRoute<Setting>() == true

                    WindowChrome(
                        windowState = windowState,
                        minimumSize = minimumSize,
                        onCloseRequest = requestClose,
                        onOpenHome = {
                            navController.navigateRoot(if (AccountSessionStore.isLoggedIn) Menu else Login)
                        },
                        onOpenMail = { navController.navigate(Mailbox) { launchSingleTop = true } },
                        onOpenSettings = {
                            if (settingsOpen) {
                                navController.popBackStack()
                            } else {
                                navController.navigate(Setting) { launchSingleTop = true }
                            }
                        },
                        onOpenMcSession = { sessionId ->
                            McPlayStore.selectedSessionId = sessionId
                            McPlayStore.openConsoleOnly = true
                            McPlayStore.onBack = { navController.popBackStack() }
                            navController.navigate(McPlayView) { launchSingleTop = true }
                        },
                        onLogin = { navController.navigateRoot(Login) },
                        onLogout = {
                            LocalCredentials.read().setAutoLoginDisabled(true).onFailure {
                                lgr.error(it) { "保存自动登录设置失败" }
                            }
                            AccountSessionStore.logout()
                            navController.navigateRoot(Login)
                        },
                        onOpenPlayerInfo = {
                            navController.navigate(PlayerInfo) { launchSingleTop = true }
                        },
                        onOpenWardrobe = { navController.navigate(Wardrobe) }
                    ) {
                        val background = LocalAppBackgroundPainter.current
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                painter = background,
                                contentDescription = "rdi5 background",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            AppNavigation(navController, modCatalog)
                            SnackbarHost(
                                hostState = globalSnackbar,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                            )
                        }
                        if (showExitConfirm) {
                            AlertDialog(
                                onDismissRequest = { showExitConfirm = false },
                                title = { Text("仍有任务或MC正在运行") },
                                text = { Text(buildExitConfirmMessage(activeTasks, runningMcSessions)) },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showExitConfirm = false
                                            performExit(true)
                                        }
                                    ) {
                                        Text("全部停止并退出")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showExitConfirm = false }) {
                                        Text("继续等待")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        }
    } finally {
        modCatalog.close()
    }
}

private fun initializeLoggedAccountOnStartup() {
    System.getProperty("rdi.account")?.let {
        loggedAccount = serdesJson.decodeFromString(it.decodeBase64)
    }
    System.getProperty("rdi.jwt")?.let {
        AccountSessionStore.updateJwt(it)
    }
    if (loggedAccount.jwt == null && loggedAccount != RAccount.DEFAULT) {
        GlobalScope.launch {
            val jwt = PlayerService.getJwt(loggedAccount.qq, loggedAccount.pwd)
            AccountSessionStore.updateJwt(jwt)
        }
    }
}

private fun isActiveTask(entry: Task2Entry): Boolean = !entry.status.isTerminal

private fun buildExitConfirmMessage(
    activeTasks: List<Task2Entry>,
    activeMcSessions: List<McGameSession>
): String = buildString {
    if (activeTasks.isNotEmpty()) append("${activeTasks.size}个任务正在运行\n")
    if (activeMcSessions.isNotEmpty()) append("${activeMcSessions.size}个MC正在运行\n")
    append("\n要全部停止并退出，还是继续等待？")
}

private fun clearPackProcDirOnStartup() = GlobalScope.launch {
    withContext(Dispatchers.IO) {
        val packProcDir = ClientDirs.packProcDir
        runCatching {
            packProcDir.deleteRecursivelyNoSymlink()
            packProcDir.mkdirs()
        }
    }
}

private fun clearIncompleteModDownloadsOnStartup() {
    runCatching {
        DL_MOD_DIR.mkdirs()
        DL_MOD_DIR.listFiles()
            ?.filter { it.name.contains(".downloading.") }
            ?.forEach { file ->
                if (file.isDirectory) {
                    file.deleteRecursivelyNoSymlink()
                } else {
                    file.delete()
                }
            }
    }
}
