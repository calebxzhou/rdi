package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.model.ModrinthProjectInfoVo
import calebxzhou.rdi.client.model.ModrinthProjectVersionVo
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2Progress
import calebxzhou.rdi.common.service.ModService

object RemoteModDownloadService {
    fun toMod(project: ModrinthProjectInfoVo, version: ModrinthProjectVersionVo): Mod {
        val file = version.primaryFile ?: error("此版本没有可下载文件")
        val sha1 = file.sha1?.trim()?.takeIf(String::isNotBlank) ?: error("此版本缺少SHA1")
        return Mod(
            platform = "mr",
            projectId = project.projectId,
            slug = project.slug,
            fileId = version.id,
            hash = sha1,
            side = version.environment.toModSide(),
            downloadUrls = listOf(file.url)
        )
    }

    fun downloadToLocalModpackTask2(
        mod: Mod,
        packdir: ModpackLocalDir
    ): Task2 = Task2.Sequence(
        title = "安装${mod.slug}到${packdir.vo.name}",
        children = listOf(
            ModService.downloadModsTask2(listOf(mod)),
            Task2.Leaf("链接${mod.slug}到本地整合包") { ctx ->
                val source = DL_MOD_DIR.resolve(mod.fileName)
                require(source.isFile) { "Mod文件不存在: ${source.absolutePath}" }
                val modsDir = packdir.dir.resolve("mods").also { it.mkdirs() }
                val target = modsDir.resolve(mod.fileName)
                linkOrCopyMod(source, target)
                ctx.emit(Task2Progress("已安装到${packdir.vo.name}", 1f))
            }
        )
    )
}

private fun String?.toModSide(): Mod.Side =
    when (this?.lowercase()) {
        "client_only" -> Mod.Side.CLIENT
        "server_only" -> Mod.Side.SERVER
        else -> Mod.Side.BOTH
    }
