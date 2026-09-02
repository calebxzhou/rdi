package calebxzau.rdi.server.modpack

import calebxzau.rdi.common.model.Modpack2InheritedRawFile
import calebxzau.rdi.common.model.Modpack2RawFile
import calebxzau.rdi.common.model.Modpack2RawFileRoot
import calebxzau.rdi.common.model.Modpack2ManifestFormat
import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

/** A validation or streaming-build failure in a Modpack2 source archive. */
class Modpack2ArchiveException(message: String, cause: Throwable? = null) : IOException(message, cause)

enum class Modpack2MergeRoot {
    CLIENT,
    SERVER
}

data class Modpack2SourceArchiveInfo(
    val entryCount: Int,
    val uncompressedBytes: Long,
    val hasServerRoot: Boolean
)

data class Modpack2ArchiveBuildResult(
    val source: Modpack2SourceArchiveInfo,
    val clientArchiveBytes: Long,
    val serverArchiveBytes: Long?
)

data class Modpack2ArchiveManifest(
    val format: Modpack2ManifestFormat,
    val json: String,
)

private const val METADATA_ROOT = "metadata"

val SERVER_PACK_MEDIA_EXTENSIONS: Set<String> =
    setOf("psd", "png", "jpg", "jpeg", "webp", "mp4", "ogg", "wav")

/**
 * Safe, streaming I/O for the Modpack2 source archive.
 *
 * The source archive is a tar.zst with exactly the root directories client/ and
 * shared/ (both may be empty) and an optional server/ directory. The generated
 * archives contain paths relative to their merged root.
 */
object Modpack2ArchiveIO {
    const val MAX_COMPRESSED_BYTES: Long = 2L * 1024 * 1024 * 1024
    const val MAX_UNCOMPRESSED_BYTES: Long = 8L * 1024 * 1024 * 1024
    const val MAX_ENTRIES: Int = 100_000

    val SERVER_PACK_MEDIA_EXTENSIONS: Set<String> =
        calebxzau.rdi.server.modpack.SERVER_PACK_MEDIA_EXTENSIONS

    /** Validates the complete source stream without materializing its files. */
    fun validateSourceArchive(
        sourceArchive: java.io.File,
        requireMetadata: Boolean = true,
    ): Modpack2SourceArchiveInfo = readSource(
        sourceArchive,
        stageDirectory = null,
        requireMetadata = requireMetadata,
    ).info

    /**
     * Reads the exact raw-file manifest represented by a source archive. The
     * manifest is derived from extracted bytes so PostgreSQL never becomes a
     * second, unverified source of file hashes or sizes.
     */
    fun rawFileManifest(sourceArchive: java.io.File): List<Modpack2RawFile> {
        val staging = Files.createTempDirectory(".modpack2-manifest-")
        return try {
            val source = readSource(sourceArchive, staging)
            rawFileManifest(staging, source.info.hasServerRoot)
        } finally {
            deleteTreeNoSymlink(staging)
        }
    }

