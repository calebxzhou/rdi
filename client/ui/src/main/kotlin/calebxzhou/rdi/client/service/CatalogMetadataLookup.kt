package calebxzhou.rdi.client.service

import calebxzau.rdi.client.lgr
import calebxzau.rdi.client.modcatalog.CatalogModMetadata
import calebxzau.rdi.client.modcatalog.CatalogSlugRef
import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzau.rdi.client.modcatalog.ModPlatform
import calebxzhou.rdi.common.model.Mod

internal suspend fun ModCatalog.getMetadataOrEmpty(
    refs: Set<CatalogSlugRef>
): Map<CatalogSlugRef, CatalogModMetadata> = getMetadata(refs).getOrElse { cause ->
    lgr.warn(cause) { "读取本地Mod目录Metadata失败" }
    emptyMap()
}

internal fun Mod.toCatalogSlugRef(): CatalogSlugRef? {
    val modPlatform = platform.toCatalogPlatform() ?: return null
    return slug.trim().takeIf(String::isNotBlank)?.let { CatalogSlugRef(modPlatform, it) }
}

internal fun String.toCatalogPlatform(): ModPlatform? = when (lowercase()) {
    "cf" -> ModPlatform.CURSEFORGE
    "mr" -> ModPlatform.MODRINTH
    else -> null
}
