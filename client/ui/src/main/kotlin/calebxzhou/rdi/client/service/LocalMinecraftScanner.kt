package calebxzhou.rdi.client.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class LocalMinecraftInstallation(
    val path: Path,
    val runtimeRoot: Boolean,
    val gameInstance: Boolean
)

class LocalMinecraftScanner {
    suspend fun scan(
        roots: List<Path>,
        onFound: suspend (LocalMinecraftInstallation) -> Unit = {}
    ): Result<List<LocalMinecraftInstallation>> = runCatching {
        coroutineScope {
            val pendingRoots = roots
                .map { it.toAbsolutePath().normalize() }
                .distinct()
                .filter { Files.isDirectory(it, NOFOLLOW_LINKS) }
            if (pendingRoots.isEmpty()) return@coroutineScope emptyList()
            val queue = Channel<Path>(Channel.UNLIMITED)
            val remaining = AtomicInteger(pendingRoots.size)
            val found = ConcurrentHashMap<Path, LocalMinecraftInstallation>()
            val dispatcher = Dispatchers.IO.limitedParallelism(workerCount())
            pendingRoots.forEach { queue.send(it) }
            val workers = List(workerCount()) {
                launch(dispatcher) {
                    for (directory in queue) {
                        try {
                            val installation = recognize(directory)
                            if (installation != null && found.putIfAbsent(installation.path, installation) == null) {
                                onFound(installation)
                            }
                            if (installation?.gameInstance == true && !installation.runtimeRoot) continue
                            childDirectories(directory).forEach { child ->
                                if (
                                    installation?.runtimeRoot == true &&
                                    child.fileName.toString() in RUNTIME_HEAVY_DIRECTORIES
                                ) return@forEach
                                remaining.incrementAndGet()
                                queue.send(child)
                            }
                        } finally {
                            if (remaining.decrementAndGet() == 0) queue.close()
                        }
                    }
                }
            }
            workers.joinAll()
            found.values.toList()
        }
    }

    fun recognize(path: Path): LocalMinecraftInstallation? {
        if (!Files.isDirectory(path, NOFOLLOW_LINKS)) return null
        val runtimeRoot = RUNTIME_DIRECTORIES.all { Files.isDirectory(path.resolve(it), NOFOLLOW_LINKS) }
        val modsDirectory = Files.isDirectory(path.resolve("mods"), NOFOLLOW_LINKS)
        val gameInstance = modsDirectory && (
            path.fileName?.toString().equals(".minecraft", ignoreCase = true) ||
                path.parent?.fileName?.toString().equals("versions", ignoreCase = true) ||
                INSTANCE_MARKERS.any { Files.exists(path.resolve(it), NOFOLLOW_LINKS) }
            )
        return if (runtimeRoot || gameInstance) {
            LocalMinecraftInstallation(path.toAbsolutePath().normalize(), runtimeRoot, gameInstance)
        } else {
            null
        }
    }

    private fun childDirectories(directory: Path): List<Path> = try {
        Files.newDirectoryStream(directory).use { entries ->
            buildList {
                entries.forEach { entry ->
                    try {
                        val attributes = Files.readAttributes(entry, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                        if (attributes.isDirectory && !attributes.isSymbolicLink && !attributes.isOther) add(entry)
                    } catch (_: IOException) {
                    } catch (_: SecurityException) {
                    }
                }
            }
        }
    } catch (_: IOException) {
        emptyList()
    } catch (_: SecurityException) {
        emptyList()
    }

    private fun workerCount(): Int =
        (Runtime.getRuntime().availableProcessors() * 2).coerceIn(8, 32)

    private companion object {
        val RUNTIME_DIRECTORIES = listOf("assets", "libraries", "versions")
        val RUNTIME_HEAVY_DIRECTORIES = setOf("assets", "libraries")
        val INSTANCE_MARKERS = listOf(
            "options.txt",
            "instance.cfg",
            "mmc-pack.json",
            "minecraftinstance.json",
            "instance.json",
            "launcher_profiles.json",
            "manifest.json",
            ".hmclversion.json",
            "hmclversion.json",
            "PCL"
        )
    }
}
