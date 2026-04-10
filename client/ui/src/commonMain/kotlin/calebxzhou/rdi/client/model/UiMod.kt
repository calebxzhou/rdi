package calebxzhou.rdi.client.model

import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.displaySlugOrProject
import java.io.File

data class UiMod(
    val mod: Mod,
    val card: Mod.CardVo? = null,
    val file: File? = null
) {
    val key: String
        get() = "${mod.platform}:${mod.projectId}:${mod.fileId}"

    val platform: String
        get() = mod.platform

    val projectId: String
        get() = mod.projectId

    val slug: String
        get() = mod.slug

    val fileId: String
        get() = mod.fileId

    val hash: String
        get() = mod.hash

    val side: Mod.Side
        get() = mod.side

    val displayName: String
        get() = card?.name?.takeIf { it.isNotBlank() } ?: mod.displaySlugOrProject

    val displayNameCn: String?
        get() = card?.nameCn?.takeIf { it.isNotBlank() }

    val searchText: String
        get() = buildString {
            append(displayName)
            append('\n')
            append(displayNameCn.orEmpty())
            append('\n')
            append(slug)
            append('\n')
            append(projectId)
        }.lowercase()

    fun withMod(mod: Mod): UiMod = copy(mod = mod)

    fun withFile(file: File?): UiMod = copy(file = file)

    fun withSide(side: Mod.Side): UiMod = copy(
        mod = mod.copy(side = side),
        card = card?.copy(side = side)
    )

    fun toMod(): Mod = mod.copy(
        platform = mod.platform,
        projectId = mod.projectId,
        slug = mod.slug,
        fileId = mod.fileId,
        hash = mod.hash,
        side = mod.side,
        downloadUrls = mod.downloadUrls.toList()
    ).also {
        it.vo = card
        it.file = file
    }
}

fun Mod.toUiMod(): UiMod = UiMod(
    mod = this,
    card = vo,
    file = file
)
