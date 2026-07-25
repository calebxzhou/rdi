package calebxzhou.rdi.client.ui.screen

import calebxzhou.mykotutils.std.sha1
import calebxzhou.rdi.client.model.UiMod
import calebxzhou.rdi.client.model.toUiMod
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModsTomlConfig
import calebxzhou.rdi.common.model.ModrinthProject
import calebxzhou.rdi.common.service.CurseForgeService.loadInfoCurseForge
import calebxzhou.rdi.common.service.ModService.readNeoForgeConfig
import calebxzhou.rdi.common.service.ModrinthService
import calebxzhou.rdi.common.service.ModrinthService.mapModrinthVersions
import calebxzhou.rdi.common.service.ModrinthService.toCardVo
import calebxzhou.rdi.client.ui.pickAwtOpenFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.maven.artifact.versioning.DefaultArtifactVersion
import org.apache.maven.artifact.versioning.VersionRange
import java.io.File
import java.util.jar.JarFile

fun selectHostExtraModFiles(): List<File>? {
    return pickAwtOpenFiles(
        title = "选择额外Mod JAR",
        filenameFilter = { _, name -> name.endsWith(".jar", ignoreCase = true) }
    )
        ?.filter { it.extension.equals("jar", ignoreCase = true) }
        ?.distinctBy { it.absolutePath }
        ?.takeIf { it.isNotEmpty() }
}

fun selectHostTaczFiles(): List<File>? {
    return pickAwtOpenFiles(
        title = "选择TaCZ枪包ZIP",
        filenameFilter = { _, name -> name.endsWith(".zip", ignoreCase = true) }
    )
        ?.filter { it.isFile && it.extension.equals("zip", ignoreCase = true) }
        ?.distinctBy { it.absolutePath }
        ?.takeIf { it.isNotEmpty() }
}

suspend fun matchHostExtraModFiles(
    files: List<File>,
    hostMcVersion: McVersion,
    onProgress: (String) -> Unit
): HostExtraModMatchResult = withContext(Dispatchers.IO) {
    val inputFiles = files
        .filter { it.exists() && it.isFile && it.extension.equals("jar", ignoreCase = true) }
        .distinctBy { it.absolutePath }
    if (inputFiles.isEmpty()) return@withContext HostExtraModMatchResult(emptyList(), emptyList())

    reportHostExtraModProgress(onProgress, "先检查Mod支持的MC版本")
    val versionCheck = validateHostExtraModsForMcVersion(inputFiles, hostMcVersion)
    val compatibleFiles = versionCheck.compatibleFiles
    if (compatibleFiles.isEmpty()) {
        return@withContext HostExtraModMatchResult(
            matchedMods = emptyList(),
            rejectedFiles = versionCheck.rejectedFiles
        )
    }

    reportHostExtraModProgress(onProgress, "正在读取mod信息，请稍等一分钟")
    val mrResult = matchHostExtraModsMR(compatibleFiles)
    val remaining = compatibleFiles.filterNot { it in mrResult.removeFiles }

    reportHostExtraModProgress(onProgress, "在CurseForge搜索mod信息中，请稍等1分钟")
    val cfResult = matchHostExtraModsCF(remaining)
    val selectedMods = selectLatestMatchedMods(mrResult.mods + cfResult.mods)

    HostExtraModMatchResult(
        matchedMods = selectedMods.map(UiMod::toMod),
        rejectedFiles = versionCheck.rejectedFiles + cfResult.rejectedFiles
    )
}

private suspend fun reportHostExtraModProgress(
    onProgress: (String) -> Unit,
    text: String
) = withContext(Dispatchers.Main) {
    onProgress(text)
}

private data class SelectedLocalMod(
    val mod: UiMod,
    val sourceFile: File,
    val configVersion: String?
)

private data class LocalMatchResult(
    val mods: List<SelectedLocalMod>,
    val removeFiles: Set<File>,
    val rejectedFiles: List<String> = emptyList()
)

private suspend fun matchHostExtraModsMR(files: List<File>): LocalMatchResult {
    if (files.isEmpty()) return LocalMatchResult(emptyList(), emptySet())
    val hashToVersion = files.mapModrinthVersions()
    if (hashToVersion.isEmpty()) return LocalMatchResult(emptyList(), emptySet())
    val projectMap = ModrinthService.getMultipleProjects(hashToVersion.values.map { it.projectId }.distinct())
        .associateBy { it.id }

    val matched = mutableListOf<SelectedLocalMod>()
    val removeFiles = mutableSetOf<File>()
    files.forEach { file ->
        val sha1 = file.sha1
        val version = hashToVersion[sha1] ?: return@forEach
        val project = projectMap[version.projectId]
        val side = project?.toHostExtraModSide() ?: Mod.Side.BOTH
        val slug = project?.slug?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension.ifBlank { version.projectId }
        val downloadUrls = version.files.map { it.url }.filter { it.isNotBlank() }
        val rawMod = Mod(
            platform = "mr",
            projectId = version.projectId,
            slug = slug,
            fileId = version.id,
            hash = sha1,
            side = side,
            downloadUrls = downloadUrls
        )
        val mod = UiMod(
            mod = rawMod,
            card = project?.toCardVo(file)?.copy(side = side),
            file = file
        )
        matched += SelectedLocalMod(
            mod = mod,
            sourceFile = file,
            configVersion = readModsTomlVersion(file)
        )
        removeFiles += file
    }
    return LocalMatchResult(matched, removeFiles)
}

