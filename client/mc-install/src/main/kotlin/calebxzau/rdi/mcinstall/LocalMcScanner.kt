package calebxzau.rdi.mcinstall

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

data class LocalMcInstallation(val path: Path)

fun interface FixedDriveProvider {
    fun fixedDrives(): Result<List<Path>>
}

interface McInstallationScanner {
    suspend fun scan(
        roots: List<Path>,
        onFound: suspend (Path) -> Unit = {}
    ): Result<List<Path>>
}

class WindowsFixedDriveProvider : FixedDriveProvider {
    override fun fixedDrives(): Result<List<Path>> = runCatching {
        if (!isWindows()) return@runCatching emptyList()
        java.io.File.listRoots()
            .filter { com.sun.jna.platform.win32.Kernel32.INSTANCE.GetDriveType(it.path) == com.sun.jna.platform.win32.WinBase.DRIVE_FIXED }
            .map { it.toPath().toAbsolutePath().normalize() }
            .distinctBy(::pathKey)
    }
}

class LocalMcScanner(
    private val enumerator: McDirectoryEnumerator = WindowsMcDirectoryEnumerator(),
    private val validator: McInstallationValidator = DefaultMcInstallationValidator(),
    private val maxWorkersPerDrive: Int = MAX_WORKERS_PER_DRIVE,
    private val enumerationSemaphore: Semaphore = Semaphore(MAX_GLOBAL_ENUMERATORS)
) : McInstallationScanner {
    override suspend fun scan(
        roots: List<Path>,
        onFound: suspend (Path) -> Unit
    ): Result<List<Path>> {
        return try {
            val drives = roots.map { it.toAbsolutePath().normalize() }
                .distinctBy(::pathKey)
            val found = ConcurrentHashMap<String, Path>()
            coroutineScope {
                drives.map { drive ->
                    async(Dispatchers.IO) {
                        scanDrive(drive, drives, found, onFound)
                    }
                }.awaitAll().forEach { result ->
                    result.getOrThrow()
                }
            }
            Result.success(found.values.toList())
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            Result.failure(cause)
        }
    }

    suspend fun scan(roots: List<Path>): Result<List<Path>> = scan(roots, {})

    private suspend fun scanDrive(
        drive: Path,
        fixedDriveRoots: List<Path>,
        found: ConcurrentHashMap<String, Path>,
        onFound: suspend (Path) -> Unit
    ): Result<Unit> {
        return try {
            coroutineScope {
                val queue = Channel<Path>(Channel.UNLIMITED)
                val pending = AtomicInteger(1)
                val rootFailure = AtomicReference<Throwable?>()
                queue.send(drive)

                val workers = List(maxWorkersPerDrive.coerceIn(1, MAX_WORKERS_PER_DRIVE)) {
                    launch(Dispatchers.IO) {
                        for (directory in queue) {
                            try {
                                val entries = enumerationSemaphore.withPermit {
                                    enumerator.enumerate(directory)
                                }
                                entries.fold(
                                    onSuccess = { children ->
                                        children.forEach { child ->
                                            val childName = child.path.fileName?.toString() ?: return@forEach
                                            if (childName.equals(".minecraft", ignoreCase = true)) {
                                                when (val validation = validator.validateDiscoveredCandidate(child.path, fixedDriveRoots)) {
                                                    is McInstallationValidation.Valid -> {
                                                        val realPath = validation.realPath
                                                        if (found.putIfAbsent(pathKey(realPath), realPath) == null) {
                                                            onFound(realPath)
                                                        }
                                                    }

                                                    McInstallationValidation.Missing,
                                                    McInstallationValidation.Invalid,
                                                    McInstallationValidation.Inaccessible -> Unit
                                                }
                                                return@forEach
                                            }
                                            if (child.isReparsePoint) return@forEach
                                            pending.incrementAndGet()
                                            queue.send(child.path)
                                        }
                                    },
                                    onFailure = { cause ->
                                        if (directory == drive) rootFailure.compareAndSet(null, cause)
                                    }
                                )
                            } finally {
                                if (pending.decrementAndGet() == 0) queue.close()
                            }
                        }
                    }
                }
                workers.joinAll()
                rootFailure.get()?.let { throw IOException("Cannot enumerate fixed drive $drive", it) }
                Result.success(Unit)
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            Result.failure(cause)
        }
    }

    private companion object {
        const val MAX_WORKERS_PER_DRIVE = 4
        const val MAX_GLOBAL_ENUMERATORS = 12
    }
}

private fun pathKey(path: Path): String =
    path.toAbsolutePath().normalize().toString().lowercase(Locale.ROOT)

private fun isWindows(): Boolean = System.getProperty("os.name").contains("Windows", ignoreCase = true)
