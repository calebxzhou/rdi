package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.database.MinecraftInstallationDatabase
import calebxzhou.rdi.client.database.MinecraftInstallationDatabaseHandle
import calebxzhou.rdi.client.database.MinecraftInstallationStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private val minecraftDiscoveryLogger = KotlinLogging.logger("MinecraftDiscovery")

fun interface MinecraftClock {
    fun now(): Long
}

object SystemMinecraftClock : MinecraftClock {
    override fun now(): Long = System.currentTimeMillis()
}

class LocalMinecraftDiscoveryService(
    private val databasePath: Path,
    private val fixedDriveProvider: FixedDriveProvider = WindowsFixedDriveProvider(),
    private val validator: MinecraftInstallationValidator = DefaultMinecraftInstallationValidator(),
    private val scanner: MinecraftInstallationScanner = LocalMinecraftScanner(validator = validator),
    private val clock: MinecraftClock = SystemMinecraftClock,
    private val environment: (String) -> String? = System::getenv,
    private val platformSupported: () -> Boolean = ::isWindows,
    private val databaseOpener: (Path) -> Result<MinecraftInstallationDatabaseHandle> = {
        MinecraftInstallationDatabase.open(it)
    },
    private val initialDatabaseHandle: MinecraftInstallationDatabaseHandle? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : AutoCloseable {
    private val scanMutex = Mutex()
    private val started = AtomicBoolean()
    private val closed = AtomicBoolean()
    private val databaseHandle = AtomicReference(initialDatabaseHandle)
    private var discoveryJob: Job? = null
    private val installationsByName = ConcurrentHashMap<String, Path>()
    private val mutableInstallations = MutableStateFlow<List<Path>>(emptyList())

    /** 当前已知的Minecraft安装根目录（真实路径），随记录新增/删除/全量扫描完成而更新。 */
    val installations: StateFlow<List<Path>> = mutableInstallations.asStateFlow()

    private fun reflectInstallations(
        upsert: List<Path> = emptyList(),
        remove: List<Path> = emptyList()
    ) {
        upsert.forEach { installationsByName[pathKey(it)] = it.toAbsolutePath().normalize() }
        remove.forEach { installationsByName.remove(pathKey(it)) }
        mutableInstallations.value = installationsByName.values
            .map { it.toAbsolutePath().normalize() }
            .distinctBy(::pathKey)
            .sortedBy { it.toString().lowercase(Locale.ROOT) }
    }

    @Synchronized
    fun start(): Job? {
        if (!platformSupported() || closed.get()) return null
        if (!started.compareAndSet(false, true)) return discoveryJob
        return scope.launch {
            scanMutex.withLock {
                discover()
            }
        }.also { discoveryJob = it }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.cancel()
        databaseHandle.getAndSet(null)?.close()
    }

    private suspend fun discover() {
        val providedHandle = databaseHandle.get()
        val opened = providedHandle ?: databaseOpener(databasePath).getOrElse { cause ->
            minecraftDiscoveryLogger.error(cause) { "初始化Minecraft安装数据库失败，本次跳过安装发现" }
            return
        }.also { databaseHandle.set(it) }
        if (closed.get()) {
            if (databaseHandle.compareAndSet(opened, null)) opened.close()
            return
        }
        databaseHandle.set(opened)
        try {
            val fixedDriveResult = fixedDriveProvider.fixedDrives()
            val fixedDrives = fixedDriveResult.getOrElse { cause ->
                minecraftDiscoveryLogger.error(cause) { "获取固定磁盘列表失败，本次跳过完整Minecraft扫描" }
                emptyList()
            }
            val now = clock.now()
            val store = opened.store
            validateStoredInstallations(store, fixedDrives, now)
            validateKnownCandidates(store, fixedDrives, now)

            val lastFullScanAt = store.lastFullScanAt().getOrElse { cause ->
                minecraftDiscoveryLogger.error(cause) { "读取Minecraft扫描状态失败，本次跳过完整扫描" }
                return
            }
            if (fixedDriveResult.isFailure) return
            if (lastFullScanAt == null || now - lastFullScanAt >= FULL_SCAN_INTERVAL_MS) {
                runFullScan(store, fixedDrives, now)
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            minecraftDiscoveryLogger.error(cause) { "Minecraft安装发现失败" }
        } finally {
            if (providedHandle == null && databaseHandle.compareAndSet(opened, null)) opened.close()
        }
    }

    private suspend fun validateStoredInstallations(
        store: MinecraftInstallationStore,
        fixedDrives: List<Path>,
        seenAt: Long
    ) {
        val records = store.list().getOrElse { cause ->
            minecraftDiscoveryLogger.error(cause) { "读取已记录的Minecraft安装失败" }
            return
        }
        records.forEach { record ->
            when (val validation = validator.validateStoredRealPath(record.path, fixedDrives)) {
                is MinecraftInstallationValidation.Valid -> {
                    val realPath = validation.realPath
                    store.upsert(realPath, seenAt).onSuccess {
                        if (pathKey(realPath) != pathKey(record.path)) {
                            store.delete(record.path).onFailure { cause ->
                                minecraftDiscoveryLogger.error(cause) { "清理旧Minecraft安装路径失败：${record.path}" }
                            }
                        }
                        logInstallation("更新", realPath)
                        reflectInstallations(
                            upsert = listOf(realPath),
                            remove = if (pathKey(realPath) != pathKey(record.path)) listOf(record.path) else emptyList()
                        )
                    }.onFailure { cause ->
                        minecraftDiscoveryLogger.error(cause) { "更新Minecraft安装失败：$realPath" }
                    }
                }

                MinecraftInstallationValidation.Missing,
                MinecraftInstallationValidation.Invalid -> {
                    store.delete(record.path).onSuccess {
                        logInstallation("删除", record.path)
                        reflectInstallations(remove = listOf(record.path))
                    }.onFailure { cause ->
                        minecraftDiscoveryLogger.error(cause) { "删除无效Minecraft安装失败：${record.path}" }
                    }
                }

                MinecraftInstallationValidation.Inaccessible -> Unit
            }
        }
    }

    private suspend fun validateKnownCandidates(
        store: MinecraftInstallationStore,
        fixedDrives: List<Path>,
        seenAt: Long
    ) {
        knownCandidates().forEach { candidate ->
            when (val validation = validator.validateDiscoveredCandidate(candidate, fixedDrives)) {
                is MinecraftInstallationValidation.Valid -> {
                    store.upsert(validation.realPath, seenAt).onSuccess {
                        logInstallation("发现", validation.realPath)
                        reflectInstallations(upsert = listOf(validation.realPath))
                    }.onFailure { cause ->
                        minecraftDiscoveryLogger.error(cause) { "保存Minecraft安装失败：${validation.realPath}" }
                    }
                }

                MinecraftInstallationValidation.Missing,
                MinecraftInstallationValidation.Invalid,
                MinecraftInstallationValidation.Inaccessible -> Unit
            }
        }
    }

    private suspend fun runFullScan(
        store: MinecraftInstallationStore,
        fixedDrives: List<Path>,
        startedAt: Long
    ) = coroutineScope {
        val discoveries = Channel<Path>(Channel.UNLIMITED)
        val writerFailure = AtomicReference<Throwable?>()
        val writer = launch(Dispatchers.IO) {
            for (path in discoveries) {
                store.upsert(path, startedAt).onSuccess {
                    logInstallation("发现", path)
                    reflectInstallations(upsert = listOf(path))
                }.onFailure { cause ->
                    writerFailure.compareAndSet(null, cause)
                    minecraftDiscoveryLogger.error(cause) { "保存Minecraft安装失败：$path" }
                }
            }
        }

        val scanResult = try {
            scanner.scan(fixedDrives) { discoveries.send(it) }
        } finally {
            discoveries.close()
        }
        writer.join()

        val failure = scanResult.exceptionOrNull() ?: writerFailure.get()
        if (failure != null) {
            minecraftDiscoveryLogger.error(failure) { "Minecraft固定磁盘扫描失败，本次不更新完整扫描时间" }
            return@coroutineScope
        }

        store.setLastFullScanAt(startedAt).onFailure { cause ->
            minecraftDiscoveryLogger.error(cause) { "保存Minecraft完整扫描时间失败" }
            return@coroutineScope
        }
        val installationCount = store.list().getOrElse { cause ->
            minecraftDiscoveryLogger.error(cause) { "读取Minecraft安装数量失败" }
            emptyList()
        }.size
        val elapsed = (clock.now() - startedAt).coerceAtLeast(0L)
        minecraftDiscoveryLogger.info { "Minecraft扫描完成：固定磁盘${fixedDrives.size}个，安装${installationCount}个，耗时${elapsed}ms" }
    }

    private fun knownCandidates(): List<Path> = buildList {
        listOf("APPDATA", "USERPROFILE").forEach { variable ->
            environment(variable)?.takeIf(String::isNotBlank)?.let { value ->
                runCatching { Path.of(value).resolve(".minecraft") }
                    .onSuccess(::add)
            }
        }
    }

    private fun logInstallation(action: String, path: Path) {
        minecraftDiscoveryLogger.info { "$action Minecraft安装：$path" }
    }

    private companion object {
        const val FULL_SCAN_INTERVAL_MS = 72L * 60 * 60 * 1000
    }
}

private fun pathKey(path: Path): String =
    path.toAbsolutePath().normalize().toString().lowercase(Locale.ROOT)

private fun isWindows(): Boolean = System.getProperty("os.name").contains("Windows", ignoreCase = true)
