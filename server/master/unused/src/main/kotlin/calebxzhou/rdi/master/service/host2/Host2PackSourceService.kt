package calebxzhou.rdi.master.service.host2

import calebxzau.rdi.common.model.ContentOrigin
import calebxzau.rdi.common.model.ContentVo
import calebxzau.rdi.common.model.Modpack2Loader
import calebxzau.rdi.common.model.Modpack2VersionStatus
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Host2PackInfo
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.PackSource
import calebxzhou.rdi.master.infra.postgres.DatabaseProvider
import calebxzau.rdi.server.modpack.Modpack2Repo
import calebxzau.rdi.server.modpack.Modpack2VersionStorage
import java.io.File

data class ResolvedHost2PackSource(
    val source: PackSource,
    val packInfo: Host2PackInfo,
    val sourceArchive: File,
    val clientArchive: File,
    val contents: List<ContentVo>,
    val requireMetadata: Boolean,
)

class Host2PackSourceService(
    private val database: DatabaseProvider,
    private val modpack2Repository: Modpack2Repo,
    private val modpack2Storage: Modpack2VersionStorage,
) {
    suspend fun resolveForCreate(source: PackSource): ResolvedHost2PackSource =
        resolve(source).requireArchives()

    suspend fun resolveExact(source: PackSource): Result<ResolvedHost2PackSource> = runCatching {
        resolve(source)
    }

    private suspend fun resolve(source: PackSource): ResolvedHost2PackSource =
        resolveModpack2(PackSource.Modpack2(source.versionId))

    private suspend fun resolveModpack2(source: PackSource.Modpack2): ResolvedHost2PackSource {
        val resolved = database.transaction {
            val owner = modpack2Repository.findVersionWithModpack(source.versionId)
                ?: throw RequestError("无此整合包版本")
            if (owner.version.status != Modpack2VersionStatus.Ok) {
                throw RequestError("整合包版本尚未准备完成")
            }
            Triple(
                owner,
                modpack2Repository.listContents(source.versionId),
                modpack2Repository.findVersionManifest(source.versionId) != null,
            )
        }
        val (owner, contents, requireMetadata) = resolved
        val mcVersion = McVersion.fromMinor(owner.modpack.mc.toString())
            ?: throw RequestError("整合包MC版本不受支持")
        val modLoader = owner.modpack.loader.toModLoader()
        validateHost2Version(mcVersion, modLoader)
        val paths = modpack2Storage.paths(owner.modpack.id, owner.version.id)
        return ResolvedHost2PackSource(
            source = source,
            packInfo = Host2PackInfo(
                name = owner.modpack.name,
                versionName = owner.version.name,
                mcVersion = mcVersion,
                modLoader = modLoader,
                modpackId = owner.modpack.id,
            ),
            sourceArchive = paths.sourceArchive.toFile(),
            clientArchive = paths.clientArchive.toFile(),
            contents = contents.map { content ->
                ContentVo(
                    origin = ContentOrigin.Pack,
                    platform = content.platform,
                    type = content.type,
                    projectId = content.projectId,
                    fileId = content.fileId,
                    slug = content.slug,
                    hash = content.hash,
                    targetPath = content.targetPath,
                    side = content.side,
                    required = content.required,
                    fileSize = content.fileSize,
                    enabled = content.required,
                )
            },
            requireMetadata = requireMetadata,
        )
    }

}

private fun ResolvedHost2PackSource.requireArchives(): ResolvedHost2PackSource {
    if (!sourceArchive.isFile || !clientArchive.isFile) throw RequestError("整合包归档暂时不可用")
    return this
}

private fun Modpack2Loader.toModLoader(): ModLoader = when (this) {
    Modpack2Loader.Forge -> ModLoader.forge
    Modpack2Loader.NeoForge -> ModLoader.neoforge
}

private fun validateHost2Version(mcVersion: McVersion, modLoader: ModLoader) {
    val supported = mcVersion == McVersion.V201 && modLoader == ModLoader.forge ||
        mcVersion == McVersion.V211 && modLoader == ModLoader.neoforge
    if (!supported) throw RequestError("Host2仅支持MC1.20.1Forge和MC1.21.1NeoForge")
}
