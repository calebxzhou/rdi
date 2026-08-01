package calebxzhou.rdi.master.service.host

import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.HostStatus
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.master.service.ModpackService
import calebxzhou.rdi.master.service.ModpackService.toBriefVo
import calebxzhou.rdi.master.service.host.HostControlService.status
import calebxzhou.rdi.master.service.host.HostPresenceService.getOnlinePlayers
import calebxzhou.rdi.model.Role
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Sorts
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId

object HostQueryService {
    private val dbcl get() = HostService.dbcl

    suspend fun getById(id: ObjectId): Host? = dbcl.find(eq("_id", id)).firstOrNull()

    suspend fun getByPort(port: Int): Host? = dbcl.find(eq("port", port)).firstOrNull()

    suspend fun getByOwner(uid: ObjectId): List<Host> =
        dbcl.find(eq("ownerId", uid)).toList()

    suspend fun findByWorld(worldId: ObjectId): Host? =
        dbcl.find(eq("worldId", worldId)).firstOrNull()

    suspend fun findByModpack(modpackId: ObjectId): List<Host> {
        return dbcl.find(eq("modpackId", modpackId)).toList()
    }

    suspend fun findByModpackVersion(modpackId: ObjectId, verName: String): List<Host> {
        return dbcl.find(
            and(
                eq("modpackId", modpackId),
                eq("packVer", verName)
            )
        ).toList()
    }

    suspend fun findByOwnerAndModpack(uid: ObjectId, modpackId: ObjectId): Host? =
        dbcl.find(
            and(
                eq("ownerId", uid),
                eq("modpackId", modpackId)
            )
        ).firstOrNull()

    suspend fun RAccount.ownHosts() = getByOwner(_id)

    suspend fun RAccount.getBriefHost(id: ObjectId): Host.BriefVo? =
        getById(id)?.toBriefVo(_id)

    suspend fun RAccount.listAllHosts(
        page: Int,
        myOnly: Boolean,
        pageSize: Int = HostService.HOSTS_PER_PAGE
    ): List<Host.BriefVo> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = pageSize.coerceIn(1, 100)
        val hosts = dbcl.find()
            .sort(Sorts.descending("_id"))
            .skip(safePage * safeSize)
            .limit(safeSize)
            .toList()

        if (hosts.isEmpty()) return emptyList()

        val requesterId = _id
        val (memberHosts, otherHosts) = hosts.partition { host ->
            host.ownerId == requesterId ||
                    host.members.any { it.id == requesterId }
        }
        val visibleHosts = if (myOnly) {
            memberHosts + otherHosts.filter { it.isPublic }
        } else {
            memberHosts + otherHosts
        }

        return coroutineScope {
            visibleHosts.map { host ->
                async {
                    host.toBriefVo(requesterId)
                }
            }.awaitAll()
        }
    }

    private suspend fun Host.toBriefVo(requesterId: ObjectId): Host.BriefVo {
        val modpack = ModpackService.getById(modpackId)
        val onlinePlayers = getOnlinePlayers()
        val isMember = ownerId == requesterId || members.any { it.id == requesterId }
        val role = if (ownerId == requesterId) {
            Role.OWNER
        } else {
            members.firstOrNull { it.id == requesterId }?.role
        }
        val playable = when {
            isMember -> true
            isPublic -> true
            status == HostStatus.PLAYABLE && !whitelist -> true
            else -> false
        }
        return Host.BriefVo(
            _id = _id,
            intro = intro,
            name = name,
            ownerId = ownerId,
            modpackName = modpack?.name ?: "未知整合包",
            iconUrl = modpack?.iconUrl,
            packVer = packVer,
            port = port,
            playable = playable,
            isMember = isMember,
            role = role,
            onlinePlayerIds = onlinePlayers
        )
    }

    suspend fun Host.toDetailVo(): Host.DetailVo {
        val modpack = ModpackService.getById(modpackId)
        val modpackVo = modpack?.toBriefVo()
            ?: Modpack.BriefVo(id = modpackId, name = "未知整合包")
        val onlinePlayers = runCatching { getOnlinePlayers() }.getOrElse { emptyList() }
        return Host.DetailVo(
            _id = _id,
            name = name,
            intro = intro,
            iconUrl = modpackVo.icon,
            ownerId = ownerId,
            modpack = modpackVo,
            packVer = packVer,
            worldId = worldId,
            port = port,
            difficulty = difficulty,
            gameMode = gameMode,
            levelType = levelType,
            gameRules = gameRules,
            whitelist = whitelist,
            allowCheats = allowCheats,
            members = members,
            extraMods = extraMods,
            disabledMods = disabledMods,
            onlinePlayerIds = onlinePlayers
        )
    }
}
