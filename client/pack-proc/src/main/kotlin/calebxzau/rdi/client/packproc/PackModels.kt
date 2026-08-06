package calebxzau.rdi.client.packproc

import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModLoader
import java.io.File

enum class LocalModpackSourceType {
    MODRINTH,
    CURSEFORGE
}

data class ServerExtraFile(
    val sourceFile: File,
    val relativePath: String
)

data class LoadedLocalModpack(
    val sourceType: LocalModpackSourceType,
    val sourceDir: File,
    val packName: String,
    val packVersion: String,
    val mcVersion: McVersion,
    val modloader: ModLoader,
    val mods: List<Mod>,
    val embeddedModOriginalFileNames: Map<String, String> = emptyMap(),
    val serverExtraFiles: List<ServerExtraFile> = emptyList()
)

data class UploadPayload(
    val sourceType: LocalModpackSourceType,
    val sourceDir: File,
    var mods: MutableList<Mod>,
    val mcVersion: McVersion,
    val modloader: ModLoader,
    val sourceName: String,
    val sourceVersion: String,
    var embeddedModOriginalFileNames: Map<String, String> = emptyMap(),
    val serverExtraFiles: List<ServerExtraFile> = emptyList()
)

data class LoadedServerPackResult(
    val mods: List<Mod>,
    val serverExtraFiles: List<ServerExtraFile>
)

data class PackProcessingPaths(
    val workDir: File,
    val modCacheDir: File
)

fun LoadedLocalModpack.toUploadPayload(): UploadPayload = UploadPayload(
    sourceType = sourceType,
    sourceDir = sourceDir,
    mods = mods.toMutableList(),
    mcVersion = mcVersion,
    modloader = modloader,
    sourceName = packName,
    sourceVersion = packVersion,
    embeddedModOriginalFileNames = embeddedModOriginalFileNames,
    serverExtraFiles = serverExtraFiles
)

fun UploadPayload.toLoadedLocalModpack(): LoadedLocalModpack = LoadedLocalModpack(
    sourceType = sourceType,
    sourceDir = sourceDir,
    packName = sourceName,
    packVersion = sourceVersion,
    mcVersion = mcVersion,
    modloader = modloader,
    mods = mods.toList(),
    embeddedModOriginalFileNames = embeddedModOriginalFileNames,
    serverExtraFiles = serverExtraFiles
)
