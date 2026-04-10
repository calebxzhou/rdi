package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.TaskContext
import calebxzhou.rdi.common.model.TaskProgress
import java.io.File

/**
 * Android actual for installer bootstrapper expect functions.
 * On Android, the installer bootstrapper is not run — FCL handles this.
 */
internal actual fun GameService.runInstallerBootstrapperDesktop(
    holder: GameService.LoaderInstallHolder,
    ctx: TaskContext
) {
    ctx.emitProgress(TaskProgress("Android不运行客户端安装器 (由FCL处理)", 1f))
}

internal actual fun GameService.runServerInstallerBootstrapperDesktop(
    holder: GameService.LoaderInstallHolder,
    ctx: TaskContext
) {
    ctx.emitProgress(TaskProgress("Android不运行服务端安装器", 1f))
}

internal actual class ServerTestProcessHandle {
    actual fun isAlive(): Boolean = false

    actual fun destroy() = Unit

    actual fun destroyForcibly() = Unit

    actual suspend fun waitFor(): Int = 0
}

internal actual fun GameService.startServerTestProcess(
    mcVer: McVersion,
    loaderVer: ModLoader.Version,
    workDir: File,
    onLine: (String) -> Unit
): ServerTestProcessHandle {
    throw UnsupportedOperationException("当前平台暂不支持服务端测试")
}