    /** Reads the supported installer manifest retained in the uploaded source snapshot. */
    fun readManifest(sourceArchive: java.io.File): Modpack2ArchiveManifest {
        val staging = Files.createTempDirectory(".modpack2-manifest-")
        return try {
            val source = readSource(sourceArchive, staging)
            val candidates = listOf(
                Modpack2ManifestFormat.CurseForge to "manifest.json",
                Modpack2ManifestFormat.Modrinth to "modrinth.index.json",
            ).flatMap { (format, name) ->
                listOf(METADATA_ROOT).flatMap { rootName ->
                    val root = staging.resolve(rootName)
                    val path = root.resolve(name)
                    if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) listOf(format to path) else emptyList()
                }
            }
            if (candidates.size != 1) {
                throw Modpack2ArchiveException("source归档必须包含且只能包含一个受支持的manifest")
            }
            val (format, path) = candidates.single()
            val bytes = Files.readAllBytes(path)
            if (bytes.isEmpty() || bytes.size > Modpack2ManifestValidator.MAX_MANIFEST_BYTES) {
                throw Modpack2ArchiveException("manifest大小无效")
            }
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            Modpack2ArchiveManifest(format, decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString())
        } catch (error: Modpack2ArchiveException) {
            throw error
        } catch (error: Throwable) {
            throw Modpack2ArchiveException("读取上传manifest失败", error)
        } finally {
            deleteTreeNoSymlink(staging)
        }
    }

    /**
     * Materializes a Generated append into an independent source snapshot.
     * Only explicitly declared base server paths are copied, and each path is
     * checked against the base bytes' SHA-1 before it is admitted.
     */
    fun materializeGeneratedAppend(
        sourceArchive: java.io.File,
        baseSourceArchive: java.io.File,
        inheritedServerFiles: List<Modpack2InheritedRawFile>,
        targetArchive: java.io.File,
    ): Modpack2SourceArchiveInfo {
        val target = targetArchive.toPath().toAbsolutePath().normalize()
        ensureDestinationParent(target)
        rejectExistingDestination(target)
        val sourceStage = Files.createTempDirectory(".modpack2-source-")
        val baseStage = Files.createTempDirectory(".modpack2-base-")
        val combined = Files.createTempDirectory(".modpack2-combined-")
        try {
            val source = readSource(sourceArchive, sourceStage)
            val base = readSource(baseSourceArchive, baseStage)
            if (!base.info.hasServerRoot) {
                throw Modpack2ArchiveException("基础版本没有可继承的server目录")
            }
            ROOTS.filter { it != SERVER_ROOT || source.info.hasServerRoot || inheritedServerFiles.isNotEmpty() }
                .forEach { ensureDirectoryPath(combined.resolve(it)) }
            ensureDirectoryPath(combined.resolve(METADATA_ROOT))
            copyDirectoryTree(sourceStage.resolve(CLIENT_ROOT), combined.resolve(CLIENT_ROOT))
            copyDirectoryTree(sourceStage.resolve(SHARED_ROOT), combined.resolve(SHARED_ROOT))
            copyDirectoryTree(sourceStage.resolve(METADATA_ROOT), combined.resolve(METADATA_ROOT))
            if (source.info.hasServerRoot) {
                copyDirectoryTree(sourceStage.resolve(SERVER_ROOT), combined.resolve(SERVER_ROOT))
            }

            val baseServerRoot = baseStage.resolve(SERVER_ROOT)
            val targetServerRoot = combined.resolve(SERVER_ROOT)
            inheritedServerFiles.forEach { declaration ->
                val relative = normalizeRelativePath(declaration.path)
                val sourceFile = baseServerRoot.resolve(relative).normalize()
                if (!sourceFile.startsWith(baseServerRoot) ||
                    !Files.isRegularFile(sourceFile, LinkOption.NOFOLLOW_LINKS) ||
                    Files.isSymbolicLink(sourceFile)
                ) {
                    throw Modpack2ArchiveException("基础版本缺少server文件: ${declaration.path}")
                }
                val actualSha1 = sha1File(sourceFile)
                if (!actualSha1.equals(declaration.sha1, ignoreCase = true)) {
                    throw Modpack2ArchiveException("基础版本server文件已变化: ${declaration.path}")
                }
                val destination = targetServerRoot.resolve(relative).normalize()
                if (!destination.startsWith(targetServerRoot)) {
                    throw Modpack2ArchiveException("继承server文件路径无效: ${declaration.path}")
                }
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS) ||
                        !sha1File(destination).equals(actualSha1, ignoreCase = true)
                    ) {
                        throw Modpack2ArchiveException("上传source与继承server文件冲突: ${declaration.path}")
                    }
                } else {
                    ensureDirectoryPath(destination.parent)
                    Files.copy(sourceFile, destination, StandardCopyOption.COPY_ATTRIBUTES)
                }
            }
            val includeServer = source.info.hasServerRoot || inheritedServerFiles.isNotEmpty()
            writeSourceArchive(combined, includeServer, target)
            return validateSourceArchive(target.toFile())
        } finally {
            deleteTreeNoSymlink(sourceStage)
            deleteTreeNoSymlink(baseStage)
            deleteTreeNoSymlink(combined)
        }
    }

    /**
     * Validates and builds the client archive and, when requested, the server
     * archive. Existing destination files are never replaced.
     */
    fun buildMergedArchives(
        sourceArchive: java.io.File,
        clientArchive: java.io.File,
        serverArchive: java.io.File? = null,
        requireMetadata: Boolean = true,
    ): Modpack2ArchiveBuildResult {
        val clientPath = clientArchive.toPath().toAbsolutePath().normalize()
        val serverPath = serverArchive?.toPath()?.toAbsolutePath()?.normalize()
        if (serverPath != null && clientPath == serverPath) {
            throw Modpack2ArchiveException("client和server归档目标不能相同")
        }
        ensureDestinationParent(clientPath)
        if (serverPath != null) ensureDestinationParent(serverPath)
        rejectExistingDestination(clientPath)
        if (serverPath != null) rejectExistingDestination(serverPath)

        val staging = Files.createTempDirectory(clientPath.parent, ".modpack2-archive-")
        var clientCreated = false
        var serverCreated = false
        try {
            val source = readSource(sourceArchive, staging, requireMetadata = requireMetadata)
            val clientBytes = writeMergedArchive(
                staging = staging,
                roots = source.roots,
                side = Modpack2MergeRoot.CLIENT,
                target = clientPath,
                filterServerMedia = false
            )
            clientCreated = true
            val serverBytes = if (serverPath == null) {
                null
            } else {
                writeMergedArchive(
                    staging = staging,
                    roots = source.roots,
                    side = Modpack2MergeRoot.SERVER,
                    target = serverPath,
                    filterServerMedia = true
                ).also { serverCreated = true }
            }
            return Modpack2ArchiveBuildResult(source.info, clientBytes, serverBytes)
        } catch (error: Throwable) {
            if (clientCreated) deleteTreeNoSymlink(clientPath)
            if (serverCreated) deleteTreeNoSymlink(serverPath)
            throw error
        } finally {
            deleteTreeNoSymlink(staging)
        }
    }

    /**
     * Validates the source and merges shared/side into an existing or new
     * writable directory. Source files override shared files; file/directory
     * conflicts are rejected. Server media filtering is applied only for the
     * SERVER side.
     */
    fun mergeToDirectory(
        sourceArchive: java.io.File,
        targetDirectory: java.io.File,
        side: Modpack2MergeRoot,
        requireMetadata: Boolean = true,
    ): Modpack2SourceArchiveInfo {
        val target = targetDirectory.toPath().toAbsolutePath().normalize()
        ensureDirectoryPath(target)
        val staging = Files.createTempDirectory(target, ".modpack2-merge-")
        try {
            val source = readSource(sourceArchive, staging, requireMetadata = requireMetadata)
            copyMergedTree(
                staging = staging,
                roots = source.roots,
                side = side,
                target = target,
                filterServerMedia = side == Modpack2MergeRoot.SERVER
            )
            return source.info
        } finally {
            deleteTreeNoSymlink(staging)
        }
    }

    private fun readSource(
        sourceArchive: java.io.File,
        stageDirectory: Path?,
        requireMetadata: Boolean = true,
    ): SourceReadResult {
        val sourcePath = sourceArchive.toPath().toAbsolutePath().normalize()
        validateSourceFile(sourcePath)
        if (compressedSize(sourcePath) > MAX_COMPRESSED_BYTES) {
            throw Modpack2ArchiveException("source.tar.zst压缩后超过2GiB")
        }

        stageDirectory?.let {
            ensureDirectoryPath(it)
            ALL_ROOTS.forEach { root -> ensureDirectoryPath(it.resolve(root)) }
        }

        val roots = ALL_ROOTS.associateWith { RootEntries() }.toMutableMap()
        var entryCount = 0
        var uncompressedBytes = 0L
        try {
            ensureZstdMagic(sourcePath)
            Files.newInputStream(sourcePath, LinkOption.NOFOLLOW_LINKS).buffered().use { raw ->
                TarArchiveInputStream(ZstdInputStream(raw)).use { input ->
                    while (true) {
                        val entry = input.nextTarEntry ?: break
                        entryCount++
                        if (entryCount > MAX_ENTRIES) {
                            throw Modpack2ArchiveException("归档条目数量超过${MAX_ENTRIES}")
                        }

                        val parsed = parseEntry(entry)
                        val rootEntries = roots[parsed.root]
                            ?: throw Modpack2ArchiveException("归档根目录无效")
                        val relativePath = parsed.relativePath
                        if (!rootEntries.seenPaths.add(relativePath)) {
                            throw Modpack2ArchiveException("同一根目录存在重复路径: ${parsed.originalPath}")
                        }
                        rootEntries.addPath(relativePath, parsed.kind)

                        if (parsed.kind == EntryKind.FILE) {
                            val size = entry.size
                            if (size < 0) {
                                throw Modpack2ArchiveException("文件条目缺少确定大小: ${parsed.originalPath}")
                            }
                            uncompressedBytes = checkedAdd(uncompressedBytes, size)
                            if (uncompressedBytes > MAX_UNCOMPRESSED_BYTES) {
                                throw Modpack2ArchiveException("归档解压后超过8GiB")
                            }
                        }

                        val destination = stageDirectory?.resolve(parsed.root)?.resolve(relativePath)
                        if (parsed.kind == EntryKind.DIRECTORY) {
                            if (entry.size != 0L) {
                                throw Modpack2ArchiveException("目录条目包含文件内容: ${parsed.originalPath}")
                            }
                            if (destination != null) ensureDirectoryPath(destination)
                            drainEntry(input, 0, DiscardOutputStream)
                        } else {
                            if (destination == null) {
                                drainEntry(input, entry.size, DiscardOutputStream)
                            } else {
                                ensureDirectoryPath(destination.parent ?: stageDirectory)
                                createFileFromEntry(input, destination, entry.size)
                            }
                        }
                    }
                }
            }
        } catch (error: Modpack2ArchiveException) {
            throw error
        } catch (error: Throwable) {
            throw Modpack2ArchiveException("读取source.tar.zst失败", error)
        }

        if (compressedSize(sourcePath) > MAX_COMPRESSED_BYTES) {
            throw Modpack2ArchiveException("source.tar.zst压缩后超过2GiB")
        }
        ROOTS.filter { it != SERVER_ROOT }.forEach { root ->
            if (roots.getValue(root).seenPaths.none { it.isEmpty() }) {
                throw Modpack2ArchiveException("source.tar.zst缺少${root}/目录")
            }
        }
        if (requireMetadata) validateMetadataRoot(roots.getValue(METADATA_ROOT))
        validateMergedPaths(roots, Modpack2MergeRoot.CLIENT)
        if (roots.getValue(SERVER_ROOT).seenPaths.any { it.isEmpty() }) {
            validateMergedPaths(roots, Modpack2MergeRoot.SERVER)
        }
        val info = Modpack2SourceArchiveInfo(
            entryCount = entryCount,
            uncompressedBytes = uncompressedBytes,
            hasServerRoot = roots.getValue(SERVER_ROOT).seenPaths.any { it.isEmpty() }
        )
        return SourceReadResult(info, roots)
    }

    private fun rawFileManifest(staging: Path, hasServerRoot: Boolean): List<Modpack2RawFile> {
        val roots = ROOTS.filter { it != SERVER_ROOT || hasServerRoot }
        return buildList {
            roots.forEach { rootName ->
                val root = staging.resolve(rootName)
                Files.walk(root).use { paths ->
                    paths.filter { path ->
                        Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                            !Files.isSymbolicLink(path)
                    }.forEach { path ->
                        val relative = root.relativize(path).toString().replace('\\', '/')
                        add(
                            Modpack2RawFile(
                                root = rootName.toRawFileRoot(),
                                path = relative,
                                sha1 = sha1File(path),
                                size = Files.size(path),
                            )
                        )
                    }
                }
            }
        }.sortedWith(compareBy<Modpack2RawFile>({ it.root.name }, { it.path }))
    }

    private fun validateMetadataRoot(metadata: RootEntries) {
        val paths = metadata.paths
        if ("" !in metadata.seenPaths) {
            throw Modpack2ArchiveException("source.tar.zst缺少metadata/目录")
        }
        val files = paths.filterValues { it == EntryKind.FILE }.keys
        if (files.size != 1 || files.singleOrNull() !in setOf("manifest.json", "modrinth.index.json") ||
            paths.keys.any { it != "" && it !in files }
        ) {
            throw Modpack2ArchiveException("metadata/只能包含一个manifest文件")
        }
    }

    private fun writeSourceArchive(staging: Path, includeServer: Boolean, target: Path) {
        val roots = ROOTS.filter { it != SERVER_ROOT || includeServer } + METADATA_ROOT
        val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp")
        rejectExistingDestination(temporary)
        try {
            CountingOutputStream(
                Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                MAX_COMPRESSED_BYTES,
            ).use { counted ->
                TarArchiveOutputStream(ZstdOutputStream(counted, 9)).use { output ->
                    output.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    output.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                    roots.forEach { rootName ->
                        val root = staging.resolve(rootName)
                        Files.walk(root).use { paths ->
                            paths.sorted(compareBy<Path>({ it.nameCount }, { it.toString() }))
                                .forEach { path ->
                                    val relative = root.relativize(path).toString().replace('\\', '/')
                                    val archivePath = if (relative.isEmpty()) {
                                        "$rootName/"
                                    } else {
                                        "$rootName/$relative"
                                    }
                                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                                        val entry = TarArchiveEntry(archivePath.trimEnd('/') + "/")
                                        output.putArchiveEntry(entry)
                                        output.closeArchiveEntry()
                                    } else {
                                        copyFileToTar(path, archivePath, output)
                                    }
                                }
                        }
                    }
                }
                counted.count
            }
            if (Files.size(temporary) > MAX_COMPRESSED_BYTES) {
                throw Modpack2ArchiveException("生成的source.tar.zst压缩后超过2GiB")
            }
            moveNoReplace(temporary, target)
        } catch (error: Throwable) {
            deleteTreeNoSymlink(temporary)
            throw error
        }
    }

    private fun copyDirectoryTree(source: Path, target: Path) {
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
            throw Modpack2ArchiveException("临时归档树缺少目录: $source")
        }
        ensureDirectoryPath(target)
        Files.walk(source).use { paths ->
            paths.sorted(compareBy<Path>({ it.nameCount }, { it.toString() }))
                .forEach { path ->
                    val relative = source.relativize(path)
                    val destination = target.resolve(relative.toString()).normalize()
                    if (!destination.startsWith(target)) {
                        throw Modpack2ArchiveException("临时归档树包含穿越路径: $relative")
                    }
                    if (Files.isSymbolicLink(path)) {
                        throw Modpack2ArchiveException("临时归档树包含符号链接: $path")
                    }
                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        ensureDirectoryPath(destination)
                    } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        ensureDirectoryPath(destination.parent)
                        Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES)
                    } else {
                        throw Modpack2ArchiveException("临时归档树包含特殊文件: $path")
                    }
                }
        }
    }

    private fun normalizeRelativePath(path: String): String {
        val raw = path.trim().replace('\\', '/')
        val firstSlash = raw.indexOf('/')
        val normalized = if (firstSlash > 0 && raw.substring(0, firstSlash).equals(SERVER_ROOT, ignoreCase = true)) {
            raw.substring(firstSlash + 1)
        } else {
            raw
        }
        if (normalized.isBlank() || normalized.startsWith('/') ||
            WINDOWS_ABSOLUTE_PATH.matches(normalized) ||
            normalized.split('/').any { it.isBlank() || it == "." || it == ".." }
        ) {
            throw Modpack2ArchiveException("继承server文件路径无效: $path")
        }
        return normalized
    }

    private fun sha1File(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-1")
        Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun String.toRawFileRoot(): Modpack2RawFileRoot = when (this) {
        CLIENT_ROOT -> Modpack2RawFileRoot.Client
        SHARED_ROOT -> Modpack2RawFileRoot.Shared
        SERVER_ROOT -> Modpack2RawFileRoot.Server
        else -> throw Modpack2ArchiveException("未知原始文件根目录: $this")
    }

    private fun parseEntry(entry: TarArchiveEntry): ParsedEntry {
        if (entry.isSymbolicLink || entry.isLink || (!entry.isDirectory && !entry.isFile)) {
            throw Modpack2ArchiveException("归档包含不支持的链接或特殊条目: ${entry.name}")
        }
        val originalPath = entry.name
        val replaced = originalPath.replace('\\', '/')
        if (replaced.isBlank() || replaced.startsWith('/') || replaced.startsWith("//") ||
            WINDOWS_ABSOLUTE_PATH.matches(replaced) || replaced.indexOf('\u0000') >= 0
        ) {
            throw Modpack2ArchiveException("归档包含绝对路径: $originalPath")
        }
        val directory = entry.isDirectory || replaced.endsWith('/')
        val withoutTrailingSlash = replaced.trimEnd('/')
        if (withoutTrailingSlash.isBlank()) {
            throw Modpack2ArchiveException("归档包含空路径")
        }
        val segments = withoutTrailingSlash.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) {
            throw Modpack2ArchiveException("归档包含穿越路径: $originalPath")
        }
        val root = segments.first()
        if (root !in ALL_ROOTS) {
            throw Modpack2ArchiveException("归档根目录无效")
        }
        val relativePath = segments.drop(1).joinToString("/")
        if (relativePath.isEmpty() && !directory) {
            throw Modpack2ArchiveException("根目录条目必须是目录: $originalPath")
        }
        return ParsedEntry(
            originalPath = originalPath,
            root = root,
            relativePath = relativePath,
            kind = if (directory) EntryKind.DIRECTORY else EntryKind.FILE
        )
    }

    private fun validateMergedPaths(roots: Map<String, RootEntries>, side: Modpack2MergeRoot) {
        val selected = LinkedHashMap<String, EntryKind>()
        overlayPaths(selected, roots.getValue(SHARED_ROOT).paths)
        overlayPaths(
            selected,
            roots.getValue(if (side == Modpack2MergeRoot.CLIENT) CLIENT_ROOT else SERVER_ROOT).paths
        )
        selected.remove("")
        selected.keys.forEach { path ->
            var parent = path.substringBeforeLast('/', missingDelimiterValue = "")
            while (parent.isNotEmpty()) {
                if (selected[parent] == EntryKind.FILE) {
                    throw Modpack2ArchiveException("文件和目录路径冲突: $parent / $path")
                }
                parent = parent.substringBeforeLast('/', missingDelimiterValue = "")
            }
        }
    }

    private fun overlayPaths(selected: MutableMap<String, EntryKind>, incoming: Map<String, EntryKind>) {
        incoming.forEach { (path, kind) ->
            val previous = selected[path]
            if (previous != null && previous != kind) {
                throw Modpack2ArchiveException("shared与覆盖根存在文件/目录冲突: $path")
            }
            selected[path] = kind
        }
    }

    private fun writeMergedArchive(
        staging: Path,
        roots: Map<String, RootEntries>,
        side: Modpack2MergeRoot,
        target: Path,
        filterServerMedia: Boolean
    ): Long {
        val selected = mergedPaths(roots, side, filterServerMedia)
        if (selected.size > MAX_ENTRIES) {
            throw Modpack2ArchiveException("生成归档条目数量超过${MAX_ENTRIES}")
        }
        val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp")
        rejectExistingDestination(temporary)
        try {
            CountingOutputStream(
                Files.newOutputStream(
                    temporary,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
                ),
                MAX_COMPRESSED_BYTES
            ).use { counted ->
                TarArchiveOutputStream(ZstdOutputStream(counted, 9)).use { output ->
                    output.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    output.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                    selected.entries
                        .sortedWith(compareBy<Map.Entry<String, EntryKind>> { depth(it.key) }.thenBy { it.key })
                        .forEach { (path, kind) ->
                            val sourceRoot = selectedSourceRoot(roots, side, path, kind)
                            val sourcePath = staging.resolve(sourceRoot).resolve(path)
                            if (kind == EntryKind.DIRECTORY) {
                                val entry = TarArchiveEntry("$path/")
                                output.putArchiveEntry(entry)
                                output.closeArchiveEntry()
                            } else {
                                copyFileToTar(sourcePath, path, output)
                            }
                        }
                }
                counted.count
            }
            if (Files.size(temporary) > MAX_COMPRESSED_BYTES) {
                throw Modpack2ArchiveException("生成的tar.zst压缩后超过2GiB")
            }
            moveNoReplace(temporary, target)
            return Files.size(target)
        } catch (error: Throwable) {
            deleteTreeNoSymlink(temporary)
            throw error
        }
    }

    private fun mergedPaths(
        roots: Map<String, RootEntries>,
        side: Modpack2MergeRoot,
        filterServerMedia: Boolean
    ): Map<String, EntryKind> {
        val selected = LinkedHashMap<String, EntryKind>()
        overlayPaths(selected, roots.getValue(SHARED_ROOT).paths)
        overlayPaths(
            selected,
            roots.getValue(if (side == Modpack2MergeRoot.CLIENT) CLIENT_ROOT else SERVER_ROOT).paths
        )
        selected.remove("")
        if (filterServerMedia) {
            selected.entries.removeIf { (path, kind) -> kind == EntryKind.FILE && isServerMedia(path) }
        }
        return selected
    }

    private fun selectedSourceRoot(
        roots: Map<String, RootEntries>,
        side: Modpack2MergeRoot,
        path: String,
        kind: EntryKind
    ): String {
        val overlay = if (side == Modpack2MergeRoot.CLIENT) CLIENT_ROOT else SERVER_ROOT
        if (roots.getValue(overlay).paths[path] == kind) return overlay
        return SHARED_ROOT
    }

    private fun copyMergedTree(
        staging: Path,
        roots: Map<String, RootEntries>,
        side: Modpack2MergeRoot,
        target: Path,
        filterServerMedia: Boolean
    ) {
        mergedPaths(roots, side, filterServerMedia)
            .entries
            .sortedWith(compareBy<Map.Entry<String, EntryKind>> { depth(it.key) }.thenBy { it.key })
            .forEach { (path, kind) ->
                val sourceRoot = selectedSourceRoot(roots, side, path, kind)
                val source = staging.resolve(sourceRoot).resolve(path)
                val destination = target.resolve(path)
                if (kind == EntryKind.DIRECTORY) {
                    ensureDirectoryPath(destination)
                } else {
                    ensureDirectoryPath(destination.parent ?: target)
                    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                        if (Files.isSymbolicLink(destination) || Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)) {
                            throw Modpack2ArchiveException("目标存在文件/目录冲突: $path")
                        }
                        Files.newOutputStream(destination, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { output ->
                            copyFile(source, output)
                        }
                    } else {
                        Files.newOutputStream(
                            destination,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE
                        ).use { output -> copyFile(source, output) }
                    }
                }
            }
    }

    private fun copyFileToTar(source: Path, path: String, output: TarArchiveOutputStream) {
        if (Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw Modpack2ArchiveException("临时归档树包含非普通文件: $path")
        }
        val entry = TarArchiveEntry(path)
        entry.size = Files.size(source)
        output.putArchiveEntry(entry)
        Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS).buffered().use { input -> input.copyTo(output) }
        output.closeArchiveEntry()
    }

    private fun copyFile(source: Path, output: OutputStream) {
        if (Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw Modpack2ArchiveException("临时归档树包含非普通文件: $source")
        }
        Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS).buffered().use { input -> input.copyTo(output) }
    }

    private fun createFileFromEntry(input: InputStream, destination: Path, size: Long) {
        rejectExistingDestination(destination)
        Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
            drainEntry(input, size, output)
        }
    }

    private fun drainEntry(input: InputStream, expectedSize: Long, output: OutputStream) {
        if (expectedSize < 0) throw Modpack2ArchiveException("归档条目缺少确定大小")
        var copied = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (copied < expectedSize) {
            val wanted = minOf(buffer.size.toLong(), expectedSize - copied).toInt()
            val read = input.read(buffer, 0, wanted)
            if (read < 0) throw Modpack2ArchiveException("归档条目内容长度小于声明大小")
            if (read == 0) continue
            output.write(buffer, 0, read)
            copied += read
        }
        if (copied != expectedSize) throw Modpack2ArchiveException("归档条目内容长度不匹配")
    }

    private fun ensureZstdMagic(path: Path) {
        val header = ByteArray(4)
        var offset = 0
        Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
            while (offset < header.size) {
                val read = input.read(header, offset, header.size - offset)
                if (read < 0) break
                offset += read
            }
        }
        if (offset != 4 || !header.contentEquals(ZSTD_MAGIC)) {
            throw Modpack2ArchiveException("只支持tar.zst归档")
        }
    }

    private fun validateSourceFile(path: Path) {
        val attributes = runCatching {
            Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        }.getOrElse { throw Modpack2ArchiveException("无法读取source.tar.zst", it) }
        if (!attributes.isRegularFile || Files.isSymbolicLink(path)) {
            throw Modpack2ArchiveException("source.tar.zst必须是普通文件")
        }
    }

    private fun compressedSize(path: Path): Long = runCatching {
        Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).size()
    }.getOrElse { throw Modpack2ArchiveException("无法读取source.tar.zst大小", it) }

    private fun ensureDestinationParent(path: Path) {
        ensureDirectoryPath(path.parent ?: throw Modpack2ArchiveException("归档目标缺少父目录"))
    }

    private fun rejectExistingDestination(path: Path) {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw Modpack2ArchiveException("归档目标已存在: $path")
        }
    }

    private fun ensureDirectoryPath(path: Path?) {
        val directory = path ?: throw Modpack2ArchiveException("缺少目标目录")
        val absolute = directory.toAbsolutePath().normalize()
        val root = absolute.root ?: throw Modpack2ArchiveException("目标目录必须是绝对路径")
        var current = root
        for (name in absolute) {
            current = current.resolve(name.toString())
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw Modpack2ArchiveException("目标路径存在符号链接或文件: $current")
                }
            } else {
                try {
                    Files.createDirectory(current)
                } catch (error: Throwable) {
                    if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(current)) {
                        throw Modpack2ArchiveException("无法创建目标目录: $current", error)
                    }
                }
            }
        }
    }

    private fun moveNoReplace(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private fun isServerMedia(path: String): Boolean {
        val name = path.substringAfterLast('/')
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in SERVER_PACK_MEDIA_EXTENSIONS
    }

    private fun depth(path: String): Int = path.count { it == '/' }

    private fun checkedAdd(left: Long, right: Long): Long {
        if (right < 0 || left > Long.MAX_VALUE - right) {
            throw Modpack2ArchiveException("归档大小溢出")
        }
        return left + right
    }

    private fun deleteTreeNoSymlink(path: Path?) {
        if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isSymbolicLink(path)) {
            Files.deleteIfExists(path)
            return
        }
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.newDirectoryStream(path).use { children: DirectoryStream<Path> ->
                children.forEach(::deleteTreeNoSymlink)
            }
        }
        Files.deleteIfExists(path)
    }

    private data class SourceReadResult(
        val info: Modpack2SourceArchiveInfo,
        val roots: Map<String, RootEntries>
    )

    private data class ParsedEntry(
        val originalPath: String,
        val root: String,
        val relativePath: String,
        val kind: EntryKind
    )

    private enum class EntryKind {
        FILE,
        DIRECTORY
    }

    private class RootEntries {
        val paths = LinkedHashMap<String, EntryKind>()
        val seenPaths = HashSet<String>()

        fun addPath(path: String, kind: EntryKind) {
            if (kind == EntryKind.FILE) {
                val previous = paths[path]
                if (previous == EntryKind.DIRECTORY) {
                    throw Modpack2ArchiveException("文件和目录路径冲突: $path")
                }
                paths[path] = EntryKind.FILE
                addImplicitParents(path)
                return
            }
            val previous = paths[path]
            if (previous == EntryKind.FILE) {
                throw Modpack2ArchiveException("文件和目录路径冲突: $path")
            }
            paths[path] = EntryKind.DIRECTORY
            addImplicitParents(path)
        }

        private fun addImplicitParents(path: String) {
            var parent = path.substringBeforeLast('/', missingDelimiterValue = "")
            while (parent.isNotEmpty()) {
                if (paths[parent] == EntryKind.FILE) {
                    throw Modpack2ArchiveException("文件和目录路径冲突: $parent / $path")
                }
                paths.putIfAbsent(parent, EntryKind.DIRECTORY)
                parent = parent.substringBeforeLast('/', missingDelimiterValue = "")
            }
        }
    }

    private class CountingOutputStream(
        private val delegate: OutputStream,
        private val limit: Long
    ) : OutputStream() {
        var count: Long = 0
            private set

        override fun write(value: Int) {
            ensureCapacity(1)
            delegate.write(value)
            count++
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            ensureCapacity(length.toLong())
            delegate.write(bytes, offset, length)
            count += length
        }

        override fun flush() = delegate.flush()

        override fun close() = delegate.close()

        private fun ensureCapacity(incoming: Long) {
            if (incoming < 0 || count > limit - incoming) {
                throw Modpack2ArchiveException("生成的tar.zst压缩后超过2GiB")
            }
        }
    }

    private object DiscardOutputStream : OutputStream() {
        override fun write(value: Int) = Unit
        override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
    }

    private const val CLIENT_ROOT = "client"
    private const val SHARED_ROOT = "shared"
    private const val SERVER_ROOT = "server"
    private val ALL_ROOTS = listOf(CLIENT_ROOT, SHARED_ROOT, SERVER_ROOT, METADATA_ROOT)
    private val ROOTS = listOf(CLIENT_ROOT, SHARED_ROOT, SERVER_ROOT)
    private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:/.*")
    private val ZSTD_MAGIC = byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte())
}
