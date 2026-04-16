package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal actual class ServerTestProcessHandle(
    private val process: Process
) {
    actual fun isAlive(): Boolean = process.isAlive

    actual fun destroy() {
        process.destroy()
    }

    actual fun destroyForcibly() {
        process.destroyForcibly()
    }

    actual suspend fun waitFor(): Int = withContext(Dispatchers.IO) {
        process.waitFor()
    }
}

internal actual class ClientTestProcessHandle(
    private val process: Process
) {
    actual fun isAlive(): Boolean = process.isAlive

    actual fun destroy() {
        process.destroy()
    }

    actual fun destroyForcibly() {
        process.destroyForcibly()
    }

    actual suspend fun waitFor(): Int = withContext(Dispatchers.IO) {
        process.waitFor()
    }
}

internal actual fun GameService.startServerTestProcess(
    mcVer: McVersion,
    loaderVer: ModLoader.Version,
    workDir: File,
    onLine: (String) -> Unit
): ServerTestProcessHandle = ServerTestProcessHandle(
    startServerDesktop(
        mcVer = mcVer,
        loaderVer = loaderVer,
        workDir = workDir,
        onLine = onLine
    )
)

internal actual fun GameService.startClientTestProcess(
    mcVer: McVersion,
    versionId: String,
    versionDir: File,
    onLine: (String) -> Unit
): ClientTestProcessHandle = ClientTestProcessHandle(
    startDesktopInDir(
        mcVer = mcVer,
        versionId = versionId,
        versionDir = versionDir,
        onLine = onLine
    )
)
