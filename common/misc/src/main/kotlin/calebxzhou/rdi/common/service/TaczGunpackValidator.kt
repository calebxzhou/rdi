package calebxzhou.rdi.common.service

import calebxzhou.rdi.common.util.openChineseZip
import calebxzhou.rdi.common.serdesJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipException

@Serializable
data class TaczGunpackMeta(
    val namespace: String
)

data class TaczGunpackInfo(
    val namespace: String
)

object TaczGunpackValidator {
    private const val META_FILE_NAME = "gunpack.meta.json"

    fun validate(file: File): Result<TaczGunpackInfo> = runCatching {
        require(file.isFile) { "TaCZ枪包文件不存在" }

        file.openChineseZip().use { zip ->
            val entries = zip.entries().asSequence()
                .map { entry -> entry.name.normalizedZipPath() to entry }
                .filter { (path, _) -> path.isNotBlank() && !path.startsWith("__MACOSX/") }
                .toList()

            val metaEntries = entries
                .filter { (path, _) -> path == META_FILE_NAME || path.endsWith("/$META_FILE_NAME") }
                .map { (path, entry) -> path.substringBeforeLast('/', "") to entry }
                .filter { (root, _) -> root.isBlank() || '/' !in root }

            require(metaEntries.isNotEmpty()) { "TaCZ枪包缺少$META_FILE_NAME" }

            val roots = metaEntries.map { it.first }.distinct()
            require(roots.size == 1) { "TaCZ枪包结构不明确" }

            val root = roots.single()
            val rootPrefix = root.takeIf(String::isNotBlank)?.let { "$it/" }.orEmpty()
            val metaEntry = metaEntries.single { it.first == root }.second
            val meta = zip.readGunpackMeta(metaEntry)
            val namespace = meta.namespace.trim()
            require(namespace.isNotBlank()) { "$META_FILE_NAME 缺少namespace" }

            require(entries.any { (path, _) -> path == "${rootPrefix}assets" || path.startsWith("${rootPrefix}assets/") }) {
                "TaCZ枪包缺少assets目录"
            }
            require(entries.any { (path, _) -> path == "${rootPrefix}data" || path.startsWith("${rootPrefix}data/") }) {
                "TaCZ枪包缺少data目录"
            }

            TaczGunpackInfo(namespace)
        }
    }.recoverCatching { error ->
        if (error is ZipException) throw IllegalArgumentException("TaCZ枪包必须是 zip文件")
        throw error
    }

    private fun java.util.zip.ZipFile.readGunpackMeta(entry: ZipEntry): TaczGunpackMeta {
        val content = getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return runCatching { serdesJson.decodeFromString<TaczGunpackMeta>(content) }
            .getOrElse { throw IllegalArgumentException("gunpack.meta.json格式错误") }
    }

    private fun String.normalizedZipPath(): String =
        replace('\\', '/').trimStart('/').trim()
}
