package calebxzhou.rdi.client

import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import calebxzhou.mykotutils.std.decodeBase64
import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.mykotutils.std.jarResource
import calebxzhou.rdi.CONF
import calebxzhou.rdi.client.net.lgr
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.proxy.LocalMcProxy
import calebxzhou.rdi.client.service.ClientDirs
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.service.PlayerService
import calebxzhou.rdi.client.service.refreshNodeSettings
import calebxzhou.rdi.client.service.warmUpHwSpecCache
import calebxzhou.rdi.client.ui.AppNavigation
import calebxzhou.rdi.client.ui.AppTypography
import calebxzhou.rdi.client.ui.screen.*
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.model.GeoLocation
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.model.Task2Entry
import calebxzhou.rdi.common.serdesJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit

val VERTICAL_MODE= System.getProperty("rdi.ui.vertical").toBoolean()
lateinit var ScreenSize: Pair<Dp, Dp>
fun main() {
    clearIncompleteModDownloadsOnStartup()
    clearPackProcDirOnStartup()
    GlobalScope.launch(Dispatchers.IO) {
        warmUpHwSpecCache()
    }
    GlobalScope.launch(Dispatchers.IO) {
        refreshNodeSettings()
    }
    initializeLoggedAccountOnStartup()
    LocalMcProxy.start(::println)
    application {
        if(DEBUG){
            System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT")
        }
        val windowIcon = remember {
            jarResource("icon.png").use { stream ->
                BitmapPainter(stream.readAllBytes().decodeToImageBitmap())
            }
        }

        // 设置窗口初始大小为屏幕的2/3，并居中显示
        val screen = remember { Toolkit.getDefaultToolkit().screenSize }
        ScreenSize = (screen.width * 2 / 3).dp to  (screen.height * 2 / 3).dp
        val windowState = if(VERTICAL_MODE) rememberWindowState(
            width = (screen.width*1/5).dp ,
            height = ScreenSize.second,
            position = WindowPosition(Alignment.Center)
        )  else rememberWindowState(
            width = ScreenSize.first,
            height = ScreenSize.second,
            position = WindowPosition(Alignment.Center)
        )
        val taskEntries by ClientTaskManager.entries.collectAsState()
        val activeTasks = remember(taskEntries) { taskEntries.filter(::isActiveTask) }
        var showExitConfirm by remember { mutableStateOf(false) }

        fun performExit(terminateTasks: Boolean) {
            if (terminateTasks) {
                activeTasks.forEach { entry ->
                    ClientTaskManager.cancel(entry.runId, "应用关闭，任务已终止")
                }
            }
            LocalMcProxy.stop()
            exitApplication()
        }
        Window(
            onCloseRequest = {
                if (activeTasks.isNotEmpty()) {
                    showExitConfirm = true
                } else {
                    performExit(false)
                }
            },
            title = "RDI ${Const.VERSION_NUMBER}",
            icon = windowIcon,
            state = windowState
        ) {
            MaterialTheme(typography = AppTypography) {
                val initScreenName = System.getProperty("rdi.init.screen")?.trim()
                val startDestination: Any = when (initScreenName) {
                    "wd" -> Wardrobe
                    "mail" -> HostRoute(HostTab.Mail.name)
                    "hl" -> HostRoute(HostTab.MyHosts.name)
                    "wl" -> HostRoute(HostTab.Worlds.name)
                    else -> Login
                }
                AppNavigation(startDestination = startDestination)
                if (showExitConfirm) {
                    AlertDialog(
                        onDismissRequest = { showExitConfirm = false },
                        title = { Text("仍有任务正在执行") },
                        text = {
                            Text(
                                buildExitConfirmMessage(activeTasks)
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showExitConfirm = false
                                    performExit(true)
                                }
                            ) {
                                Text("结束任务并退出")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showExitConfirm = false }
                            ) {
                                Text("继续等待")
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun initializeLoggedAccountOnStartup() {
    System.getProperty("rdi.account")?.let {
        loggedAccount = serdesJson.decodeFromString(it.decodeBase64)
    }
    System.getProperty("rdi.jwt")?.let {
        loggedAccount.jwt = it
    }
    if (loggedAccount.jwt == null && loggedAccount != RAccount.DEFAULT) {
        GlobalScope.launch {
            val jwt = PlayerService.getJwt(loggedAccount.qq, loggedAccount.pwd)
            loggedAccount.jwt = jwt
        }
    }
}

private fun isActiveTask(entry: Task2Entry): Boolean = !entry.status.isTerminal

private fun buildExitConfirmMessage(activeTasks: List<Task2Entry>): String {
    val titles = activeTasks.take(3).joinToString("\n") { "• ${it.task.title}" }
    val moreText = if (activeTasks.size > 3) "\n等${activeTasks.size}个任务" else ""
    return if (titles.isBlank()) {
        "${activeTasks.size}个任务运行中。\n要立刻终止任务并退出，还是等待任务完成？"
    } else {
        "${activeTasks.size}个任务运行中：\n$titles$moreText\n\n要立刻终止任务并退出，还是等待任务完成？"
    }
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
