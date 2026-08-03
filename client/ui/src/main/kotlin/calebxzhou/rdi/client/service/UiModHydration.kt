package calebxzhou.rdi.client.service

import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzhou.rdi.client.model.UiMod
import calebxzhou.rdi.client.model.toUiMod
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModrinthProject
import calebxzhou.rdi.common.service.ModService.buildIconUrls
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

fun List<Mod>.toUiMods(): List<UiMod> = map(Mod::toUiMod)

private val uiModResolvers: List<ModCardResolver> = listOf(
    CurseForgeCardResolver,
    ModrinthCardResolver
)

suspend fun List<Mod>.hydrateToUiMods(
    modCatalog: ModCatalog,
    modrinthProjects: List<ModrinthProject>? = null
): List<UiMod> = coroutineScope {
    val hydratedMods = map(Mod::copyForUiHydration)
    val resolveContext = ModCardResolveContext(modCatalog, modrinthProjects)
    val localCardMap = LocalModCardResolver.resolve(hydratedMods, resolveContext)
    val remoteCardMap = uiModResolvers
        .map { resolver ->
            async { resolver.resolve(hydratedMods, resolveContext) }
        }
        .awaitAll()
        .fold(mutableMapOf<String, Mod.CardVo>()) { acc, resolved ->
            acc.apply { putAll(resolved) }
        }
    val cardMap = LinkedHashMap<String, Mod.CardVo>().apply {
        putAll(localCardMap)
        remoteCardMap.forEach { (key, remoteCard) ->
            this[key] = localCardMap[key]?.mergeWithRemote(remoteCard) ?: remoteCard
        }
    }

    return@coroutineScope hydratedMods.map { mod ->
        UiMod(
            mod = mod,
            card = cardMap[mod.projectKey()]?.copy(side = mod.side),
            file = mod.file
        )
    }
}

private fun Mod.copyForUiHydration(): Mod = copy(
    platform = platform,
    projectId = projectId,
    slug = slug,
    fileId = fileId,
    hash = hash,
    side = side,
    downloadUrls = downloadUrls.toList()
).also { copied ->
    copied.vo = vo?.copy(side = side)
    copied.file = file
}

private fun Mod.CardVo.mergeWithRemote(remote: Mod.CardVo): Mod.CardVo = remote.copy(
    iconData = iconData ?: remote.iconData,
    iconUrls = buildIconUrls(remote.iconUrls + iconUrls),
    side = side
)
