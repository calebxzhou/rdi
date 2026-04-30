package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.model.ModrinthProjectSearchResult
import calebxzhou.rdi.client.model.ModrinthProjectCardVo
import calebxzhou.rdi.common.model.ModrinthSearchIndex
import calebxzhou.rdi.common.service.ModService

object ModSearchService {
    suspend fun searchMods(
        query: String? = null,
        mcVersion: String? = null,
        loader: String? = null,
        index: ModrinthSearchIndex = ModrinthSearchIndex.RELEVANCE,
        offset: Int = 0,
        limit: Int = 20
    ): ModrinthProjectSearchResult {
        val queryText = query?.trim().orEmpty()
        val localSlugs = ModService.resolveModrinthSlugsByChineseName(queryText, maxResults = 5)
        if (localSlugs.isEmpty()) {
            return ModrinthProjectSearchService.searchProjects(
                projectType = "mod",
                query = query,
                mcVersion = mcVersion,
                loader = loader,
                index = index,
                offset = offset,
                limit = limit
            )
        }

        if (offset > 0) {
            return ModrinthProjectSearchResult(
                projects = emptyList(),
                offset = offset,
                limit = limit,
                totalHits = 0
            )
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
        )
    }
}
