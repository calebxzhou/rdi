package calebxzhou.rdi.client.service

import calebxzau.rdi.client.lgr
import calebxzhou.rdi.client.model.UiMod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

private const val MAX_ICON_RESOLUTIONS = 4

private val iconResolutionSlots = Semaphore(MAX_ICON_RESOLUTIONS)
private val analyzedIconSources = ConcurrentHashMap<ModIconSourceKey, ModIconColorSortKey>()

/** Resolves and analyzes every mod in one snapshot with bounded parallelism. */
suspend fun analyzeModIconColors(
    mods: List<UiMod>
): Result<Map<String, ModIconColorSortKey>> {
    return try {
        val analyzed = coroutineScope {
            mods.map { mod ->
                async {
                    val color = iconResolutionSlots.withPermit {
                        withContext(Dispatchers.Default) {
                            analyzeModIconColor(mod)
                        }
                    }
                    color
                        .onFailure { error ->
                            lgr.warn(error) { "Mod图标颜色分析失败: ${mod.primaryName}" }
                        }
                        .getOrElse { MISSING_MOD_ICON_COLOR_SORT_KEY }
                        .let { mod.key to it }
                }
            }.awaitAll()
        }
        Result.success(analyzed.toMap())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
}

private suspend fun analyzeModIconColor(mod: UiMod): Result<ModIconColorSortKey> {
    val source = ModIconSourceKey.from(mod.iconData, mod.iconUrls)
    analyzedIconSources[source]?.let { return Result.success(it) }

    return resolveLocalFirstModIcon(mod.iconData, mod.iconUrls)
        .fold(
            onSuccess = { bitmap ->
                classifyModIconColor(bitmap).onSuccess { color ->
                    analyzedIconSources.putIfAbsent(source, color)
                }
            },
            onFailure = { error -> Result.failure(error) }
        )
}

private data class ModIconSourceKey(
    val localData: IconDataKey?,
    val remoteUrls: List<String>
) {
    companion object {
        fun from(iconData: ByteArray?, iconUrls: List<String>): ModIconSourceKey = ModIconSourceKey(
            localData = iconData?.let(::IconDataKey),
            remoteUrls = normalizeModIconUrls(iconUrls)
        )
    }
}

private class IconDataKey(data: ByteArray) {
    private val digest = MessageDigest.getInstance("SHA-256").digest(data)
    private val hash = digest.contentHashCode()

    override fun equals(other: Any?): Boolean = other is IconDataKey &&
        digest.contentEquals(other.digest)

    override fun hashCode(): Int = hash
}
