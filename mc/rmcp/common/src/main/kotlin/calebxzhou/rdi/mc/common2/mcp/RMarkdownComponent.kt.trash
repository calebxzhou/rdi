package calebxzhou.rdi.mc.common2.mcp

object RMarkdownComponent {
    private val COLORS = setOf(
        "black",
        "dark_blue",
        "dark_green",
        "dark_aqua",
        "dark_red",
        "dark_purple",
        "gold",
        "gray",
        "dark_gray",
        "blue",
        "green",
        "aqua",
        "red",
        "light_purple",
        "yellow",
        "white"
    )
    private val MARKERS = charArrayOf('*', '_', '`', '[', '{', '~')
    private val EMPHASIS_SPANS = listOf(
        InlineSpan("**") { it.withBold() },
        InlineSpan("__") { it.withBold() },
        InlineSpan("~~") { it.withStrikethrough() }
    )

    fun <T> parse(markdown: String, adapter: ComponentAdapter<T>): T {
        val root = adapter.empty()
        parseSegments(markdown).forEach { segment ->
            adapter.append(root, adapter.literal(segment.text, segment.style))
        }
        return root
    }

    fun <T> parseLine(markdown: String, adapter: ComponentAdapter<T>): T =
        parse(markdown.orEmpty().replace("\r", "").replace("\n", " "), adapter)

    fun <T> parseLines(markdown: String, adapter: ComponentAdapter<T>): MutableList<T> =
        markdown.normalizedLines().mapTo(ArrayList()) { parseLine(it, adapter) }

    fun <T> parseSignLines(markdown: String, adapter: ComponentAdapter<T>): MutableList<T> {
        val lines = markdown.normalizedLines()
        return lines.take(lines.size.coerceIn(1, 4)).mapTo(ArrayList()) { parseLine(it, adapter) }
    }

    fun parseSegments(markdown: String?): MutableList<Segment> {
        val lines = markdown.normalizedLines()
        val segments = ArrayList<Segment>()
        var codeBlock = false

        lines.forEachIndexed { index, line ->
            if (line.trim().startsWith("```")) {
                codeBlock = !codeBlock
                return@forEachIndexed
            }

            if (codeBlock) {
                add(segments, line, TextStyle.plain().withColor("gray"))
            } else {
                parseBlockLine(line, segments)
            }

            if (index < lines.lastIndex) {
                add(segments, "\n", TextStyle.plain())
            }
        }
        return segments
    }

    private fun parseBlockLine(line: String, segments: MutableList<Segment>) {
        val heading = headingLevel(line)
        if (heading > 0) {
            parseInline(
                line.substring(heading + 1).trim(),
                TextStyle.plain().withBold().withColor(headingColor(heading)),
                segments
            )
            return
        }

        when {
            line.startsWith("> ") -> parseInline(
                line.substring(2),
                TextStyle.plain().withItalic().withColor("gray"),
                segments
            )

            line.startsWith("- ") || line.startsWith("* ") -> {
                add(segments, "- ", TextStyle.plain().withColor("gray"))
                parseInline(line.substring(2), TextStyle.plain(), segments)
            }

            else -> parseInline(line, TextStyle.plain(), segments)
        }
    }

    private fun parseInline(text: String, base: TextStyle, segments: MutableList<Segment>) {
        var style = base
        var i = 0

        loop@ while (i < text.length) {
            for (span in EMPHASIS_SPANS) {
                val next = parseDelimited(text, i, span.marker, span.apply(style), segments)
                if (next != null) {
                    i = next
                    continue@loop
                }
            }

            when (text[i]) {
                '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        add(segments, text.substring(i + 1, end), style.withColor("aqua"))
                        i = end + 1
                        continue@loop
                    }
                }

                '[' -> {
                    val link = parseLink(text, i)
                    if (link != null) {
                        parseInline(
                            link.label,
                            style.withColor("blue").withUnderlined().withClickUrl(link.url),
                            segments
                        )
                        i = link.endIndex
                        continue@loop
                    }
                }

                '{' -> {
                    val span = parseColorSpan(text, i)
                    if (span != null) {
                        parseInline(span.text, style.withColor(span.color), segments)
                        i = span.endIndex
                        continue@loop
                    }
                    val switch = parseColorSwitch(text, i)
                    if (switch != null) {
                        style = style.withColor(switch.color)
                        i = switch.endIndex
                        continue@loop
                    }
                }

                '*' -> {
                    val next = parseDelimited(text, i, "*", style.withItalic(), segments)
                    if (next != null) {
                        i = next
                        continue@loop
                    }
                }

                '_' -> {
                    val next = parseDelimited(text, i, "_", style.withItalic(), segments)
                    if (next != null) {
                        i = next
                        continue@loop
                    }
                }
            }

