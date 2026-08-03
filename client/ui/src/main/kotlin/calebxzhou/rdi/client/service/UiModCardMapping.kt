package calebxzhou.rdi.client.service

import calebxzau.rdi.client.modcatalog.CatalogModMetadata
import calebxzhou.rdi.common.model.CurseForgeModInfo
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModrinthProject
import calebxzhou.rdi.common.service.ModService.buildIconUrls
import calebxzhou.rdi.common.service.ModService.modLogo
import calebxzhou.rdi.common.service.ModService.readModMeta
import java.io.File
import java.util.jar.JarFile

internal fun Mod.toLocalCardVo(metadata: CatalogModMetadata?): Mod.CardVo? {
    val localFile = file ?: targetPath.toFile().takeIf { it.exists() }
    val localMeta = localFile?.let {
        runCatching { it.readLocalModCardMeta() }.getOrNull()
    }
    val iconBytes = localMeta?.iconBytes
    val introText = localMeta?.introText
    if (metadata == null && iconBytes == null && introText == null) return null

    return metadata?.toUiCardVo(localMeta, side)?.copy(
        intro = metadata.intro?.takeIf(String::isNotBlank) ?: introText ?: "暂无介绍"
    ) ?: Mod.CardVo(
        name = slug.ifBlank { projectId },
        intro = introText ?: "暂无介绍",
        iconData = iconBytes,
        side = side
    )
}

internal fun CurseForgeModInfo.toUiCardVo(metadata: CatalogModMetadata?, modFile: File? = null): Mod.CardVo {
    val icons = buildIconUrls(logo?.thumbnailUrl, logo?.url, metadata?.logoUrl)
    val resolvedName = name.ifBlank { slug }
    val localMeta = modFile?.let {
        runCatching { it.readLocalModCardMeta() }.getOrNull()
    }
    val iconBytes = localMeta?.iconBytes
    val introText = summary?.takeIf { it.isNotBlank() }?.trim()
        ?: localMeta?.introText
        ?: "暂无介绍"

    return metadata?.toUiCardVo(localMeta)?.copy(
        intro = metadata.intro?.takeIf(String::isNotBlank) ?: introText,
        iconUrls = icons
    ) ?: Mod.CardVo(
        name = resolvedName,
        nameCn = null,
        intro = introText,
        iconData = iconBytes,
        iconUrls = icons,
        side = Mod.Side.BOTH
    )
}

internal fun ModrinthProject.toUiCardVo(metadata: CatalogModMetadata?, modFile: File? = null): Mod.CardVo {
    val icons = buildIconUrls(iconUrl, metadata?.logoUrl)
    val resolvedName = title.ifBlank { slug }
    val localMeta = modFile?.let {
        runCatching { it.readLocalModCardMeta() }.getOrNull()
    }
    val iconBytes = localMeta?.iconBytes
    val introText = description?.takeIf { it.isNotBlank() }?.trim()
        ?: localMeta?.introText
        ?: "暂无介绍"

    return metadata?.toUiCardVo(localMeta)?.copy(
        intro = metadata.intro?.takeIf(String::isNotBlank) ?: introText,
        iconUrls = icons
    ) ?: Mod.CardVo(
        name = resolvedName,
        nameCn = null,
        intro = introText,
        iconData = iconBytes,
        iconUrls = icons,
        side = Mod.Side.BOTH
    )
}

private data class LocalModCardMeta(
    val iconBytes: ByteArray? = null,
    val introText: String? = null
)

private fun File.readLocalModCardMeta(): LocalModCardMeta = JarFile(this).use { jar ->
    LocalModCardMeta(
        iconBytes = jar.modLogo,
        introText = jar.readModMeta()?.description?.takeIf { it.isNotBlank() }
    )
}

private fun CatalogModMetadata.toUiCardVo(
    localMeta: LocalModCardMeta? = null,
    side: Mod.Side = Mod.Side.BOTH
): Mod.CardVo = Mod.CardVo(
    name = name,
    nameCn = nameCn,
    intro = intro.orEmpty(),
    iconData = localMeta?.iconBytes,
    iconUrls = buildIconUrls(logoUrl),
    side = side
)
