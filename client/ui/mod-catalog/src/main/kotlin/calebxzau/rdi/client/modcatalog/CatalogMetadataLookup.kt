package calebxzau.rdi.client.modcatalog

import calebxzau.rdi.common.logging.Loggers
import calebxzhou.rdi.common.model.Mod

val lgr by Loggers
suspend fun ModCatalog.getMetadataOrEmpty(
    refs: Set<CatalogSlugRef>
): Map<CatalogSlugRef, CatalogModMetadata> = getMetadata(refs).getOrElse { cause ->
    lgr.warn(cause) { "读取本地Mod目录Metadata失败" }
    emptyMap()
}

fun Mod.toCatalogSlugRef(): CatalogSlugRef? {
    val modPlatform = platform.toCatalogPlatform() ?: return null
    return slug.trim().takeIf(String::isNotBlank)?.let { CatalogSlugRef(modPlatform, it) }
}

internal fun String.toCatalogPlatform(): ModPlatform? = when (lowercase()) {
    "cf" -> ModPlatform.CURSEFORGE
    "mr" -> ModPlatform.MODRINTH
    else -> null
}
