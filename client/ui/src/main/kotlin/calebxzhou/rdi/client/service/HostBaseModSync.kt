package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2Context
import calebxzhou.rdi.common.model.Task2Progress
import calebxzhou.rdi.common.service.ModService
import calebxzhou.rdi.common.service.runInline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

fun buildHostBaseModSyncTask2(
    versionId: String,
    activeBaseMods: List<Mod>,
    disabledBaseMods: List<Mod>
): Task2 {
    val serverOnlyBaseMods = activeBaseMods.filter { it.side == Mod.Side.SERVER }
    val unknownSideBaseMods = activeBaseMods.filter { it.side == Mod.Side.UNKNOWN }
    val distinctDisabledMods = (disabledBaseMods + serverOnlyBaseMods + unknownSideBaseMods).distinctBy { it.fileName }
    val distinctActiveMods = activeBaseMods
        .filter { it.side != Mod.Side.SERVER && it.side != Mod.Side.UNKNOWN }
        .distinctBy { it.fileName }

    return Task2.Sequence(
        title = "同步房间基础Mod",
        children = listOf(
            Task2.Leaf("整理基础Mod状态") { ctx ->
                removeDisabledBaseMods(versionId, distinctDisabledMods, ctx)
            },
            Task2.Leaf("检查基础Mod下载") { ctx ->
                val missingMods = distinctActiveMods.filterNot(ModService::isDownloadedModFileValid)
                if (missingMods.isEmpty()) {
                    ctx.emit(Task2Progress("基础Mod已下载", 1f))
                    return@Leaf
                }
                ctx.emit(Task2Progress("开始下载缺失基础Mod ${missingMods.size}个"))
                ModService.downloadModsTask2(missingMods).runInline(ctx)
            },
            Task2.Leaf("同步基础Mod链接") { ctx ->
                syncHostBaseModLinks(versionId, distinctActiveMods, ctx)
            }
        )
    )
}

suspend fun syncHostManagedBaseMods(
    versionId: String,
    activeBaseMods: List<Mod>,
    disabledBaseMods: List<Mod>,
    onProgress: (Task2Progress) -> Unit = {}
) = withContext(Dispatchers.IO) {
    buildHostBaseModSyncTask2(versionId, activeBaseMods, disabledBaseMods).runInline(
        Task2Context(emitProgress = onProgress)
    )
}

private fun removeDisabledBaseMods(
    versionId: String,
    disabledBaseMods: List<Mod>,
    ctx: Task2Context
) {
    val versionDir = GameService.versionListDir.resolve(versionId)
    require(versionDir.exists()) { "未找到整合包目录: ${versionDir.absolutePath}" }
    val modsDir = versionDir.resolve("mods").apply { mkdirs() }

    disabledBaseMods.forEach { mod ->
        mod.fileNames.forEach { fileName ->
            val target = modsDir.resolve(fileName)
            if (target.exists()) {
                Files.deleteIfExists(target.toPath())
                ctx.emit(Task2Progress("移除已禁用基础Mod ${target.name}"))
            }
        }
    }
}

private fun syncHostBaseModLinks(
    versionId: String,
    activeBaseMods: List<Mod>,
    ctx: Task2Context
) {
    val versionDir = GameService.versionListDir.resolve(versionId)
    require(versionDir.exists()) { "未找到整合包目录: ${versionDir.absolutePath}" }
    val modsDir = versionDir.resolve("mods").apply { mkdirs() }

    if (activeBaseMods.isEmpty()) {
        ctx.emit(Task2Progress("没有基础Mod需要同步", 1f))
        return
    }

    activeBaseMods.forEachIndexed { index, mod ->
        val source = mod.candidateFiles.firstOrNull(File::exists)
            ?: error("缺少基础Mod文件: ${mod.targetFile.absolutePath}")
        val target = modsDir.resolve(mod.fileName)
        if (!target.pointsTo(source)) {
            linkOrCopyMod(source, target)
        }
        val fraction = (index + 1).toFloat() / activeBaseMods.size.coerceAtLeast(1)
        ctx.emit(Task2Progress("已同步基础Mod ${index + 1}/${activeBaseMods.size}", fraction))
    }
}
