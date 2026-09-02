package calebxzhou.rdi.master.service.host2

import calebxzhou.rdi.common.util.deleteRecursivelyNoSymlink
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.Host2SetupStatus
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.master.HOST2_DIR
import io.ktor.http.content.PartData
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

class Host2FileService(private val hostService: Host2Service) {
    suspend fun list(player: RAccount, id: UUID, path: String): List<Host.FileEntry> {
        hostService.requireAdmin(player, id)
        val root = hostRoot(id)
        if (path.isBlank()) return OPERABLE_DIRS.sorted().mapNotNull { name ->
            root.resolve(name).takeIf(File::isDirectory)?.toEntry(root)
        }
        val directory = resolve(id, path, allowRoot = true).file
        if (!directory.exists()) return emptyList()
        if (!directory.isDirectory) throw RequestError("目标不是目录")
        return directory.listFiles()?.asSequence()
            ?.filterNot { Files.isSymbolicLink(it.toPath()) }
            ?.filter { it.isDirectory && !it.relativePath(root).isUnderTacz() || it.isFile && it.relativePath(root).isAllowedFile() }
            ?.map { it.toEntry(root) }
            ?.sortedWith(compareBy<Host.FileEntry> { !it.directory }.thenBy { it.name.lowercase() })
            ?.toList().orEmpty()
    }

    suspend fun search(player: RAccount, id: UUID, query: String): List<Host.FileEntry> {
        hostService.requireAdmin(player, id)
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()
        val root = hostRoot(id)
        val results = mutableListOf<Host.FileEntry>()
        val directories = ArrayDeque<File>()
        OPERABLE_DIRS.map(root::resolve)
            .filter { it.isDirectory && !Files.isSymbolicLink(it.toPath()) }
            .forEach(directories::addLast)
        while (directories.isNotEmpty() && results.size < 200) {
            val directory = directories.removeFirst()
            directory.listFiles()?.forEach { file ->
                if (results.size >= 200 || Files.isSymbolicLink(file.toPath())) return@forEach
                val relative = file.relativePath(root)
                if (file.isDirectory && !relative.isUnderTacz()) directories += file
                if ((file.isDirectory || relative.isAllowedFile()) &&
                    (file.name.contains(normalized, true) || relative.contains(normalized, true))) {
                    results += file.toEntry(root)
                }
            }
        }
        return results
    }

    suspend fun read(player: RAccount, id: UUID, path: String): Host.FileContentVo {
        hostService.requireAdmin(player, id)
        val resolved = resolve(id, path)
        validateFilePath(resolved.path)
        if (!resolved.file.isFile) throw RequestError("文件不存在")
        return Host.FileContentVo(resolved.path, resolved.file.readText(), resolved.file.length(), resolved.file.lastModified())
    }