private suspend fun matchHostExtraModsCF(files: List<File>): LocalMatchResult {
    if (files.isEmpty()) return LocalMatchResult(emptyList(), emptySet())
    val result = files.loadInfoCurseForge()
    val removeFiles = result.matched.mapNotNull { it.file }.toSet()
    return LocalMatchResult(
        mods = result.matched.mapNotNull { mod ->
            val sourceFile = mod.file ?: return@mapNotNull null
            SelectedLocalMod(
                mod = mod.toUiMod(),
                sourceFile = sourceFile,
                configVersion = readModsTomlVersion(sourceFile)
            )
        },
        removeFiles = removeFiles,
        rejectedFiles = result.unmatched.map { "${it.name}: 没有在Modrinth或CurseForge上找到对应信息" }
    )
}

private fun ModrinthProject.toHostExtraModSide(): Mod.Side {
    if (serverSide == "unsupported") return Mod.Side.CLIENT
    if (clientSide == "unsupported") return Mod.Side.SERVER
    return Mod.Side.BOTH
}

private data class McVersionValidationResult(
    val compatibleFiles: List<File>,
    val rejectedFiles: List<String>
)

private fun validateHostExtraModsForMcVersion(
    files: List<File>,
    hostMcVersion: McVersion
): McVersionValidationResult {
    val hostVersion = DefaultArtifactVersion(hostMcVersion.mcVer)
    val compatibleFiles = mutableListOf<File>()
    val rejectedFiles = mutableListOf<String>()

    files.forEach { file ->
        val validationError = runCatching {
            JarFile(file).use { jar ->
                val config = jar.readNeoForgeConfig() ?: return@use null
                val invalidRanges = findInvalidMinecraftVersionRanges(config, hostVersion)
                if (invalidRanges.isEmpty()) {
                    null
                } else {
                    "${file.name}: 需要MC版本${invalidRanges.joinToString(" 或 ")}，当前整合包是${hostMcVersion.mcVer}"
                }
            }
        }.getOrElse { err ->
            "${file.name}: 读取mods.toml失败，无法确认支持的MC版本${err.message?.let { "($it)" } ?: ""}"
        }

        if (validationError == null) {
            compatibleFiles += file
        } else {
            rejectedFiles += validationError
        }
    }

    return McVersionValidationResult(
        compatibleFiles = compatibleFiles,
        rejectedFiles = rejectedFiles
    )
}

private fun findInvalidMinecraftVersionRanges(
    config: ModsTomlConfig,
    hostVersion: DefaultArtifactVersion
): List<String> {
    val invalidRanges = mutableListOf<String>()
    config.mods.forEach { modEntry ->
        val modId = modEntry.modId.trim()
        if (modId.isEmpty()) return@forEach
        val minecraftDependencies = config.dependencies.entries
            .firstOrNull { (key, _) -> key.equals(modId, ignoreCase = true) }
            ?.value
            .orEmpty()
            .filter { it.modId.equals("minecraft", ignoreCase = true) }

        minecraftDependencies.forEach { dependency ->
            val rangeText = dependency.versionRange?.trim().orEmpty()
            if (rangeText.isEmpty()) return@forEach
            val range = runCatching { VersionRange.createFromVersionSpec(rangeText) }.getOrNull()
                ?: return listOf(rangeText)
            if (!range.containsVersion(hostVersion)) {
                invalidRanges += rangeText
            }
        }
    }
    return invalidRanges.distinct()
}

private fun readModsTomlVersion(file: File): String? {
    return runCatching {
        JarFile(file).use { jar ->
            jar.readNeoForgeConfig()
                ?.mods
                ?.asSequence()
                ?.mapNotNull { it.version?.trim() }
                ?.firstOrNull { it.isNotEmpty() }
        }
    }.getOrNull()
}

private fun selectLatestMatchedMods(selectedMods: List<SelectedLocalMod>): List<UiMod> {
    if (selectedMods.isEmpty()) return emptyList()
    return selectedMods
        .groupBy { "${it.mod.platform}:${it.mod.projectId}" }
        .values
        .map { sameProjectMods ->
            sameProjectMods.maxWithOrNull(
                compareBy<SelectedLocalMod> { parseComparableModVersion(it.configVersion) }
                    .thenBy { it.sourceFile.name }
            )!!
        }
        .map { selected ->
            selected.mod.withFile(null)
        }
}

private fun parseComparableModVersion(version: String?): DefaultArtifactVersion {
    val normalized = version
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.contains('$') }
        ?: "0"
    return DefaultArtifactVersion(normalized)
}
