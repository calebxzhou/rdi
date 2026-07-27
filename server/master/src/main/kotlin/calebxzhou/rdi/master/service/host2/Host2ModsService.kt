package calebxzhou.rdi.master.service.host2

import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Host2
import calebxzhou.rdi.common.model.Host2Operation
import calebxzhou.rdi.common.model.Host2SetupStatus
import calebxzhou.rdi.common.model.HostStatus
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.service.ModService
import calebxzhou.rdi.master.HOST2_DIR
import calebxzhou.rdi.master.infra.postgres.DatabaseProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.jar.JarFile

class Host2ModsService(
    private val database: DatabaseProvider,
    private val repository: Host2Repository,
    private val hostService: Host2Service,
    private val downloadService: Host2ModDownloadService
) {
    suspend fun list(player: RAccount, id: UUID): List<Host2.ModVo> {
        hostService.detail(player, id)
        return database.transaction {
            repository.mods(id).map { record ->
                Host2.ModVo(record.mod, enabledFile(id, record.mod).exists())
            }
        }
    }

    suspend fun add(player: RAccount, id: UUID, mods: List<Mod>) =
        hostService.withMutation(id, Host2Operation.MOD_ADD) {
            val host = requireMutable(player, id)
            if (mods.isEmpty()) throw RequestError("请选择要添加的Mod")
            val stagingDir = newStagingDir(id, "mod-add")
            val movedFiles = mutableListOf<File>()
            try {
                val selectedKeys = mutableSetOf<Pair<String, String>>()
                mods.forEach { mod ->
                    val key = mod.platform.lowercase() to mod.projectId
                    if (!selectedKeys.add(key)) throw RequestError("包含重复Mod项目: ${mod.slug}")
                    downloadService.download(mod, host.mcVersion, host.modLoader, stagingDir)
                }
                val existingModIds = readModIdsInDirectory(hostDir(id).resolve("mods"))
                val selectedModIds = mutableSetOf<String>()
                val reservedModIds = buildSet {
                    add("rdi")
                    if (host.mcVersion.mcVer in setOf("1.20.1", "1.21.1")) add("kotlinforforge")
                }
                stagingDir.listFiles().orEmpty().forEach { file ->
                    val ids = readModIds(file)
                    val reserved = ids.firstOrNull { it in reservedModIds }
                    if (reserved != null) throw RequestError("不能添加平台保留Mod: $reserved")
                    if (ids.any { it in existingModIds || !selectedModIds.add(it) }) {
                        throw RequestError("所选Mod与房间已有Mod重复: ${file.name}")
                    }
                }
                val addedBytes = stagingDir.listFiles().orEmpty().filter(File::isFile).sumOf(File::length)
                if (Host2RuntimeService.sizeBytes(id) + addedBytes > HOST2_QUOTA_BYTES) {
                    throw RequestError("添加后将超过8GiBquota")
                }
                database.transaction {
                    val locked = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
                    requireStoppedAndReady(locked)
                    val existingKeys = repository.mods(id)
                        .mapTo(mutableSetOf()) { it.mod.platform.lowercase() to it.mod.projectId }
                    val duplicate = selectedKeys.firstOrNull { it in existingKeys }
                    if (duplicate != null) throw RequestError("所选Mod项目已经存在")
                    val modsDir = hostDir(id).resolve("mods").apply { mkdirs() }
                    mods.forEach { mod ->
                        val source = stagingDir.resolve(mod.host2FileName)
                        val target = modsDir.resolve(mod.host2FileName)
                        if (target.exists() || disabledFile(id, mod).exists()) throw RequestError("Mod文件已经存在: ${mod.slug}")
                        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                        movedFiles += target
                        repository.insertMod(id, mod)
                    }
                }
            } catch (error: Throwable) {
                movedFiles.forEach { it.delete() }
                throw error
            } finally {
                stagingDir.takeIf(File::exists)?.deleteRecursivelyNoSymlink()
            }
        }

    suspend fun delete(player: RAccount, id: UUID, keys: List<Host2.ModKey>) =
        hostService.withMutation(id, Host2Operation.MOD_DEL) {
            requireMutable(player, id)
            if (keys.isEmpty()) throw RequestError("请选择要删除的Mod")
            val stagingDir = newStagingDir(id, "mod-del")
            val movedFiles = mutableListOf<Pair<File, File>>()
            try {
                database.transaction {
                    val locked = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
                    requireStoppedAndReady(locked)
                    val records = repository.mods(id).associateBy { it.mod.key }
                    keys.distinctBy { it.normalized() }.forEach { key ->
                        val record = records[key.normalized()] ?: throw RequestError("所选Mod不存在")
                        val source = existingFile(id, record.mod) ?: throw RequestError("Mod文件不存在: ${record.mod.slug}")
                        val staged = stagingDir.resolve(source.name)
                        Files.move(source.toPath(), staged.toPath(), StandardCopyOption.ATOMIC_MOVE)
                        movedFiles += staged to source
                        if (!repository.deleteMod(id, key)) throw RequestError("删除Mod记录失败")
                    }
                }
            } catch (error: Throwable) {
                movedFiles.asReversed().forEach { (staged, source) ->
                    if (staged.exists()) Files.move(staged.toPath(), source.toPath())
                }
                throw error
            } finally {
                stagingDir.takeIf(File::exists)?.deleteRecursivelyNoSymlink()
            }
        }

    suspend fun setEnabled(player: RAccount, id: UUID, dto: Host2.SetModsEnabledDto) =
        hostService.withMutation(id, Host2Operation.MOD_CHG) {
            requireMutable(player, id)
            if (dto.mods.isEmpty()) throw RequestError("请选择要修改的Mod")
            val changed = mutableListOf<Pair<File, File>>()
            try {
                database.transaction {
                    val locked = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
                    requireStoppedAndReady(locked)
                    val records = repository.mods(id).associateBy { it.mod.key }
                    dto.mods.distinctBy { it.normalized() }.forEach { key ->
                        val mod = records[key.normalized()]?.mod ?: throw RequestError("所选Mod不存在")
                        val source = if (dto.enabled) disabledFile(id, mod) else enabledFile(id, mod)
                        val target = if (dto.enabled) enabledFile(id, mod) else disabledFile(id, mod)
                        if (!source.exists()) {
                            if (target.exists()) return@forEach
                            throw RequestError("Mod文件不存在: ${mod.slug}")
                        }
                        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                        changed += target to source
                    }
                }
            } catch (error: Throwable) {
                changed.asReversed().forEach { (current, original) ->
                    if (current.exists()) Files.move(current.toPath(), original.toPath())
                }
                throw error
            }
        }

    private suspend fun requireMutable(player: RAccount, id: UUID): Host2Record =
        hostService.requireAdmin(player, id).also(::requireStoppedAndReady)

    private fun requireStoppedAndReady(host: Host2Record) {
        if (host.setupStatus != Host2SetupStatus.READY) throw RequestError("server pack尚未配置完成")
        if (Host2RuntimeService.status(host.id) != HostStatus.STOPPED) throw RequestError("请先停止新版房间")
    }

    private fun existingFile(id: UUID, mod: Mod): File? =
        enabledFile(id, mod).takeIf(File::exists) ?: disabledFile(id, mod).takeIf(File::exists)

    private fun enabledFile(id: UUID, mod: Mod) = hostDir(id).resolve("mods").resolve(mod.host2FileName)
    private fun disabledFile(id: UUID, mod: Mod) = File(enabledFile(id, mod).path + ".disabled")
    private fun hostDir(id: UUID) = HOST2_DIR.resolve(id.toString())

    private fun readModIdsInDirectory(directory: File): Set<String> = directory.listFiles().orEmpty()
        .filter { it.isFile && (it.name.endsWith(".jar", true) || it.name.endsWith(".jar.disabled", true)) }
        .flatMapTo(mutableSetOf(), ::readModIds)

    private fun readModIds(file: File): List<String> = runCatching {
        JarFile(file).use { jar -> ModService.run { jar.readModMeta()?.modIds.orEmpty() } }
    }.getOrElse { throw RequestError("无法读取Mod文件: ${file.name}") }

    private fun newStagingDir(id: UUID, kind: String) =
        HOST2_DIR.resolve(".staging").resolve("$id-$kind-${UUID.randomUUID()}").apply { mkdirs() }
}

private val Mod.key: Host2.ModKey
    get() = Host2.ModKey(platform.lowercase(), projectId)

private fun Host2.ModKey.normalized() = Host2.ModKey(platform.lowercase(), projectId)
