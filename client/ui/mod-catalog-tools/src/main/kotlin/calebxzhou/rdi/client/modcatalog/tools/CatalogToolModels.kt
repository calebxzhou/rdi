package calebxzhou.rdi.client.modcatalog.tools

import calebxzhou.rdi.client.modcatalog.ModPlatform
import kotlinx.serialization.Serializable

@Serializable
internal data class CatalogToolMod(
    val mcmodId: Int,
    val popularity: Int,
    val name: String,
    val nameCn: String?,
    val intro: String?,
    val logoUrl: String?,
    val projects: List<CatalogToolProject>
)

@Serializable
internal data class CatalogToolProject(
    val order: Int,
    val platform: ModPlatform,
    val slug: String,
    val nameCnOverride: String?
)

internal data class WikiPage(
    val mcmodId: Int,
    val popularity: Int,
    val aliases: List<WikiAlias>
)

internal data class WikiAlias(
    val curseForgeSlug: String?,
    val modrinthSlug: String?,
    val nameCn: String?
)

@Serializable
internal data class McmodItem(
    val mcmodId: Int,
    val logoUrl: String,
    val name: String,
    val nameCn: String? = null,
    val intro: String
)

internal data class McmodPage(
    val currentPage: Int,
    val totalPages: Int,
    val totalItems: Int,
    val items: List<McmodItem>
)
