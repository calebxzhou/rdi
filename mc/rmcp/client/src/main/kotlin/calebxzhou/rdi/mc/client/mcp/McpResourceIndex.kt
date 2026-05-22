package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.common2.mcp.model.ResourceMatch
import calebxzhou.rdi.mc.common2.mcp.model.ResourceResolveP
import java.util.concurrent.atomic.AtomicReference

object McpResourceIndex {
    private val ref = AtomicReference(ResourceIndex.EMPTY)

    val size get() = ref.get().size
    val refreshedAtMillis get() = ref.get().refreshedAtMillis

    fun refresh(entries: Iterable<McpResourceIndexEntry>) {
        ref.set(ResourceIndex.from(entries))
    }

    fun resolve(text: String, kinds: Set<String> = emptySet(), limit: Int = 10): ResourceResolveP {
        return ref.get().resolve(text, kinds, limit)
    }
}

data class McpResourceIndexEntry(
    val kind: String,
    val id: String,
    val name: String,
)

class ResourceIndex private constructor(
    private val entries: List<ResourceEntry>,
    val refreshedAtMillis: Long,
) {
    val size get() = entries.size

    fun resolve(text: String, kinds: Set<String>, limit: Int): ResourceResolveP {
        val query = normalizeSearchText(text)
        if (query.isEmpty() || limit <= 0) {
            return ResourceResolveP(emptyList())
        }
        val normalizedKinds = kinds.mapTo(mutableSetOf()) { it.lowercase() }
        val matches = entries
            .asSequence()
            .filter { normalizedKinds.isEmpty() || it.kind in normalizedKinds }
            .map { ResourceCandidate(it, score(it, query)) }
            .filter { it.score > 0.0 }
            .sortedWith(
                compareByDescending<ResourceCandidate> { it.score }
                    .thenBy { kindPriority(it.entry.kind) }
                    .thenBy { it.entry.id.length }
                    .thenBy { it.entry.id }
            )
            .take(limit)
            .map { ResourceMatch(it.entry.kind, it.entry.id, it.entry.name) }
            .toList()
        return ResourceResolveP(matches)
    }

    companion object {
        val EMPTY = ResourceIndex(emptyList(), 0)

        fun from(rawEntries: Iterable<McpResourceIndexEntry>): ResourceIndex {
            val entries = rawEntries
                .mapNotNull { raw ->
                    val kind = raw.kind.trim().lowercase()
                    val id = raw.id.trim()
                    val name = raw.name.trim()
                    val normalizedName = normalizeSearchText(name)
                    if (kind.isEmpty() || id.isEmpty() || name.isEmpty() || normalizedName.isEmpty()) {
                        return@mapNotNull null
                    }
                    ResourceEntry(kind, id, name, normalizedName)
                }
                .distinctBy { "${it.kind}:${it.id}" }
                .toList()
            return ResourceIndex(entries, System.currentTimeMillis())
        }

        private fun score(entry: ResourceEntry, query: String): Double {
            val nameScore = scoreName(query, entry.normalizedName)
            val idScore = scoreId(query, normalizeSearchText(entry.id))
            val baseScore = maxOf(nameScore, idScore)
            if (baseScore <= 0.0) {
                return 0.0
            }
            val namespaceBonus = if (entry.id.startsWith("minecraft:")) 0.0 else 2.0
            return baseScore + namespaceBonus
        }

        private fun scoreName(query: String, candidate: String): Double {
            return when {
                candidate == query -> 100.0
                candidate.startsWith(query) -> 80.0
                candidate.contains(query) -> 60.0
                query.length < 2 -> 0.0
                else -> fuzzySearchScore(query, candidate) * 50.0
            }
        }

        private fun scoreId(query: String, id: String): Double {
            val path = id.substringAfter(':')
            return when {
                id == query -> 90.0
                path == query -> 85.0
                id.startsWith(query) || path.startsWith(query) -> 65.0
                id.contains(query) || path.contains(query) -> 45.0
                query.length < 2 -> 0.0
                else -> fuzzySearchScore(query, id) * 30.0
            }
        }

        private fun kindPriority(kind: String): Int {
            return when (kind) {
                "block" -> 0
                "item" -> 1
                "block_tag" -> 2
                "item_tag" -> 3
                else -> 4
            }
        }

        private fun normalizeSearchText(text: String): String {
            val builder = StringBuilder()
            val lower = text.trim { it <= ' ' }.lowercase()
            var offset = 0
            while (offset < lower.length) {
                val codePoint = lower.codePointAt(offset)
                if (Character.isLetterOrDigit(codePoint) ||
                    codePoint == '_'.code ||
                    codePoint == ':'.code ||
                    codePoint == '.'.code
                ) {
                    builder.appendCodePoint(codePoint)
                }
                offset += Character.charCount(codePoint)
            }
            return builder.toString()
        }

        private fun fuzzySearchScore(query: String, candidate: String): Double {
            if (query.isEmpty() || candidate.isEmpty()) {
                return 0.0
            }
            var matched = 0
            var candidateIndex = 0
            for (i in query.indices) {
                val ch = query[i]
                while (candidateIndex < candidate.length && candidate[candidateIndex] != ch) {
                    candidateIndex++
                }
                if (candidateIndex >= candidate.length) {
                    continue
                }
                matched++
                candidateIndex++
            }
            if (matched < 2) {
                return 0.0
            }
            return matched.toDouble() / candidate.length
        }
    }

    private data class ResourceEntry(
        val kind: String,
        val id: String,
        val name: String,
        val normalizedName: String,
    )

    private data class ResourceCandidate(
        val entry: ResourceEntry,
        val score: Double,
    )
}