    suspend fun write(player: RAccount, id: UUID, dto: Host.FileWriteDto): Host.FileContentVo =
        hostService.withMutation(id) {
            requireWritable(player, id)
            val resolved = resolve(id, dto.path)
            validateFilePath(resolved.path)
            val bytes = dto.content.toByteArray()
            ensureQuota(id, resolved.file.length(), bytes.size.toLong())
            resolved.file.parentFile.mkdirs()
            Files.write(resolved.file.toPath(), bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            Host.FileContentVo(resolved.path, dto.content, bytes.size.toLong(), resolved.file.lastModified())
        }

    suspend fun create(player: RAccount, id: UUID, dto: Host.FileCreateDto): Host.FileEntry =
        hostService.withMutation(id) {
            requireWritable(player, id)
            val resolved = resolve(id, dto.path)
            if (resolved.file.exists()) throw RequestError("目标已经存在")
            if (dto.directory) {
                if (resolved.path.isUnderTacz()) throw RequestError("TaCZ目录不允许创建子目录")
                if (!resolved.file.mkdirs()) throw RequestError("创建目录失败")
            } else {
                validateFilePath(resolved.path)
                val bytes = dto.content.toByteArray()
                ensureQuota(id, 0, bytes.size.toLong())
                resolved.file.parentFile.mkdirs()
                Files.write(resolved.file.toPath(), bytes, StandardOpenOption.CREATE_NEW)
            }
            resolved.file.toEntry(hostRoot(id))
        }

    suspend fun rename(player: RAccount, id: UUID, dto: Host.FileRenameDto): Host.FileEntry =
        hostService.withMutation(id) {
            requireWritable(player, id)
            val source = resolve(id, dto.from)
            val target = resolve(id, dto.to)
            if (!source.file.exists()) throw RequestError("源文件不存在")
            if (target.file.exists()) throw RequestError("目标已经存在")
            if (source.file.isFile) validateFilePath(target.path)
            if (source.file.isDirectory && target.path.isUnderTacz()) throw RequestError("TaCZ目录不允许创建子目录")
            target.file.parentFile.mkdirs()
            Files.move(source.file.toPath(), target.file.toPath(), StandardCopyOption.ATOMIC_MOVE)
            target.file.toEntry(hostRoot(id))
        }

    suspend fun delete(player: RAccount, id: UUID, dto: Host.FileDeleteDto) = hostService.withMutation(id) {
        requireWritable(player, id)
        val resolved = resolve(id, dto.path)
        if (!resolved.file.exists()) return@withMutation
        resolved.file.deleteRecursivelyNoSymlink()
        if (resolved.file.exists()) throw RequestError("删除失败")
    }

    suspend fun upload(player: RAccount, id: UUID, call: ApplicationCall): Host.FileUploadVo {
        requireWritable(player, id)
        val staging = HOST2_DIR.resolve(".staging").resolve("$id-file-${UUID.randomUUID()}").apply { mkdirs() }
        val temp = staging.resolve("upload.tmp")
        var path = call.request.queryParameters["path"]
        var received = false
        try {
            val multipart = call.receiveMultipart(formFieldLimit = HOST2_QUOTA_BYTES)
            while (true) {
                val part = multipart.readPart() ?: break
                try {
                    when (part) {
                        is PartData.FormItem -> if (part.name == "path") path = part.value
                        is PartData.FileItem -> if (part.name == "file") {
                            if (received) throw RequestError("只能上传一个文件")
                            receiveFile(part.provider(), temp)
                            received = true
                        }
                        else -> Unit
                    }
                } finally {
                    part.dispose()
                }
            }
            if (!received) throw RequestError("缺少文件")
            return hostService.withMutation(id) {
                requireWritable(player, id)
                val resolved = resolve(id, path ?: throw RequestError("缺少路径"))
                validateFilePath(resolved.path)
                ensureQuota(id, resolved.file.length(), temp.length())
                resolved.file.parentFile.mkdirs()
                Files.move(temp.toPath(), resolved.file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                Host.FileUploadVo(resolved.path, resolved.file.length(), resolved.file.lastModified())
            }
        } finally {
            staging.deleteRecursivelyNoSymlink()
        }
    }

    private suspend fun requireWritable(player: RAccount, id: UUID) {
        val host = hostService.requireAdmin(player, id)
        if (host.setupStatus == Host2SetupStatus.PROCESSING) throw RequestError("server pack正在处理")
        if (host.setupStatus != Host2SetupStatus.READY) throw RequestError("server pack尚未配置完成")
    }

    private fun resolve(id: UUID, rawPath: String, allowRoot: Boolean = false): Resolved {
        val rawNormalized = rawPath.trim().replace('\\', '/')
        if (rawNormalized.startsWith('/')) throw RequestError("非法文件路径")
        val normalized = rawNormalized
        if (normalized.isBlank()) throw RequestError("文件路径不能为空")
        val root = hostRoot(id).toPath().toAbsolutePath().normalize()
        val target = root.resolve(normalized).normalize()
        if (!target.startsWith(root)) throw RequestError("非法文件路径")
        val relative = root.relativize(target).toString().replace('\\', '/')
        val allowedRoot = relative.substringBefore('/')
        if (allowedRoot !in OPERABLE_DIRS) throw RequestError("只能操作${OPERABLE_DIRS.joinToString()}目录")
        if (!allowRoot && relative == allowedRoot) throw RequestError("不能直接操作目录根")
        checkNoSymlink(root, target)
        return Resolved(relative, target.toFile())
    }

    private fun checkNoSymlink(root: Path, target: Path) {
        var current = root
        root.relativize(target).forEach { segment ->
            current = current.resolve(segment)
            if (Files.isSymbolicLink(current)) throw RequestError("不允许操作软链接")
        }
    }

    private fun validateFilePath(path: String) {
        if (!path.isAllowedFile()) throw RequestError("不支持此文件类型")
    }

    private fun String.isAllowedFile(): Boolean {
        if (isUnderTacz()) return removePrefix("tacz/").let { '/' !in it && it.substringAfterLast('.', "").equals("zip", true) }
        return substringAfterLast('.', "").lowercase() in ALLOWED_EXTENSIONS
    }

    private fun String.isUnderTacz() = this == "tacz" || startsWith("tacz/")

    private fun ensureQuota(id: UUID, replacedBytes: Long, newBytes: Long) {
        if (Host2RuntimeService.sizeBytes(id) - replacedBytes + newBytes > HOST2_QUOTA_BYTES) {
            throw RequestError("写入后将超过8GiBquota")
        }
    }

    private suspend fun receiveFile(channel: ByteReadChannel, target: File) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        Files.newOutputStream(target.toPath(), StandardOpenOption.CREATE_NEW).use { output ->
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                if (read > 0) {
                    total += read
                    if (total > HOST2_QUOTA_BYTES) throw RequestError("单个文件不能超过8GiB")
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private fun hostRoot(id: UUID) = HOST2_DIR.resolve(id.toString())
    private fun File.relativePath(root: File) = relativeTo(root).path.replace('\\', '/')
    private fun File.toEntry(root: File) = Host.FileEntry(relativePath(root), name, isDirectory, if (isFile) length() else 0, lastModified())
    private data class Resolved(val path: String, val file: File)
}

private val OPERABLE_DIRS = setOf("config", "datapacks", "tacz", "kubejs", "scripts")
private val ALLOWED_EXTENSIONS = setOf(
    "txt", "js", "json", "json5", "jsonc", "md", "ini", "toml", "yaml", "yml", "cfg", "zs",
    "properties", "snbt", "mcmeta", "bak", "lang", "lua", "mcfunction", "xml"
)
