package calebxzhou.rdi.common.archive

import calebxzhou.rdi.common.util.openChineseZip
import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption

enum class PackArchiveFormat {
    ZIP,
    TAR_ZST
}

data class ArchiveEntryMeta(
    val path: String,
    val isDirectory: Boolean,
    val size: Long?,
    val time: Long
)

data class ArchiveEntryData(
    val path: String,
    val isDirectory: Boolean,
    val size: Long?,
    val time: Long,
    val bytes: ByteArray?
)

data class StreamingTarEntry(
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val time: Long,
    val isSymbolicLink: Boolean,
    val isHardLink: Boolean,
    val isSpecial: Boolean,
    val isSparse: Boolean,
)

private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
private val ZSTD_MAGIC = byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte())

fun detectArchiveFormat(bytes: ByteArray, fallbackName: String? = null): PackArchiveFormat {
    if (bytes.size >= 4) {
        val head = bytes.copyOfRange(0, 4)
        if (head.contentEquals(ZIP_MAGIC)) return PackArchiveFormat.ZIP
        if (head.contentEquals(ZSTD_MAGIC)) return PackArchiveFormat.TAR_ZST
    }
    return when {
        fallbackName?.lowercase()?.endsWith(".tar.zst") == true -> PackArchiveFormat.TAR_ZST
        else -> PackArchiveFormat.ZIP
    }
}

fun File.detectArchiveFormat(): PackArchiveFormat {
    inputStream().use { input ->
        val header = ByteArray(4)
        val read = input.read(header)
        return detectArchiveFormat(
            bytes = if (read <= 0) byteArrayOf() else header.copyOf(read),
            fallbackName = name
        )
    }
}

fun listArchiveEntries(file: File): List<ArchiveEntryMeta> {
    val entries = mutableListOf<ArchiveEntryMeta>()
    when (file.detectArchiveFormat()) {
        PackArchiveFormat.ZIP -> file.openChineseZip().use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val path = entry.name.replace('\\', '/').trimStart('/')
                entries += ArchiveEntryMeta(
                    path = path,
                    isDirectory = entry.isDirectory || path.endsWith('/'),
                    size = entry.size.takeIf { it >= 0L },
                    time = entry.time
                )
            }
        }

        PackArchiveFormat.TAR_ZST -> openTarZstInput(file).use { input ->
            while (true) {
                val entry = input.nextTarEntry ?: break
                entries += ArchiveEntryMeta(
                    path = entry.name.replace('\\', '/').trimStart('/'),
                    isDirectory = entry.isDirectory,
                    size = entry.size.takeIf { it >= 0L },
                    time = entry.modTime.time
                )
            }
        }
    }
    return entries
}

fun forEachArchiveEntry(file: File, onEntry: (ArchiveEntryData) -> Unit) {
    when (file.detectArchiveFormat()) {
        PackArchiveFormat.ZIP -> file.openChineseZip().use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val path = entry.name.replace('\\', '/').trimStart('/')
                if (path.isBlank()) return@forEach
                val isDirectory = entry.isDirectory || path.endsWith('/')
                val bytes = if (isDirectory) null else zip.getInputStream(entry).use(InputStream::readBytes)
                onEntry(ArchiveEntryData(path, isDirectory, entry.size.takeIf { it >= 0L }, entry.time, bytes))
            }
        }

        PackArchiveFormat.TAR_ZST -> openTarZstInput(file).use { input ->
            while (true) {
                val entry = input.nextTarEntry ?: break
                val path = entry.name.replace('\\', '/').trimStart('/')
                if (path.isBlank()) continue
                val bytes = if (entry.isDirectory) null else input.readBytes()
                onEntry(
                    ArchiveEntryData(
                        path = path,
                        isDirectory = entry.isDirectory,
                        size = entry.size.takeIf { it >= 0L },
                        time = entry.modTime.time,
                        bytes = bytes
                    )
                )
            }
        }
    }
}

/**
 * Streams regular and special TAR.Zstandard entries without buffering their
 * contents. The callback must consume the supplied stream when it needs the
 * entry bytes; any remaining bytes are discarded before the next entry.
 */
fun forEachTarZstEntryStreaming(
    file: File,
    onEntry: (entry: StreamingTarEntry, input: InputStream) -> Unit,
) {
    openTarZstInput(file).use { input ->
        while (true) {
            val entry = input.nextTarEntry ?: break
            onEntry(
                StreamingTarEntry(
                    path = entry.name,
                    isDirectory = entry.isDirectory,
                    size = entry.size,
                    time = entry.modTime.time,
                    isSymbolicLink = entry.isSymbolicLink,
                    isHardLink = entry.isLink,
                    isSpecial = entry.isCharacterDevice || entry.isBlockDevice || entry.isFIFO,
                    isSparse = entry.isSparse,
                ),
                input,
            )
            while (input.read() != -1) {
                // Drain an entry that the callback intentionally skipped.
            }
        }
    }
}

/** Reads only the first TAR entry; closing the stream aborts before payload data is read. */
fun readFirstTarZstEntry(
    file: File,
    onEntry: (entry: StreamingTarEntry, input: InputStream) -> Unit,
): Boolean {
    openTarZstInput(file).use { input ->
        val entry = input.nextTarEntry ?: return false
        onEntry(
            StreamingTarEntry(
                path = entry.name,
                isDirectory = entry.isDirectory,
                size = entry.size,
                time = entry.modTime.time,
                isSymbolicLink = entry.isSymbolicLink,
                isHardLink = entry.isLink,
                isSpecial = entry.isCharacterDevice || entry.isBlockDevice || entry.isFIFO,
                isSparse = entry.isSparse,
            ),
            input,
        )
        return true
    }
}

