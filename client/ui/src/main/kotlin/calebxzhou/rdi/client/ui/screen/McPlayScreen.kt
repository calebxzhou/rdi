package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import calebxzhou.mykotutils.std.encodeBase64
import calebxzhou.rdi.client.service.GameService
import calebxzhou.rdi.client.service.LocalMcProxyService
import calebxzhou.rdi.client.service.ModpackService
import calebxzhou.rdi.client.service.ensureDesktopLaunchLibraries
import calebxzhou.rdi.client.service.ensureGtnhRuntime
import calebxzhou.rdi.client.service.startDesktop
import calebxzhou.rdi.client.service.syncHostExtraMods
import calebxzhou.rdi.client.service.syncHostManagedBaseMods
import calebxzau.rdi.client.RDIClient
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.McGameSession
import calebxzhou.rdi.client.ui.McPlayArgs
import calebxzhou.rdi.client.ui.McPlayStore
import calebxzau.rdi.client.ui.Space8h
import calebxzau.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.comp.Console
import calebxzhou.rdi.common.model.FORGEGUARD_AGENT_FILE_NAME
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Task2Progress
import calebxzhou.rdi.common.model.supportsForgeguard


@Composable
fun McPlayScreen(
    launchArgs: McPlayArgs? = null,
    autoStart: Boolean = true,
    extraJvmArgs: List<String> = emptyList(),
    onBack: () -> Unit
) {
    val sessions = McPlayStore.sessions
    var duplicateLaunchArgs by remember { mutableStateOf<McPlayArgs?>(null) }
    val screenFocusRequester = remember { FocusRequester() }

    fun stopLocalMcProxy() {
        LocalMcProxyService.stop().onFailure { error ->
            McPlayStore.appendProxyLog("[LocalMcProxy] 停止失败: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun markSessionExited(session: McGameSession, message: String? = null) {
        McPlayStore.markExited(session.id, message)
        if (!McPlayStore.hasAliveSessions()) {
            stopLocalMcProxy()
        }
    }

    fun stopSession(session: McGameSession, force: Boolean = false) {
        session.requestStop(force)
        if (!McPlayStore.hasAliveSessions()) {
            stopLocalMcProxy()
        }
    }

    fun startSession(args: McPlayArgs, allowDuplicateVersion: Boolean = false) {
        if (!allowDuplicateVersion && McPlayStore.aliveCount(args.versionId) > 0) {
            duplicateLaunchArgs = args
            return
        }

        val session = McPlayStore.createSession(args)
        session.appendLog("[RDI] 准备启动 ${args.title}")

        McPlayStore.launchSessionTask {
            try {
                if (args.manageHostBaseMods) {
                    session.appendLog("[RDI] 检查房间基础Mod...")
                    syncHostManagedBaseMods(args.versionId, args.activeBaseMods, args.disabledBaseMods) { progress ->
                        appendSyncProgress(session, progress)
                    }
                    session.appendLog("[RDI] 房间基础Mod已同步")
                }
                if (session.stopRequested) return@launchSessionTask

                if (args.manageHostExtraMods) {
                    session.appendLog("[RDI] 检查房间附加Mod...")
                    syncHostExtraMods(args.versionId, args.extraMods) { progress ->
                        appendSyncProgress(session, progress)
                    }
                    session.appendLog("[RDI] 房间附加Mod已同步")
                }
                if (session.stopRequested) return@launchSessionTask

                session.appendLog("[RDI] 检查RDI核心Mod...")
                ModpackService.installRdiCore(
                    args.mcVer,
                    args.modLoader,
                    GameService.versionListDir.resolve(args.versionId).resolve("mods")
                )
                session.appendLog("[RDI] RDI核心Mod已同步")
                if (session.stopRequested) return@launchSessionTask

                if (args.mcVer == McVersion.V071) {
                    session.appendLog("[RDI] 检查GTNH运行库...")
                    GameService.ensureGtnhRuntime(GameService.versionListDir.resolve(args.versionId)) { progress ->
                        session.appendLog("[RDI] $progress")
                    }.getOrThrow()
                    session.appendLog("[RDI] GTNH运行库已就绪")
                }
                if (session.stopRequested) return@launchSessionTask

                session.appendLog("[RDI] 检查游戏运行库...")
                GameService.ensureDesktopLaunchLibraries(args.mcVer, args.versionId) { progress ->
                    session.appendLog("[RDI] $progress")
                }.getOrThrow()
                session.appendLog("[RDI] 游戏运行库已就绪")
                if (session.stopRequested) return@launchSessionTask

                val launchJvmArgs = buildList {
                    addAll(extraJvmArgs.filterNot { it.startsWith("-Drdi.play=") })
                    if (args.mcVer.supportsForgeguard(args.modLoader)) {
                        val forgeguardAgent = RDIClient.DIR.resolve("lib/$FORGEGUARD_AGENT_FILE_NAME")
                        require(forgeguardAgent.isFile) { "缺少Forgeguard启动保护文件: ${forgeguardAgent.absolutePath}" }
                        add("\"-javaagent:${forgeguardAgent.absolutePath}\"")
                    }
                    val localGameAddr = LocalMcProxyService.start(McPlayStore::appendProxyLog).getOrThrow()
                    val proxiedPlayArg = args.playArg.withGameAddr(localGameAddr)
                    add("-Drdi.play=${proxiedPlayArg.encodeBase64}")
                }
                if (session.stopRequested) return@launchSessionTask

                val started = GameService.startDesktop(
                    args.mcVer,
                    args.versionId,
                    *launchJvmArgs.toTypedArray()
                ) { line ->
                    McPlayStore.launchSessionTask {
                        session.consoleState.append(line)
                        if (line.startsWith("启动失败") || line.startsWith("已退出") || line.startsWith("MC已结束")) {
                            markSessionExited(session, line)
                        }
                    }
                }
                session.process = started
                session.preparing = false
            } catch (e: Exception) {
                session.appendLog("[RDI] 启动前检查失败: ${e.message ?: "unknown"}")
                markSessionExited(session, e.message)
            }
        }
    }

    LaunchedEffect(launchArgs, autoStart) {
        if (autoStart && launchArgs != null) {
            startSession(launchArgs)
        }
    }

    LaunchedEffect(sessions.size) {
        if (McPlayStore.selectedSessionId == null || sessions.none { it.id == McPlayStore.selectedSessionId }) {
            McPlayStore.selectedSessionId = sessions.lastOrNull()?.id
        }
    }

    val selectedSession = McPlayStore.selectedSession()

    LaunchedEffect(Unit) {
        screenFocusRequester.requestFocus()
    }

    MaxBox {
        ScreenContentSurface(
            size = ScreenContentSize.FULL,
            modifier = Modifier
                .focusRequester(screenFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyDown &&
                        event.key == Key.Delete &&
                        selectedSession?.let { McPlayStore.closeStoppedSession(it.id) } == true
                },
            containerAlpha = 1f,
            shadowElevation = 0.dp
        ) {
            TitleRow("MC控制台", onBack) {
                selectedSession?.let { session ->
                    //if(Const.AI_TEST){
                        /*CircleIconButton(
                            icon = "\uE0CA",
                            tooltip = "AI陪玩",
                            bgColor = MaterialColor.PURPLE_700.color
                        ) {
                            onOpenAiChat(session.args.mcpPort, session.args.versionDir)
                        }*/
                    //}
                    CircleIconButton("\uEAD2", "重启MC") {
                        session.requestStop()
                        startSession(session.args, allowDuplicateVersion = true)
                    }
                    CircleIconButton("\uF04D", "终止MC", bgColor = MaterialColor.ORANGE_900.color) {
                        stopSession(session,force = true)
                    }
                }
            }
            ContentBody {
                if (sessions.isEmpty()) {
                    Text("没有可显示的游戏", color = MaterialColor.GRAY_700.color)
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sessions.forEach { session ->
                            McSessionChip(
                                session = session,
                                selected = session.id == selectedSession?.id,
                                onClick = { McPlayStore.selectedSessionId = session.id }
                            )
                        }
                    }
                    Space8h()
                    selectedSession?.let { session ->
                        Console(session.consoleState, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    duplicateLaunchArgs?.let { args ->
        AlertDialog(
            onDismissRequest = { duplicateLaunchArgs = null },
            title = { Text("该整合包已在运行") },
            text = {
                Text("再次启动${args.title}可能造成存档、配置、日志或Mod缓存冲突。是否继续？")
            },
            confirmButton = {
                TextButton(onClick = {
                    duplicateLaunchArgs = null
                    startSession(args, allowDuplicateVersion = true)
                }) {
                    Text("继续启动")
                }
            },
            dismissButton = {
                TextButton(onClick = { duplicateLaunchArgs = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun McSessionChip(
    session: McGameSession,
    selected: Boolean,
    onClick: () -> Unit
) {
    val status = when {
        session.preparing -> "启动中"
        session.isAlive() -> "运行中"
        else -> "已结束"
    }
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(session.title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Text(status, color = sessionStatusColor(session), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun sessionStatusColor(session: McGameSession) = when {
    session.preparing -> MaterialColor.BLUE_700.color
    session.isAlive() -> MaterialColor.GREEN_800.color
    else -> MaterialColor.GRAY_700.color
}

private fun appendSyncProgress(session: McGameSession, progress: Task2Progress) {
    val suffix = progress.fraction
        ?.let { fraction -> " ${(fraction * 100).toInt()}%" }
        .orEmpty()
    session.appendLog("[RDI] ${progress.message}$suffix")
}

private fun String.withGameAddr(gameAddr: String): String {
    val lines = split(Regex("\\r?\\n")).toMutableList()
    require(lines.size >= 2) { "RDI参数错误，请重新启动房间" }
    lines[1] = gameAddr
    return lines.joinToString("\n")
}
