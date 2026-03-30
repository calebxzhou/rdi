package calebxzhou.rdi.client.ui.screen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import calebxzhou.mykotutils.std.encodeBase64
import calebxzhou.rdi.client.proxy.LocalMcProxy
import calebxzhou.rdi.client.service.GameService
import calebxzhou.rdi.client.service.startDesktop
import calebxzhou.rdi.client.service.syncHostExtraMods
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.MainColumn
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.McPlayStore
import calebxzhou.rdi.client.ui.Space8h
import calebxzhou.rdi.client.ui.Space8w
import calebxzhou.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.comp.Console
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.TaskProgress
import kotlinx.coroutines.launch

/**
 * calebxzhou @ 2026-01-16 20:46
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McPlayScreen(
    title:String,
    mcVer: McVersion,
    versionId: String,
    playArg: String? = null,
    extraMods: List<Mod> = emptyList(),
    manageHostExtraMods: Boolean = false,
    vararg jvmArgs: String,
    onBack: ()-> Unit )
{
    val scope = rememberCoroutineScope()
    val consoleState = McPlayStore.consoleState
    var process by remember { mutableStateOf(McPlayStore.process?.takeIf { it.isAlive }) }
    var preparing by remember { mutableStateOf(false) }
    fun onProcessExit() {
        process = null
        McPlayStore.process = null
    }
    fun startProcess() {
        if (process?.isAlive == true || preparing) return
        consoleState.clear()
        preparing = true
        scope.launch {
            try {
                if (manageHostExtraMods) {
                    consoleState.append("[RDI] 检查地图附加Mod...")
                    syncHostExtraMods(versionId, extraMods) { progress ->
                        scope.launch {
                            appendSyncProgress(consoleState, progress)
                        }
                    }
                    consoleState.append("[RDI] 地图附加Mod已同步")
                }
                val launchJvmArgs = buildList {
                    addAll(jvmArgs.filterNot { it.startsWith("-Drdi.play=") })
                    playArg?.let { rawPlayArg ->
                        val proxiedPlayArg = rawPlayArg.withGameAddr(LocalMcProxy.gameAddr)
                        add("-Drdi.play=${proxiedPlayArg.encodeBase64}")
                    }
                }
                val started = GameService.startDesktop(mcVer, versionId, *launchJvmArgs.toTypedArray()) { line ->
                    scope.launch {
                        consoleState.append(line)
                        if (line.startsWith("启动失败") || line.startsWith("已退出")) {
                            onProcessExit()
                        }
                    }
                }
                process = started
                McPlayStore.process = started
            } catch (e: Exception) {
                consoleState.append("[RDI] 启动前检查失败: ${e.message ?: "unknown"}")
                onProcessExit()
            } finally {
                preparing = false
            }
        }
    }
    LaunchedEffect(versionId, playArg, jvmArgs) {
        if (process?.isAlive != true && !preparing) {
            startProcess()
        }
    }
    MainColumn {
        TitleRow(title,onBack){
            CircleIconButton("\uEAD2","重启MC"){
                process?.destroy()
                startProcess()
            }
            Space8w()
            CircleIconButton("\uF04D","停止MC", bgColor = MaterialColor.ORANGE_900.color){
                process?.destroy()
                onProcessExit()
            }
            Space8w()
            CircleIconButton("\uF05E","强制结束MC", bgColor = MaterialColor.RED_900.color){
                process?.destroyForcibly()
                onProcessExit()
            }
        }
        Space8h()
        Console(consoleState)
    }
}

private fun appendSyncProgress(consoleState: calebxzhou.rdi.client.ui.comp.ConsoleState, progress: TaskProgress) {
    val suffix = progress.fraction
        ?.let { fraction -> " ${(fraction * 100).toInt()}%" }
        .orEmpty()
    consoleState.append("[RDI] ${progress.message}$suffix")
}

private fun String.withGameAddr(gameAddr: String): String {
    val lines = split(Regex("\\r?\\n")).toMutableList()
    require(lines.size >= 2) { "RDI参数错误，请重新启动地图" }
    lines[1] = gameAddr
    return lines.joinToString("\n")
}