fun extractArchiveToDir(
    archiveFile: File,
    targetDir: File,
    pathTransform: (String) -> String? = { it },
    onProgress: (doneFiles: Int, totalFiles: Int, currentPath: String) -> Unit = { _, _, _ -> }
) {
    val fileEntries = listArchiveEntries(archiveFile)
        .mapNotNull { entry ->
            val transformedPath = pathTransform(entry.path)?.replace('\\', '/')?.trimStart('/')?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            entry.copy(path = transformedPath)
        }
        .filterNot { it.isDirectory }
    val totalFiles = fileEntries.size.coerceAtLeast(1)
    var doneFiles = 0
    val targetPath = targetDir.toPath()
    fun normalizePath(path: String): String? =
        pathTransform(path)?.replace('\\', '/')?.trimStart('/')?.takeIf { it.isNotBlank() }

    fun extractEntry(
        path: String,
        isDirectory: Boolean,
        copyPayload: (output: java.io.OutputStream) -> Unit,
    ) {
        val transformedPath = normalizePath(path) ?: return
        val resolved = targetPath.resolve(transformedPath).normalize()
        if (!resolved.startsWith(targetPath)) {
            throw IllegalArgumentException("非法文件路径: $transformedPath")
        }
        if (isDirectory) {
            Files.createDirectories(resolved)
            return
        }
        resolved.parent?.let { Files.createDirectories(it) }
        Files.newOutputStream(
            resolved,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        ).use { output ->
            copyPayload(output)
        }
        doneFiles++
        onProgress(doneFiles, totalFiles, transformedPath)
    }

    when (archiveFile.detectArchiveFormat()) {
        PackArchiveFormat.ZIP -> archiveFile.openChineseZip().use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val path = entry.name.replace('\\', '/').trimStart('/')
                val isDirectory = entry.isDirectory || path.endsWith('/')
                extractEntry(path, isDirectory) { output ->
                    zip.getInputStream(entry).use { input -> input.copyTo(output) }
                }
            }
        }

        PackArchiveFormat.TAR_ZST -> forEachTarZstEntryStreaming(archiveFile) { entry, input ->
            val path = entry.path.replace('\\', '/').trimStart('/')
            extractEntry(path, entry.isDirectory) { output -> input.copyTo(output) }
        }
    }
}

class TarZstArchiveWriter(target: File) : Closeable {
    private val output = TarArchiveOutputStream(
        ZstdOutputStream(
            Files.newOutputStream(
                target.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            ),
            9
        )
    ).apply {
        setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
        setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
    }

    fun addDirectory(path: String, time: Long = System.currentTimeMillis()) {
        val normalized = path.replace('\\', '/').trim('/').ifBlank { return }
        val entry = TarArchiveEntry("$normalized/").apply {
            modTime = java.util.Date(time)
            mode = 0b111101101
        }
        output.putArchiveEntry(entry)
        output.closeArchiveEntry()
    }

    fun addFile(path: String, bytes: ByteArray, time: Long = System.currentTimeMillis()) {
        val normalized = path.replace('\\', '/').trim('/').ifBlank { return }
        val entry = TarArchiveEntry(normalized).apply {
            size = bytes.size.toLong()
            modTime = java.util.Date(time)
            mode = 0b110100100
        }
        output.putArchiveEntry(entry)
        output.write(bytes)
        output.closeArchiveEntry()
    }

    fun addFile(path: String, source: File) {
        val normalized = path.replace('\\', '/').trim('/').ifBlank { return }
        val entry = TarArchiveEntry(normalized).apply {
            size = source.length()
            modTime = java.util.Date(source.lastModified())
            mode = 0b110100100
        }
        output.putArchiveEntry(entry)
        source.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
        }
        output.closeArchiveEntry()
    }

    fun addFileStreaming(
        path: String,
        input: InputStream,
        size: Long,
        time: Long = System.currentTimeMillis(),
        beforeChunk: () -> Unit = {},
    ) {
        require(size >= 0) { "Archive entry size must not be negative" }
        val normalized = path.replace('\\', '/').trim('/').ifBlank { return }
        val entry = TarArchiveEntry(normalized).apply {
            this.size = size
            modTime = java.util.Date(time)
            mode = 0b110100100
        }
        output.putArchiveEntry(entry)
        var complete = false
        try {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var remaining = size
            while (remaining > 0) {
                beforeChunk()
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (count < 0) throw java.io.EOFException("Archive entry ended before its declared size")
                output.write(buffer, 0, count)
                remaining -= count
            }
            complete = true
        } finally {
            if (complete) output.closeArchiveEntry()
        }
    }

    override fun close() {
        var finishFailure: Throwable? = null
        try {
            output.finish()
        } catch (cause: Throwable) {
            finishFailure = cause
            throw cause
        } finally {
            try {
                output.close()
            } catch (closeFailure: Throwable) {
                if (finishFailure != null) {
                    finishFailure!!.addSuppressed(closeFailure)
                } else {
                    throw closeFailure
                }
            }
        }
    }
}

private fun openTarZstInput(file: File): TarArchiveInputStream =
    TarArchiveInputStream(ZstdInputStream(file.inputStream()))
