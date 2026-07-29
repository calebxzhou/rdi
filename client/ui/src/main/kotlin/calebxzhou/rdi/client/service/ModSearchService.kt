package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.modcatalog.ModCatalog
import calebxzhou.rdi.client.modcatalog.ModPlatform
import calebxzhou.rdi.client.modcatalog.CatalogSlugRef
import calebxzhou.rdi.client.model.ModrinthProjectCardVo
import calebxzhou.rdi.client.model.ModrinthProjectSearchResult
import calebxzhou.rdi.client.model.RemoteModCardVo
import calebxzhou.rdi.client.model.RemoteModSearchResult
import calebxzhou.rdi.client.model.RemoteModSourceFilter
import calebxzhou.rdi.common.model.ModrinthSearchIndex
import calebxzhou.rdi.common.service.CurseForgeService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object ModSearchService {
    suspend fun searchMods(
        modCatalog: ModCatalog,
        query: String? = null,
        mcVersion: String? = null,
        loader: String? = null,
        index: ModrinthSearchIndex = ModrinthSearchIndex.RELEVANCE,
        sourceFilter: RemoteModSourceFilter = RemoteModSourceFilter.ALL,
        offset: Int = 0,
        modrinthOffset: Int = offset,
        curseForgeOffset: Int = offset,
        limit: Int = 20
    ): RemoteModSearchResult = coroutineScope {
        val loadModrinth = sourceFilter != RemoteModSourceFilter.CURSEFORGE
        val loadCurseForge = sourceFilter != RemoteModSourceFilter.MODRINTH
        val modrinth = async {
            if (loadModrinth) {
                runCatching { searchModrinth(modCatalog, query, mcVersion, loader, index, modrinthOffset, limit) }
            } else {
                Result.success(emptyModrinthResult(modrinthOffset, limit).toRemoteModSearchResult())
            }
        }
        val curseForge = async {
            if (loadCurseForge) {
                runCatching { searchCurseForge(modCatalog, query, mcVersion, loader, index, curseForgeOffset, limit) }
            } else {
                Result.success(emptyCurseForgeResult(curseForgeOffset, limit))
            }
        }

        val modrinthResult = modrinth.await()
        val curseForgeResult = curseForge.await()
        if (modrinthResult.isFailure && curseForgeResult.isFailure) {
            throw modrinthResult.exceptionOrNull() ?: curseForgeResult.exceptionOrNull()!!
        }
        val mr = modrinthResult.getOrElse { emptyModrinthResult(modrinthOffset, limit).toRemoteModSearchResult() }
        val cf = curseForgeResult.getOrElse { emptyCurseForgeResult(curseForgeOffset, limit) }
        val mergedMods = when (sourceFilter) {
            RemoteModSourceFilter.ALL -> mergeRemoteMods(mr.mods, cf.mods)
            RemoteModSourceFilter.MODRINTH -> mr.mods
            RemoteModSourceFilter.CURSEFORGE -> cf.mods
        }.localize(modCatalog)
        RemoteModSearchResult(
            mods = mergedMods,
            offset = offset,
            limit = limit,
            totalHits = mr.totalHits + cf.totalHits,
            nextModrinthOffset = mr.nextModrinthOffset,
            nextCurseForgeOffset = cf.nextCurseForgeOffset,
            hasMore = mr.hasMore || cf.hasMore
        )
    }

    private suspend fun searchModrinth(
        modCatalog: ModCatalog,
        query: String?,
        mcVersion: String?,
        loader: String?,
        index: ModrinthSearchIndex,
        offset: Int,
        limit: Int
    ): RemoteModSearchResult {
        val queryText = query?.trim().orEmpty()
        val localSlugs = modCatalog.resolveSlugsByChineseName(queryText, ModPlatform.MODRINTH)
        if (localSlugs.isEmpty()) {
            return ModrinthProjectSearchService.searchProjects(
                projectType = "mod",
                query = query,
                mcVersion = mcVersion,
                loader = loader,
                index = index,
                offset = offset,
                limit = limit
            ).toRemoteModSearchResult()
        }

        if (offset > 0) {
            return emptyModrinthResult(offset, limit).toRemoteModSearchResult()
        }

        val projects = mutableListOf<ModrinthProjectCardVo>()
        val seenProjectIds = mutableSetOf<String>()
        val seenSlugs = mutableSetOf<String>()
        localSlugs.forEach { slug ->
            ModrinthProjectSearchService.searchProjects(
                projectType = "mod",
                query = slug,
                mcVersion = mcVersion,
                loader = loader,
                index = index,
                offset = 0,
                limit = limit
            ).projects.forEach { project ->
                if (project.projectId !in seenProjectIds && project.slug !in seenSlugs) {
                    seenProjectIds += project.projectId
                    seenSlugs += project.slug
                    projects += project
                }
            }
        }
        val dedupedProjects = projects.take(limit)
        return ModrinthProjectSearchResult(
            projects = dedupedProjects,
            offset = 0,
            limit = limit,
            totalHits = dedupedProjects.size
        ).toRemoteModSearchResult()
    }

    private suspend fun searchCurseForge(
        modCatalog: ModCatalog,
        query: String?,
        mcVersion: String?,
        loader: String?,
        index: ModrinthSearchIndex,
        offset: Int,
        limit: Int
    ): RemoteModSearchResult {
        val queryText = query?.trim().orEmpty()
        val localSlugs = modCatalog.resolveSlugsByChineseName(queryText, ModPlatform.CURSEFORGE)
        if (localSlugs.isNotEmpty()) {
            if (offset > 0) {
                return emptyCurseForgeResult(offset, limit)
            }
            val mods = mutableListOf<RemoteModCardVo>()
            val seenProjectIds = mutableSetOf<String>()
            val seenSlugs = mutableSetOf<String>()
            localSlugs.forEach { slug ->
                CurseForgeService.searchMods(
                    query = slug,
                    mcVersion = mcVersion,
                    loader = loader,
                    sortField = index.toCurseForgeSortField(),
                    sortOrder = "desc",
                    offset = 0,
                    limit = limit.coerceIn(1, 50)
                ).data.filter { it.isMod }.forEach { project ->
                    val projectId = project.id.toString()
                    val normalizedSlug = project.slug.trim().lowercase()
                    if (projectId !in seenProjectIds && normalizedSlug !in seenSlugs) {
                        seenProjectIds += projectId
                        seenSlugs += normalizedSlug
                        mods += project.toRemoteModCardVo()
                    }
                }
            }
            val dedupedMods = mods.take(limit)
            return RemoteModSearchResult(
                mods = dedupedMods,
                offset = 0,
                limit = limit,
                totalHits = dedupedMods.size,
                nextCurseForgeOffset = dedupedMods.size,
                hasMore = false
            )
        }

        val response = CurseForgeService.searchMods(
            query = query,
            mcVersion = mcVersion,
            loader = loader,
            sortField = index.toCurseForgeSortField(),
            sortOrder = "desc",
            offset = offset,
            limit = limit.coerceIn(1, 50)
        )
        val pagination = response.pagination
        val resultCount = pagination?.resultCount ?: response.data.size
        val pageSize = pagination?.pageSize?.takeIf { it > 0 } ?: limit
        val nextOffset = (pagination?.index ?: offset) + resultCount
        val totalCount = pagination?.totalCount ?: response.data.size
        return RemoteModSearchResult(
            mods = response.data.filter { it.isMod }.map { it.toRemoteModCardVo() },
            offset = pagination?.index ?: offset,
            limit = pageSize,
            totalHits = totalCount,
            nextModrinthOffset = 0,
            nextCurseForgeOffset = nextOffset,
            hasMore = nextOffset < totalCount
        )
    }
}

