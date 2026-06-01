package calebxzhou.rdi.common.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * calebxzhou @ 2026-05-11 16:14
 */
object TextTool {
    private const val MAX_READ_LINES = 500
    private const val MAX_SEARCH_MATCHES = 200
    private const val MAX_TEXT_FILE_BYTES = 1024 * 1024
    private const val MAX_OUTPUT_CHARS = 32 * 1024
    private val binaryExtensions = setOf(
        "jar", "zip", "7z", "rar", "tar", "gz", "zst", "png", "jpg", "jpeg", "webp", "gif", "ico", "class", "dll", "exe", "so", "dylib"
    )
    suspend fun list(root: File, rawPath: String, recursive: Boolean, limit: Int): Result<TextToolListResult> = withContext(Dispatchers.IO) { runCatching {
        val base = root.canonicalFile
        val target = resolveChild(base, rawPath)
        require(target.isDirectory) { "只能列目录: ${target.absolutePath}" }
        val max = limit.coerceIn(1, 1000)
        val files = (if (recursive) target.walkTopDown().drop(1) else target.listFiles().orEmpty().asSequence())
            .filter { it.canonicalFile.toPath().startsWith(base.toPath()) }
            .take(max)
            .map { file ->
                val rel = file.relativeTo(base).invariantSeparatorsPath
                val suffix = when {
                    file.isDirectory -> "/"
                    file.isFile -> " ${file.length()}B"
                    else -> ""
                }
                rel + suffix
            }
            .toList()
        TextToolListResult(
            target = target.relativeToOrSelf(base).invariantSeparatorsPath.ifBlank { base.name },
            output = files.joinToString("\n"),
            resultCount = files.size,
            truncated = files.size == max
        )
    } }

    suspend fun read(root: File, rawPath: String, startLine: Int, maxLines: Int): Result<TextToolReadResult> = withContext(Dispatchers.IO) { runCatching {
        val base = root.canonicalFile
        val target = resolveChild(base, rawPath)
        require(target.isFile) { "只能读取文件: ${target.absolutePath}" }
        require(isTextFile(target)) { "只能读取文本文件: ${target.name}" }
        require(target.length() <= MAX_TEXT_FILE_BYTES) { "文件超过1MB，请用search缩小范围" }
        val firstLine = startLine.coerceAtLeast(1)
        val lineLimit = maxLines.coerceIn(1, MAX_READ_LINES)
        val lines = mutableListOf<TextToolLine>()
        var currentLine = 0
        target.bufferedReader(Charsets.UTF_8).useLines { sequence ->
            sequence.forEach { text ->
                currentLine++
                if (currentLine >= firstLine && lines.size < lineLimit) {
                    lines += TextToolLine(currentLine, text)
                }
            }
        }
        val output = lines.joinToString("\n") { "${it.line}: ${it.text}" }.limitOutput()
        TextToolReadResult(
            target = target.relativeTo(base).invariantSeparatorsPath,
            startLine = firstLine,
            endLine = lines.lastOrNull()?.line ?: firstLine,
            output = output,
            outputBytes = target.length(),
            resultCount = lines.size,
            truncated = lines.size == lineLimit || output.length >= MAX_OUTPUT_CHARS
        )
    } }

