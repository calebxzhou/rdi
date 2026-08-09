package calebxzhou.rdi.master.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2CancelledException
import calebxzhou.rdi.common.service.ModService
import calebxzhou.rdi.common.service.runInline
import java.io.File
import kotlinx.coroutines.CancellationException

internal class ClientModCacheService(
    private val targetDir: File,
    private val downloader: (List<Mod>, File) -> Task2 = ModService::downloadModsTask2
) {
    private val lgr by Loggers

    fun downloadTask(mods: List<Mod>): Task2 = Task2.Group(
        title = "缓存客户端Mod",
        children = mods
            .filter { it.side == Mod.Side.CLIENT }
            .map { mod ->
                Task2.Leaf("缓存 ${mod.slug}") { ctx ->
                    try {
                        require(mod.platform in supportedPlatforms) { "不支持的Mod平台: ${mod.platform}" }
                        require(mod.platform == "cf" || mod.downloadUrls.isNotEmpty()) {
                            "Mod缺少下载地址: ${mod.slug}"
                        }
                        downloader(listOf(mod), targetDir).runInline(ctx)
                    } catch (error: Exception) {
                        if (error is CancellationException || error is Task2CancelledException) throw error
                        lgr.warn(error) { "客户端Mod加速缓存失败: ${mod.slug}" }
                    }
                }
            }
    )

    private companion object {
        val supportedPlatforms = setOf("cf", "mr", "github")
    }
}