private suspend fun ModCatalog.resolveSlugsByChineseName(query: String, platform: ModPlatform): List<String> {
    if (query.none { it in '\u4e00'..'\u9fff' }) return emptyList()
    return searchMetadata(query, 5).getOrElse { emptyList() }
        .mapNotNull { it.project(platform)?.slug }
        .distinct()
}

private suspend fun List<RemoteModCardVo>.localize(modCatalog: ModCatalog): List<RemoteModCardVo> {
    val refs = mapNotNull { mod ->
        val platform = when (mod.source) {
            calebxzhou.rdi.client.model.RemoteModSource.CURSEFORGE -> ModPlatform.CURSEFORGE
            calebxzhou.rdi.client.model.RemoteModSource.MODRINTH -> ModPlatform.MODRINTH
        }
        mod.slug?.takeIf(String::isNotBlank)?.let { CatalogSlugRef(platform, it) }
    }.toSet()
    val metadata = modCatalog.getMetadataOrEmpty(refs)
    return map { mod ->
        val platform = when (mod.source) {
            calebxzhou.rdi.client.model.RemoteModSource.CURSEFORGE -> ModPlatform.CURSEFORGE
            calebxzhou.rdi.client.model.RemoteModSource.MODRINTH -> ModPlatform.MODRINTH
        }
        val local = mod.slug?.let { metadata[CatalogSlugRef(platform, it)] } ?: return@map mod
        mod.copy(
            title = local.nameCn?.takeIf(String::isNotBlank) ?: local.name,
            summary = local.intro?.takeIf(String::isNotBlank) ?: mod.summary,
            iconUrl = mod.iconUrl ?: local.logoUrl
        )
    }
}

private fun emptyModrinthResult(offset: Int, limit: Int): ModrinthProjectSearchResult =
    ModrinthProjectSearchResult(
        projects = emptyList(),
        offset = offset,
        limit = limit,
        totalHits = 0
    )

private fun emptyCurseForgeResult(offset: Int, limit: Int): RemoteModSearchResult =
    RemoteModSearchResult(
        mods = emptyList(),
        offset = offset,
        limit = limit,
        totalHits = 0,
        nextCurseForgeOffset = offset
    )

private fun ModrinthSearchIndex.toCurseForgeSortField(): Int? =
    when (this) {
        ModrinthSearchIndex.RELEVANCE -> null
        ModrinthSearchIndex.DOWNLOADS -> 6
        ModrinthSearchIndex.FOLLOWS -> 2
        ModrinthSearchIndex.NEWEST -> 11
        ModrinthSearchIndex.UPDATED -> 3
    }

private fun mergeRemoteMods(
    modrinthMods: List<RemoteModCardVo>,
    curseForgeMods: List<RemoteModCardVo>
): List<RemoteModCardVo> {
    val seen = mutableSetOf<String>()
    fun RemoteModCardVo.keys(): List<String> = listOfNotNull(slug, title)
        .map { it.lowercase().filter(Char::isLetterOrDigit) }
        .filter(String::isNotBlank)
    return buildList {
        (modrinthMods + curseForgeMods).forEach { mod ->
            val keys = mod.keys()
            if (keys.none { it in seen }) {
                add(mod)
                seen += keys
            }
        }
    }
}
