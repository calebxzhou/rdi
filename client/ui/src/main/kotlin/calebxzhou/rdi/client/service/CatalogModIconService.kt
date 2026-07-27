package calebxzhou.rdi.client.service

import calebxzau.rdi.client.lgr
import calebxzhou.rdi.client.modcatalog.CatalogMod
import calebxzhou.rdi.client.modcatalog.ModPlatform
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.model.toModFileSlugAlias
import calebxzhou.rdi.common.service.ModService.modLogo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.jar.JarFile

internal suspend fun CatalogMod.loadLocalIcon(): Result<ByteArray?> = withContext(Dispatchers.IO) {
    runCatching {
        val jarFiles = DL_MOD_DIR.listFiles { file ->
            file.isFile && file.extension.equals("jar", ignoreCase = true)
        }.orEmpty()
        localFilePrefixes().firstNotNullOfOrNull { prefix ->
            jarFiles
                .asSequence()
                .filter { it.matchesLocalModFile(prefix) }
                .sortedByDescending { it.lastModified() }
                .firstNotNullOfOrNull { file ->
                    file.readModIcon().getOrElse { cause ->
                        lgr.warn(cause) { "读取本地Mod图标失败: ${file.absolutePath}" }
                        null
                    }
                }
        }
    }
}

private fun CatalogMod.localFilePrefixes(): List<String> {
    val primary = sources.first { it.ref == primaryRef }
    return (listOf(primary) + sources.filterNot { it.ref == primaryRef })
        .flatMap { source ->
            listOf(source.slug.toModFileSlugAlias(), source.slug.trim())
                .filter(String::isNotBlank)
                .distinct()
                .map { slug -> "${slug}_${source.ref.platform.fileTag}_" }
        }
        .distinct()
}

private val ModPlatform.fileTag: String
    get() = when (this) {
        ModPlatform.CURSEFORGE -> "cf"
        ModPlatform.MODRINTH -> "mr"
    }

private fun File.matchesLocalModFile(prefix: String): Boolean {
    if (!name.startsWith(prefix, ignoreCase = true)) return false
    val hash = nameWithoutExtension.substring(prefix.length)
    return hash.isNotBlank() && hash.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
}

private fun File.readModIcon(): Result<ByteArray?> = runCatching {
    JarFile(this).use { it.modLogo }
}
