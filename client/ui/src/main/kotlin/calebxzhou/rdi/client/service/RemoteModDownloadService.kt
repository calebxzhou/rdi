package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.model.ModrinthProjectInfoVo
import calebxzhou.rdi.client.model.ModrinthProjectVersionVo
import calebxzhou.rdi.client.model.RemoteModSource
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2Progress
import calebxzhou.rdi.common.service.ModService

object RemoteModDownloadService {
    fun toMod(project: ModrinthProjectInfoVo, version: ModrinthProjectVersionVo): Mod {
        val file = version.primaryFile ?: error("此版本没有可下载文件")
        return when (project.source) {
            RemoteModSource.MODRINTH -> {
                val sha1 = file.sha1?.trim()?.takeIf(String::isNotBlank) ?: error("此版本缺少SHA1")
                Mod(
                    platform = "mr",
                    projectId = project.projectId,
                    slug = project.slug,
                    fileId = version.id,
                    hash = sha1,
                    side = version.environment.toModSide(),
                    downloadUrls = listOf(file.url)
                )
            }

            RemoteModSource.CURSEFORGE -> {
                val murmur2 = file.murmur2?.trim()?.takeIf(String::isNotBlank) ?: error("此版本缺少CurseForge指纹")
                Mod(
                    platform = "cf",
                    projectId = project.projectId,
                    slug = project.slug,
                    fileId = file.fileId ?: version.id,
                    hash = murmur2,
                    side = Mod.Side.BOTH,
                    downloadUrls = listOf(file.url)
                )
            }
        }
    }

    fun downloadToLocalModpackTask2(
        mod: Mod,
        packdir: ModpackLocalDir
    ): Task2 = Task2.Sequence(
        title = "安装${mod.slug}到${packdir.vo.name}",
        children = listOf(
            ModService.downloadModsTask2(listOf(mod)),
            Task2.Leaf("链接${mod.slug}到本地整合包") { ctx ->
                val source = mod.candidateFiles.firstOrNull { it.isFile }
                    ?: error("Mod文件不存在: ${mod.targetFile.absolutePath}")
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
