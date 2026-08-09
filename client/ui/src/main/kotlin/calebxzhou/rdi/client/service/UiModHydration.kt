package calebxzhou.rdi.client.service

import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzau.rdi.client.lgr
import calebxzhou.rdi.client.model.UiMod
import calebxzhou.rdi.client.model.toUiMod
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModrinthProject
import calebxzhou.rdi.common.service.ModService.buildIconUrls
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.jvm.JvmName

fun List<Mod>.toUiMods(): List<UiMod> = map { it.toUiMod() }

private val uiModResolvers: List<ModCardResolver> = listOf(
    CurseForgeCardResolver,
    ModrinthCardResolver
)

suspend fun List<Mod>.hydrateToUiMods(
    modCatalog: ModCatalog,
    modrinthProjects: List<ModrinthProject>? = null
): List<UiMod> = hydrateToUiModsInternal(
    source = toUiMods(),
    modCatalog = modCatalog,
    modrinthProjects = modrinthProjects
)

@JvmName("hydrateExistingUiMods")
suspend fun List<UiMod>.hydrateToUiMods(
    modCatalog: ModCatalog,
    modrinthProjects: List<ModrinthProject>? = null
): List<UiMod> = hydrateToUiModsInternal(
    source = this,
    modCatalog = modCatalog,
    modrinthProjects = modrinthProjects
)

private suspend fun hydrateToUiModsInternal(
    source: List<UiMod>,
    modCatalog: ModCatalog,
    modrinthProjects: List<ModrinthProject>?
): List<UiMod> = coroutineScope {
    val resolveContext = ModCardResolveContext(modCatalog, modrinthProjects)
    val localCardMap = LocalModCardResolver.resolve(source, resolveContext)
    val remoteCardMap = uiModResolvers
        .map { resolver ->
            async { resolver.resolve(source, resolveContext) }
        }
        .awaitAll()
        .fold(mutableMapOf<String, Mod.CardVo>()) { acc, resolved ->
            acc.apply { putAll(resolved) }
        }
    val cardMap = LinkedHashMap<String, Mod.CardVo>().apply {
        source.mapNotNull { uiMod ->
            uiMod.card?.let { uiMod.mod.projectKey() to it }
        }.forEach { (key, card) -> put(key, card) }
        putAll(localCardMap)
        remoteCardMap.forEach { (key, remoteCard) ->
            this[key] = localCardMap[key]?.mergeWithRemote(remoteCard) ?: remoteCard
        }
    }

    return@coroutineScope source.withCards(cardMap)
}

suspend fun List<Mod>.hydrateToUiModsInBatches(
    modCatalog: ModCatalog,
    modrinthProjects: List<ModrinthProject>? = null,
    onBatch: suspend (List<UiMod>) -> Unit
): List<UiMod> = supervisorScope {
    val hydratedUiMods = toUiMods()
    val resolveContext = ModCardResolveContext(modCatalog, modrinthProjects)
    val localCardMap = LocalModCardResolver.resolve(hydratedUiMods, resolveContext)
    val cardMap = LinkedHashMap<String, Mod.CardVo>().apply {
        putAll(localCardMap)
    }

    onBatch(hydratedUiMods.withCards(cardMap))

    val results = Channel<RemoteCardResolution>(capacity = uiModResolvers.size)
    val jobs = uiModResolvers.map { resolver ->
        launch {
            val result = try {
                Result.success(resolver.resolve(hydratedUiMods, resolveContext))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (cause: Throwable) {
                Result.failure(cause)
            }
            results.send(RemoteCardResolution(resolver, result))
        }
    }

    repeat(jobs.size) {
        val resolution = results.receive()
        resolution.result
            .onSuccess { remoteCardMap ->
                remoteCardMap.forEach { (key, remoteCard) ->
                    cardMap[key] = localCardMap[key]?.mergeWithRemote(remoteCard) ?: remoteCard
                }
                onBatch(hydratedUiMods.withCards(cardMap))
            }
            .onFailure { cause ->
                lgr.warn(cause) {
                    "补充${resolution.resolver.platform} Mod信息失败，保留当前卡片"
                }
            }
    }

    jobs.joinAll()
    results.close()
    hydratedUiMods.withCards(cardMap)
}

private data class RemoteCardResolution(
    val resolver: ModCardResolver,
    val result: Result<Map<String, Mod.CardVo>>
)

private fun List<UiMod>.withCards(cardMap: Map<String, Mod.CardVo>): List<UiMod> = map { uiMod ->
    uiMod.copy(
        card = cardMap[uiMod.mod.projectKey()]?.copy(side = uiMod.side)
            ?: uiMod.card?.copy(side = uiMod.side)
    )
}

private fun Mod.CardVo.mergeWithRemote(remote: Mod.CardVo): Mod.CardVo = remote.copy(
    iconData = iconData ?: remote.iconData,
    iconUrls = buildIconUrls(remote.iconUrls + iconUrls),
    side = side
)
