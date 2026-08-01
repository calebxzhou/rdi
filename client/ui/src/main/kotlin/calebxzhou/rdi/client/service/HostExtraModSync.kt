package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.model.EXTRA_MOD_PREFIX
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
import java.nio.file.LinkOption

fun buildHostExtraModSyncTask2(versionId: String, extraMods: List<Mod>): Task2 {
    val distinctMods = extraMods.distinctBy { it.fileName }
    return Task2.Sequence(
        title = "同步房间附加Mod",
        children = buildList {
            if (distinctMods.isNotEmpty()) {
                add(ModService.downloadModsTask2(distinctMods))
            }
            add(
                Task2.Leaf("整理附加Mod链接") { ctx ->
                    syncHostExtraModLinks(versionId, distinctMods, ctx)
                }
            )
        }
    )
}

suspend fun syncHostExtraMods(
    versionId: String,
    extraMods: List<Mod>,
    onProgress: (Task2Progress) -> Unit = {}
) = withContext(Dispatchers.IO) {
    buildHostExtraModSyncTask2(versionId, extraMods).runInline(
        Task2Context(emitProgress = onProgress)
    )
}

private fun syncHostExtraModLinks(
    versionId: String,
    extraMods: List<Mod>,
    ctx: Task2Context
) {
    val versionDir = GameService.versionListDir.resolve(versionId)
    require(versionDir.exists()) { "未找到整合包目录: ${versionDir.absolutePath}" }
    val modsDir = versionDir.resolve("mods").apply { mkdirs() }
    val expectedTargets = extraMods
        .associateBy(::extraModTargetFileName)

    modsDir.listFiles()
        ?.filter { it.name.startsWith(EXTRA_MOD_PREFIX) }
        ?.forEach { file ->
            if (file.name !in expectedTargets) {
                Files.deleteIfExists(file.toPath())
                ctx.emit(Task2Progress("移除旧附加Mod ${file.name}"))
            }
        }

    if (extraMods.isEmpty()) {
        ctx.emit(Task2Progress("没有附加Mod需要同步", 1f))
        return
    }

    extraMods.forEachIndexed { index, mod ->
        val source = mod.candidateFiles.firstOrNull(File::exists)
            ?: error("缺少附加Mod文件: ${mod.targetFile.absolutePath}")
        val target = modsDir.resolve(extraModTargetFileName(mod))
        if (!target.pointsTo(source)) {
            hardLinkFile(source, target).getOrThrow()
        }
        val fraction = (index + 1).toFloat() / extraMods.size
        ctx.emit(Task2Progress("已同步附加Mod ${index + 1}/${extraMods.size}", fraction))
    }
}

private fun extraModTargetFileName(mod: Mod): String = EXTRA_MOD_PREFIX + mod.fileName

internal fun File.pointsTo(source: File): Boolean {
    val targetPath = toPath()
    if (!Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) return false
    return runCatching {
        if (Files.isSymbolicLink(targetPath)) {
            val rawLink = Files.readSymbolicLink(targetPath)
            val resolvedLink = if (rawLink.isAbsolute) rawLink.normalize() else parentFile.toPath().resolve(rawLink).normalize()
            resolvedLink == source.toPath().normalize()
        } else {
            Files.isSameFile(targetPath, source.toPath())
        }
    }.getOrDefault(false)
}