            val next = nextMarker(text, i + 1)
            add(segments, text.substring(i, next), style)
            i = next
        }
    }

    private fun parseDelimited(
        text: String,
        start: Int,
        marker: String,
        style: TextStyle,
        segments: MutableList<Segment>
    ): Int? {
        if (!text.startsWith(marker, start)) {
            return null
        }
        val contentStart = start + marker.length
        val end = text.indexOf(marker, contentStart)
        if (end <= contentStart) {
            return null
        }
        parseInline(text.substring(contentStart, end), style, segments)
        return end + marker.length
    }

    private fun nextMarker(text: String, from: Int): Int {
        var next = text.length
        for (marker in MARKERS) {
            val index = text.indexOf(marker, from)
            if (index in 0..<next) {
                next = index
            }
        }
        return next
    }

    private fun add(segments: MutableList<Segment>, text: String, style: TextStyle) {
        if (text.isNotEmpty()) {
            segments += Segment(text, style)
        }
    }

    private fun headingLevel(line: String): Int {
        val level = line.take(6).takeWhile { it == '#' }.length
        return level.takeIf { it > 0 && line.getOrNull(it) == ' ' } ?: 0
    }

    private fun headingColor(level: Int): String = when (level) {
        1 -> "gold"
        2 -> "yellow"
        else -> "aqua"
    }

    private fun parseLink(text: String, start: Int): Link? {
        val labelEnd = text.indexOf("](", start + 1).takeIf { it > start + 1 } ?: return null
        val urlEnd = text.indexOf(')', labelEnd + 2).takeIf { it > labelEnd + 2 } ?: return null
        val url = safeUrl(text.substring(labelEnd + 2, urlEnd).trim()) ?: return null
        return Link(text.substring(start + 1, labelEnd), url, urlEnd + 1)
    }

    private fun parseColorSpan(text: String, start: Int): ColorSpan? {
        val colon = text.indexOf(':', start + 1)
        val end = text.indexOf('}', start + 1)
        if (colon <= start + 1 || end <= colon + 1) {
            return null
        }
        val color = normalizeColor(text.substring(start + 1, colon)) ?: return null
        return ColorSpan(color, text.substring(colon + 1, end), end + 1)
    }

    private fun parseColorSwitch(text: String, start: Int): ColorSwitch? {
        val end = text.indexOf('}', start + 1).takeIf { it > start + 1 } ?: return null
        val token = text.substring(start + 1, end).trim()
        if (token.equals("reset", ignoreCase = true)) {
            return ColorSwitch(null, end + 1)
        }
        return normalizeColor(token)?.let { ColorSwitch(it, end + 1) }
    }

    private fun normalizeColor(color: String?): String? =
        color?.trim()?.lowercase()?.replace('-', '_')?.takeIf(COLORS::contains)

    private fun safeUrl(url: String?): String? =
        url?.trim()?.takeIf { trimmed ->
            trimmed.lowercase().let { it.startsWith("https://") || it.startsWith("http://") }
        }

    private fun String?.normalizedLines(): List<String> =
        orEmpty().replace("\r", "").split('\n')

    interface ComponentAdapter<T> {
        fun empty(): T

        fun literal(text: String, style: TextStyle): T

        fun append(target: T, child: T): T
    }

    @JvmRecord
    data class TextStyle(
        val color: String,
        val bold: Boolean,
        val italic: Boolean,
        val underlined: Boolean,
        val strikethrough: Boolean,
        val clickUrl: String?
    ) {
        fun withColor(color: String?): TextStyle = copy(color = normalizeColor(color))

        fun withBold(bold: Boolean = true): TextStyle = copy(bold = bold)

        fun withItalic(italic: Boolean = true): TextStyle = copy(italic = italic)

        fun withUnderlined(underlined: Boolean = true): TextStyle = copy(underlined = underlined)

        fun withStrikethrough(strikethrough: Boolean = true): TextStyle = copy(strikethrough = strikethrough)

        fun withClickUrl(clickUrl: String?): TextStyle = copy(clickUrl = safeUrl(clickUrl))

        companion object {
            fun plain() = TextStyle(null, false, false, false, false, null)
        }
    }

    @JvmRecord
    data class Segment(val text: String, val style: TextStyle)

    private data class InlineSpan(val marker: String, val apply: (TextStyle) -> TextStyle)

    private data class Link(val label: String, val url: String, val endIndex: Int)

    private data class ColorSpan(val color: String, val text: String, val endIndex: Int)

    private data class ColorSwitch(val color: String, val endIndex: Int)
}
