package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.model.EXTRA_MOD_PREFIX
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Task
import calebxzhou.rdi.common.model.TaskContext
import calebxzhou.rdi.common.model.TaskProgress
import calebxzhou.rdi.common.model.execute
import calebxzhou.rdi.common.service.ModService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

fun buildHostExtraModSyncTask(versionId: String, extraMods: List<Mod>): Task {
    val distinctMods = extraMods.distinctBy { it.fileName }
    return Task.Sequence(
        name = "同步地图附加Mod",
        subTasks = buildList {
            if (distinctMods.isNotEmpty()) {
                add(ModService.downloadModsTask(distinctMods))
            }
            add(
                Task.Leaf("整理附加Mod链接") { ctx ->
                    syncHostExtraModLinks(versionId, distinctMods, ctx)
                }
            )
        }
    )
}

suspend fun syncHostExtraMods(
    versionId: String,
    extraMods: List<Mod>,
    onProgress: (TaskProgress) -> Unit = {}
) = withContext(Dispatchers.IO) {
    buildHostExtraModSyncTask(versionId, extraMods).execute(
        TaskContext(emitProgress = onProgress)
    )
}

private fun syncHostExtraModLinks(
    versionId: String,
    extraMods: List<Mod>,
    ctx: TaskContext
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
                ctx.emitProgress(TaskProgress("移除旧附加Mod ${file.name}"))
            }
        }

    if (extraMods.isEmpty()) {
        ctx.emitProgress(TaskProgress("没有附加Mod需要同步", 1f))
        return
    }

    extraMods.forEachIndexed { index, mod ->
        val source = DL_MOD_DIR.resolve(mod.fileName)
        require(source.exists()) { "缺少附加Mod文件: ${source.absolutePath}" }
        val target = modsDir.resolve(extraModTargetFileName(mod))
        if (!target.pointsTo(source)) {
            linkOrCopyMod(source, target)
        }
        val fraction = (index + 1).toFloat() / extraMods.size
        ctx.emitProgress(TaskProgress("已同步附加Mod ${index + 1}/${extraMods.size}", fraction))
    }
}

private fun extraModTargetFileName(mod: Mod): String = EXTRA_MOD_PREFIX + mod.fileName

private fun File.pointsTo(source: File): Boolean {
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
