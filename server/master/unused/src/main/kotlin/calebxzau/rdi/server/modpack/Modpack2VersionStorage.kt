package calebxzau.rdi.server.modpack

import java.io.IOException
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

data class Modpack2VersionPaths(
    val directory: Path,
    val sourceArchive: Path,
    val clientArchive: Path
)

/** A version directory moved aside until its database transaction commits. */
class Modpack2DeletingVersion internal constructor(
    val original: Path,
    val deleting: Path
)

/**
 * Filesystem layout and transaction-adjacent moves for Modpack2 versions.
 *
 * It intentionally does not know about PostgreSQL or shared CAS. A caller can
 * move a version directory before its DB mutation, restore it on rollback, and
 * remove it only after commit.
 */
class Modpack2VersionStorage(storageRoot: java.io.File) {
    private val root = storageRoot.toPath().toAbsolutePath().normalize().resolve("modpack2")
    private val deletingRoot = root.resolve(".deleting")

    fun paths(modpackId: UUID, versionId: UUID): Modpack2VersionPaths {
        val directory = root.resolve(modpackId.toString()).resolve(versionId.toString())
        return Modpack2VersionPaths(
            directory = directory,
            sourceArchive = directory.resolve("source.tar.zst"),
            clientArchive = directory.resolve("client.tar.zst")
        )
    }

    /** Creates the version directory and returns its two canonical artifact paths. */
    fun prepare(modpackId: UUID, versionId: UUID): Modpack2VersionPaths {
        val result = paths(modpackId, versionId)
        ensureDirectoryPath(result.directory)
        return result
    }

    /** Moves a completed upload into the canonical source archive location. */
    fun adoptSource(upload: java.io.File, modpackId: UUID, versionId: UUID): Modpack2VersionPaths {
        val result = prepare(modpackId, versionId)
        val source = upload.toPath().toAbsolutePath().normalize()
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
            throw IOException("上传归档必须是普通文件")
        }
        if (Files.exists(result.sourceArchive, LinkOption.NOFOLLOW_LINKS) ||
            Files.isSymbolicLink(result.sourceArchive)
        ) {
            throw IOException("source.tar.zst已存在: ${result.sourceArchive}")
        }
        moveNoReplace(source, result.sourceArchive)
        return result
    }

    /**
     * Moves a complete version directory to modpack2/.deleting without
     * replacing an existing path. A missing version has nothing to move.
     */
    fun moveToDeleting(modpackId: UUID, versionId: UUID): Modpack2DeletingVersion? {
        val paths = paths(modpackId, versionId)
        if (!Files.exists(paths.directory, LinkOption.NOFOLLOW_LINKS)) return null
        rejectSymlinkOrNonDirectory(paths.directory)
        ensureDirectoryPath(deletingRoot)
        val deleting = deletingRoot.resolve(
            "${modpackId}-${versionId}-${UUID.randomUUID()}"
        )
        if (Files.exists(deleting, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(deleting)) {
            throw IOException("deleting目标已存在: $deleting")
        }
        moveNoReplace(paths.directory, deleting)
        return Modpack2DeletingVersion(paths.directory, deleting)
    }

    /** Restores a moved version after a failed database transaction. */
    fun restore(moved: Modpack2DeletingVersion) {
        validateHandle(moved)
        if (!Files.exists(moved.deleting, LinkOption.NOFOLLOW_LINKS)) return
        rejectSymlinkOrNonDirectory(moved.deleting)
        if (Files.exists(moved.original, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(moved.original)) {
            throw IOException("无法恢复版本，原目录已存在: ${moved.original}")
        }
        ensureDirectoryPath(moved.original.parent)
        moveNoReplace(moved.deleting, moved.original)
    }

    /** Deletes only the already-detached directory after the DB commit. */
    fun postCommitCleanup(moved: Modpack2DeletingVersion?) {
        if (moved == null) return
        validateHandle(moved)
        deleteTreeNoSymlink(moved.deleting)
    }

    private fun validateHandle(moved: Modpack2DeletingVersion) {
        val expectedParent = deletingRoot.toAbsolutePath().normalize()
        val deleting = moved.deleting.toAbsolutePath().normalize()
        val original = moved.original.toAbsolutePath().normalize()
        if (deleting.parent != expectedParent || !original.startsWith(root) ||
            root.relativize(original).nameCount != 2
        ) {
            throw IOException("无效的Modpack2 deleting句柄")
        }
    }

    private fun rejectSymlinkOrNonDirectory(path: Path) {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("版本目录必须是普通目录: $path")
        }
    }

    private fun ensureDirectoryPath(path: Path?) {
        val directory = path ?: throw IOException("缺少存储目录")
        val absolute = directory.toAbsolutePath().normalize()
        val filesystemRoot = absolute.root ?: throw IOException("存储目录必须是绝对路径")
        var current = filesystemRoot
        for (name in absolute) {
            current = current.resolve(name.toString())
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw IOException("存储路径存在符号链接或文件: $current")
                }
            } else {
                try {
                    Files.createDirectory(current)
                } catch (error: Throwable) {
                    if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                        throw IOException("无法创建存储目录: $current", error)
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

    private fun deleteTreeNoSymlink(path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
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
}