    suspend fun search(
        root: File,
        rawPath: String,
        pattern: String,
        glob: String?,
        maxMatches: Int,
        contextLines: Int
    ): Result<TextToolSearchResult> = withContext(Dispatchers.IO) { runCatching {
        val base = root.canonicalFile
        val target = resolveChild(base, rawPath)
        require(pattern.isNotBlank()) { "搜索pattern不能为空" }
        val regex = Regex(pattern)
        val globRegex = glob?.trim()?.takeIf(String::isNotBlank)?.toGlobRegex()
        val matchLimit = maxMatches.coerceIn(1, MAX_SEARCH_MATCHES)
        val context = contextLines.coerceIn(0, 5)
        val files = when {
            target.isFile -> sequenceOf(target)
            target.isDirectory -> target.walkTopDown().filter(File::isFile)
            else -> emptySequence()
        }
        val matches = mutableListOf<TextToolMatch>()
        for (file in files) {
            val canonical = file.canonicalFile
            if (!canonical.toPath().startsWith(base.toPath())) continue
            if (!isTextFile(canonical) || canonical.length() > MAX_TEXT_FILE_BYTES) continue
            val relativePath = canonical.relativeTo(base).invariantSeparatorsPath
            if (globRegex != null && !globRegex.matches(relativePath)) continue
            searchFile(canonical, relativePath, regex, context, matchLimit - matches.size, matches)
            if (matches.size >= matchLimit) break
        }
        TextToolSearchResult(
            target = target.relativeToOrSelf(base).invariantSeparatorsPath.ifBlank { base.name },
            output = matches.joinToString("\n") { match ->
                buildString {
                    append(match.path).append(':').append(match.line).append(": ").append(match.text)
                    if (match.before.isNotEmpty()) append("\n  before: ").append(match.before.joinToString(" | "))
                    if (match.after.isNotEmpty()) append("\n  after: ").append(match.after.joinToString(" | "))
                }
            }.limitOutput(),
            resultCount = matches.size,
            truncated = matches.size == matchLimit
        )
    } }

    private fun searchFile(
        file: File,
        relativePath: String,
        regex: Regex,
        contextLines: Int,
        limit: Int,
        output: MutableList<TextToolMatch>
    ) {
        if (limit <= 0) return
        val initialSize = output.size
        val previous = ArrayDeque<String>()
        var pendingAfter = 0
        var lineNumber = 0
        var lastMatch: TextToolMatch? = null
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                lineNumber++
                if (pendingAfter > 0 && lastMatch != null) {
                    lastMatch.after += "${lineNumber}: $line"
                    pendingAfter--
                }
                if (regex.containsMatchIn(line)) {
                    val match = TextToolMatch(
                        path = relativePath,
                        line = lineNumber,
                        text = line,
                        before = previous.toList().toMutableList(),
                        after = mutableListOf()
                    )
                    output += match
                    lastMatch = match
                    pendingAfter = contextLines
                    if (output.size - initialSize >= limit) return
                }
                previous += "${lineNumber}: $line"
                while (previous.size > contextLines) previous.removeFirst()
            }
        }
    }

    private fun resolveChild(root: File, rawPath: String): File {
        val trimmed = rawPath.trim()
        if (trimmed.isBlank() || trimmed == ".") return root
        val raw = File(trimmed)
        val target = (if (raw.isAbsolute) raw else root.resolve(trimmed)).canonicalFile
        //require(target.toPath().startsWith(root.toPath())) { "路径不在RDI目录内: $rawPath" }
        return target
    }

    private fun isTextFile(file: File): Boolean {
        if (!file.isFile) return false
        if (file.extension.lowercase() in binaryExtensions) return false
        return true
    }

    private fun String.limitOutput(): String =
        if (length <= MAX_OUTPUT_CHARS) this else take(MAX_OUTPUT_CHARS)

    private fun String.toGlobRegex(): Regex {
        val pattern = buildString {
            append("^")
            this@toGlobRegex.replace('\\', '/').forEach { ch ->
                when (ch) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']' -> append('\\').append(ch)
                    else -> append(ch)
                }
            }
            append("$")
        }
        return Regex(pattern)
    }
}

data class TextToolListResult(
    val target: String,
    val output: String,
    val resultCount: Int,
    val truncated: Boolean
)

data class TextToolReadResult(
    val target: String,
    val startLine: Int,
    val endLine: Int,
    val output: String,
    val outputBytes: Long,
    val resultCount: Int,
    val truncated: Boolean
)

data class TextToolSearchResult(
    val target: String,
    val output: String,
    val resultCount: Int,
    val truncated: Boolean
)

data class TextToolLine(
    val line: Int,
    val text: String
)

data class TextToolMatch(
    val path: String,
    val line: Int,
    val text: String,
    val before: MutableList<String>,
    val after: MutableList<String>
)
