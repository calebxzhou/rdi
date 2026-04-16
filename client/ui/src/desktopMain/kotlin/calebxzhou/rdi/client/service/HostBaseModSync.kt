package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Task2Progress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

suspend fun syncHostManagedBaseMods(
    versionId: String,
    activeBaseMods: List<Mod>,
    disabledBaseMods: List<Mod>,
    onProgress: (Task2Progress) -> Unit = {}
) = withContext(Dispatchers.IO) {
    val versionDir = GameService.versionListDir.resolve(versionId)
    require(versionDir.exists()) { "未找到整合包目录: ${versionDir.absolutePath}" }
    val modsDir = versionDir.resolve("mods").apply { mkdirs() }
    val distinctDisabledMods = disabledBaseMods.distinctBy { it.fileName }
    val distinctActiveMods = activeBaseMods.distinctBy { it.fileName }

    distinctDisabledMods
        .forEach { mod ->
            val target = modsDir.resolve(mod.fileName)
            if (target.exists()) {
                Files.deleteIfExists(target.toPath())
                onProgress(Task2Progress("移除已禁用基础Mod ${target.name}"))
            }
        }

    if (distinctActiveMods.isEmpty()) {
        onProgress(Task2Progress("没有基础Mod需要同步", 1f))
        return@withContext
    }

    distinctActiveMods
        .forEachIndexed { index, mod ->
            val source = DL_MOD_DIR.resolve(mod.fileName)
            require(source.exists()) { "缺少基础Mod文件: ${source.absolutePath}" }
            val target = modsDir.resolve(mod.fileName)
            if (!target.pointsTo(source)) {
                linkOrCopyMod(source, target)
            }
            val fraction = (index + 1).toFloat() / distinctActiveMods.size.coerceAtLeast(1)
            onProgress(Task2Progress("已同步基础Mod ${index + 1}/${distinctActiveMods.size}", fraction))
        }
}
