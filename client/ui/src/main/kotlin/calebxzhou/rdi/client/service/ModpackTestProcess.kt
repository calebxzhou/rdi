package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal class ServerTestProcessHandle(
    private val process: Process
) {
    fun isAlive(): Boolean = process.isAlive

    fun destroy() {
        process.destroy()
    }

    fun destroyForcibly() {
        process.destroyForcibly()
    }

    suspend fun waitFor(): Int = withContext(Dispatchers.IO) {
        process.waitFor()
    }
}

internal class ClientTestProcessHandle(
    private val process: Process
) {
    fun isAlive(): Boolean = process.isAlive

    fun destroy() {
        process.destroy()
    }

    fun destroyForcibly() {
        process.destroyForcibly()
    }

    suspend fun waitFor(): Int = withContext(Dispatchers.IO) {
        process.waitFor()
    }
}

internal fun GameService.startServerTestProcess(
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

internal fun GameService.startClientTestProcess(
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
