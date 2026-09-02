package calebxzau.rdi.mcinstall

import calebxzhou.rdi.common.net.LocalArtifactReuse
import calebxzhou.rdi.common.net.LocalArtifactReuser
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 把发现服务记录到的Minecraft安装根目录当作下载复制源。
 * 只读 [installations] 流的最新值，不持有/关闭数据库句柄；挂载到全局 [LocalArtifactReuse]，
 * 让McInstall下载运行库、资源和客户端核心时先尝试从本地安装复制（sha1+size校验）。
 */
class McInstallationReuse(
    private val installations: StateFlow<List<Path>>
) : AutoCloseable {
    private val started = AtomicBoolean()
    private val closed = AtomicBoolean()
    private val fileReuser = LocalMcArtifactFileReuser(
        sourceProvider = {
            LocalArtifactSources(
                runtimeRoots = installations.value.map { it.toAbsolutePath().normalize() }
            )
        }
    )

    fun start() {
        if (!started.compareAndSet(false, true)) return
        LocalArtifactReuse.install(fileReuser)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        LocalArtifactReuse.install(LocalArtifactReuser { _, _ -> Result.success(null) })
    }
}
