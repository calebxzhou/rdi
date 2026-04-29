package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.service.ModrinthService

object RemoteModLocalization {
    fun titleByModrinthSlug(slug: String?, fallback: String): String {
        val briefInfo = slug.modrinthBriefInfo()
        return briefInfo?.nameCn?.takeIf(String::isNotBlank)
            ?: briefInfo?.name?.takeIf(String::isNotBlank)
            ?: fallback
    }

    fun introByModrinthSlug(slug: String?, fallback: String): String =
        slug.modrinthBriefInfo()?.intro?.takeIf(String::isNotBlank) ?: fallback

    private fun String?.modrinthBriefInfo() =
        this?.trim()?.lowercase()?.takeIf(String::isNotBlank)?.let(ModrinthService.slugBriefInfo::get)
}
