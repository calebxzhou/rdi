package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.model.ModrinthProjectSearchResult
import calebxzhou.rdi.common.model.ModrinthSearchIndex

object ModSearchService {
    suspend fun searchMods(
        query: String? = null,
        mcVersion: String? = null,
        loader: String? = null,
        index: ModrinthSearchIndex = ModrinthSearchIndex.RELEVANCE,
        offset: Int = 0,
        limit: Int = 20
    ): ModrinthProjectSearchResult =
        ModrinthProjectSearchService.searchProjects(
            projectType = "mod",
            query = query,
            mcVersion = mcVersion,
            loader = loader,
            index = index,
            offset = offset,
            limit = limit
        )
}
