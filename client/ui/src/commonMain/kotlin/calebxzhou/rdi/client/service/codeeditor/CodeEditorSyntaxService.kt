package calebxzhou.rdi.client.service.codeeditor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import calebxzhou.rdi.client.ui.MaterialColor
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml

private val punctuationStyle = SpanStyle(color = MaterialColor.BLUE_GRAY_500.color)
private val jsonKeyStyle = SpanStyle(color = MaterialColor.BLUE_800.color, fontWeight = FontWeight.SemiBold)
private val jsonStringStyle = SpanStyle(color = MaterialColor.GREEN_800.color)
private val jsonNumberStyle = SpanStyle(color = MaterialColor.DEEP_ORANGE_700.color)
private val jsonLiteralStyle = SpanStyle(color = MaterialColor.PURPLE_700.color, fontWeight = FontWeight.Medium)
private val tomlTableStyle = SpanStyle(color = MaterialColor.BLUE_800.color, fontWeight = FontWeight.SemiBold)
private val tomlKeyStyle = SpanStyle(color = MaterialColor.TEAL_800.color, fontWeight = FontWeight.SemiBold)
private val tomlStringStyle = SpanStyle(color = MaterialColor.GREEN_800.color)
private val tomlNumberStyle = SpanStyle(color = MaterialColor.DEEP_ORANGE_700.color)
private val tomlLiteralStyle = SpanStyle(color = MaterialColor.PURPLE_700.color, fontWeight = FontWeight.Medium)
private val tomlCommentStyle = SpanStyle(color = MaterialColor.GRAY_600.color)
private val yamlKeyStyle = SpanStyle(color = MaterialColor.BLUE_800.color, fontWeight = FontWeight.SemiBold)
private val yamlStringStyle = SpanStyle(color = MaterialColor.GREEN_800.color)
private val yamlNumberStyle = SpanStyle(color = MaterialColor.DEEP_ORANGE_700.color)
private val yamlLiteralStyle = SpanStyle(color = MaterialColor.PURPLE_700.color, fontWeight = FontWeight.Medium)
private val yamlCommentStyle = SpanStyle(color = MaterialColor.GRAY_600.color)
private val yamlAnchorStyle = SpanStyle(color = MaterialColor.TEAL_800.color, fontWeight = FontWeight.Medium)
private val cfgSectionStyle = SpanStyle(color = MaterialColor.BLUE_800.color, fontWeight = FontWeight.SemiBold)
private val cfgTypeStyle = SpanStyle(color = MaterialColor.PURPLE_700.color, fontWeight = FontWeight.Medium)
private val cfgKeyStyle = SpanStyle(color = MaterialColor.TEAL_800.color, fontWeight = FontWeight.SemiBold)
private val cfgValueStyle = SpanStyle(color = MaterialColor.GREEN_800.color)
private val cfgNumberStyle = SpanStyle(color = MaterialColor.DEEP_ORANGE_700.color)
private val cfgCommentStyle = SpanStyle(color = MaterialColor.GRAY_600.color)

private val strictJson = Json {
    ignoreUnknownKeys = true
    isLenient = false
    allowTrailingComma = false
    allowComments = false
}

private val syntaxCheckToml = Toml {
    ignoreUnknownKeys = true
}

private val jsonNumberRegex = Regex("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")
private val jsonLiteralRegex = Regex("(?:true|false|null)\\b")
private val tomlNumberRegex = Regex("[+-]?(?:\\d(?:[\\d_]*\\d)?)(?:\\.\\d(?:[\\d_]*\\d)?)?(?:[eE][+-]?\\d(?:[\\d_]*\\d)?)?")
private val tomlDateTimeRegex = Regex("\\d{4}-\\d{2}-\\d{2}(?:[Tt ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)?(?:[Zz]|[+-]\\d{2}:\\d{2})?")
private val tomlTokenBreakChars = charArrayOf(' ', '\t', ',', ']', '}', '#')
private val yamlNumberRegex = Regex("[+-]?(?:0|[1-9]\\d*|0b[01_]+|0o[0-7_]+|0x[\\da-fA-F_]+)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")
private val yamlLiteralTokens = setOf("true", "false", "null", "~", "yes", "no", "on", "off")
private val yamlSpecialNumberTokens = setOf(".inf", "+.inf", "-.inf", ".nan")
private val yamlTokenBreakChars = charArrayOf(' ', '\t', ',', '[', ']', '{', '}', ':')
private val yamlTagBreakChars = charArrayOf(',', '[', ']', '{', '}', ':')
private val forgeCfgNumberRegex = Regex("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?")
private val forgeCfgTypeTokens = setOf('B', 'I', 'D', 'S')
private val forgeCfgSectionNameRegex = Regex("(?:[A-Za-z0-9_.-]+|\"(?:[^\"\\\\]|\\\\.)+\")")
private const val FORGE_CFG_QUOTED_NAME_PATTERN = "\"(?:[^\"\\\\]|\\\\.)+\""
private const val FORGE_CFG_BARE_KEY_NAME_PATTERN = "[^<>=\\s:\"]+"
private val forgeCfgKeyNamePattern = "(?:$FORGE_CFG_BARE_KEY_NAME_PATTERN|$FORGE_CFG_QUOTED_NAME_PATTERN)"
private val forgeCfgValueLineRegex = Regex("[BIDS]:\\s*$forgeCfgKeyNamePattern\\s*=.*")
private val forgeCfgListStartRegex = Regex("[BIDS]:\\s*$forgeCfgKeyNamePattern\\s*<")

fun buildEditorValue(
    text: String,
    selection: TextRange,
    language: CodeLanguage,
    composition: TextRange? = null
): TextFieldValue {
    return TextFieldValue(
        annotatedString = highlightCode(text, language),
        selection = selection.coerceToText(text.length),
        composition = composition?.coerceToText(text.length)
    )
}

fun validateCodeContent(text: String, language: CodeLanguage): CodeEditorValidation? = when (language) {
    CodeLanguage.JSON -> runCatching {
        strictJson.parseToJsonElement(text)
    }.fold(
        onSuccess = { CodeEditorValidation(language, true, "JSON语法正确") },
        onFailure = { CodeEditorValidation(language, false, "JSON语法错误: ${it.shortMessage}") }
    )

    CodeLanguage.JSON5 -> runCatching {
        strictJson.parseToJsonElement(normalizeJson5ToJson(text))
    }.fold(
        onSuccess = { CodeEditorValidation(language, true, "JSON5语法正确") },
        onFailure = { CodeEditorValidation(language, false, "JSON5语法错误: ${it.shortMessage}") }
    )

    CodeLanguage.TOML -> runCatching {
        syntaxCheckToml.parseToTomlTable(text)
    }.fold(
        onSuccess = { CodeEditorValidation(language, true, "TOML语法正确") },
        onFailure = { CodeEditorValidation(language, false, "TOML语法错误: ${it.shortMessage}") }
    )

    CodeLanguage.YAML -> validateYamlSyntaxMessage(text)?.let {
        CodeEditorValidation(language, false, "YAML语法错误: $it")
    } ?: CodeEditorValidation(language, true, "YAML语法正确")

    CodeLanguage.FORGE_CFG -> validateForgeCfgSyntaxMessage(text)?.let {
        CodeEditorValidation(language, false, "CFG语法错误: $it")
    } ?: CodeEditorValidation(language, true, "CFG语法正确")

    CodeLanguage.PLAIN_TEXT -> null
}

private fun highlightCode(text: String, language: CodeLanguage): AnnotatedString = buildAnnotatedString {
    append(text)
    when (language) {
        CodeLanguage.JSON,
        CodeLanguage.JSON5 -> applyJsonHighlight(text)
        CodeLanguage.TOML -> applyTomlHighlight(text)
        CodeLanguage.YAML -> applyYamlHighlight(text)
        CodeLanguage.FORGE_CFG -> applyForgeCfgHighlight(text)
        CodeLanguage.PLAIN_TEXT -> Unit
    }
}

private fun AnnotatedString.Builder.applyJsonHighlight(text: String) {
    var index = 0
    while (index < text.length) {
        when (text[index]) {
            '"' -> {
                val end = scanQuotedText(text, index, '"')
                val nextTokenStart = skipWhitespace(text, end)
                safeAddStyle(
                    if (nextTokenStart < text.length && text[nextTokenStart] == ':') jsonKeyStyle else jsonStringStyle,
                    index,
                    end
                )
                index = end
            }
            '{', '}', '[', ']', ':', ',' -> {
                safeAddStyle(punctuationStyle, index, index + 1)
                index++
            }
            else -> {
                val numberMatch = jsonNumberRegex.find(text, index)
                if (numberMatch != null && numberMatch.range.first == index) {
                    safeAddStyle(jsonNumberStyle, index, numberMatch.range.last + 1)
                    index = numberMatch.range.last + 1
                    continue
                }
                val literalMatch = jsonLiteralRegex.find(text, index)
                if (literalMatch != null && literalMatch.range.first == index) {
                    safeAddStyle(jsonLiteralStyle, index, literalMatch.range.last + 1)
                    index = literalMatch.range.last + 1
                    continue
                }
                index++
            }
        }
    }
}

private fun AnnotatedString.Builder.applyTomlHighlight(text: String) {
    var lineStart = 0
    while (lineStart <= text.length) {
        val lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
        applyTomlLineHighlight(text, lineStart, lineEnd)
        if (lineEnd == text.length) break
        lineStart = lineEnd + 1
    }
}

private fun AnnotatedString.Builder.applyTomlLineHighlight(text: String, start: Int, end: Int) {
    if (start >= end) return
    val commentStart = findTomlCommentStart(text, start, end)
    val codeEnd = commentStart ?: end
    if (commentStart != null) safeAddStyle(tomlCommentStyle, commentStart, end)
    val contentStart = skipWhitespace(text, start, codeEnd)
    if (contentStart >= codeEnd) return
    if (text[contentStart] == '[') {
        safeAddStyle(tomlTableStyle, contentStart, codeEnd)
        return
    }
    val equalsIndex = findTomlEquals(text, contentStart, codeEnd)
    if (equalsIndex != -1) {
        safeAddStyle(tomlKeyStyle, contentStart, equalsIndex)
        safeAddStyle(punctuationStyle, equalsIndex, equalsIndex + 1)
        applyTomlValueHighlight(text, equalsIndex + 1, codeEnd)
    } else {
        applyTomlValueHighlight(text, contentStart, codeEnd)
    }
}

private fun AnnotatedString.Builder.applyTomlValueHighlight(text: String, start: Int, end: Int) {
    var index = start
    while (index < end) {
        when (text[index]) {
            '"', '\'' -> {
                val quote = text[index]
                val quoteLength = detectTripleQuote(text, index, end, quote)
                val closeIndex = scanTomlString(text, index, end, quote, quoteLength)
                safeAddStyle(tomlStringStyle, index, closeIndex)
                index = closeIndex
            }
            '[', ']', '{', '}', ',', '.' -> {
                safeAddStyle(punctuationStyle, index, index + 1)
                index++
            }
            '#', ' ', '\t' -> index++
            else -> {
                val tokenEnd = scanTomlBareToken(text, index, end)
                val token = text.substring(index, tokenEnd)
                when {
                    token == "true" || token == "false" -> safeAddStyle(tomlLiteralStyle, index, tokenEnd)
                    tomlNumberRegex.matches(token) || tomlDateTimeRegex.matches(token) -> safeAddStyle(tomlNumberStyle, index, tokenEnd)
                }
                index = tokenEnd
            }
        }
    }
}

private fun AnnotatedString.Builder.applyYamlHighlight(text: String) {
    var lineStart = 0
    while (lineStart <= text.length) {
        val lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
        applyYamlLineHighlight(text, lineStart, lineEnd)
        if (lineEnd == text.length) break
        lineStart = lineEnd + 1
    }
}

private fun AnnotatedString.Builder.applyYamlLineHighlight(text: String, start: Int, end: Int) {
    if (start >= end) return
    val commentStart = findYamlCommentStart(text, start, end)
    val codeEnd = commentStart ?: end
    if (commentStart != null) safeAddStyle(yamlCommentStyle, commentStart, end)
    var contentStart = skipWhitespace(text, start, codeEnd)
    if (contentStart >= codeEnd) return
    if (isYamlDocumentMarker(text, contentStart, codeEnd, "---") || isYamlDocumentMarker(text, contentStart, codeEnd, "...")) {
        safeAddStyle(punctuationStyle, contentStart, codeEnd)
        return
    }
    if (text[contentStart] == '%') {
        safeAddStyle(yamlAnchorStyle, contentStart, codeEnd)
        return
    }
    if (isYamlSequenceDash(text, contentStart, codeEnd)) {
        safeAddStyle(punctuationStyle, contentStart, contentStart + 1)
        contentStart = skipWhitespace(text, contentStart + 1, codeEnd)
        if (contentStart >= codeEnd) return
    } else if (text[contentStart] == '?' && isYamlIndicatorBoundary(text, contentStart + 1, codeEnd)) {
        safeAddStyle(punctuationStyle, contentStart, contentStart + 1)
        contentStart = skipWhitespace(text, contentStart + 1, codeEnd)
        if (contentStart >= codeEnd) return
    }
    val colonIndex = findYamlKeySeparator(text, contentStart, codeEnd)
    if (colonIndex != -1) {
        applyYamlKeyHighlight(text, contentStart, colonIndex)
        safeAddStyle(punctuationStyle, colonIndex, colonIndex + 1)
        applyYamlValueHighlight(text, colonIndex + 1, codeEnd)
    } else {
        applyYamlValueHighlight(text, contentStart, codeEnd)
    }
}

private fun AnnotatedString.Builder.applyYamlKeyHighlight(text: String, start: Int, end: Int) {
    val keyStart = skipWhitespace(text, start, end)
    var keyEnd = end
    while (keyEnd > keyStart && text[keyEnd - 1].isWhitespace()) keyEnd--
    if (keyStart >= keyEnd) return
    safeAddStyle(yamlKeyStyle, keyStart, keyEnd)
}

private fun AnnotatedString.Builder.applyYamlValueHighlight(text: String, start: Int, end: Int) {
    var index = skipWhitespace(text, start, end)
    while (index < end) {
        when (text[index]) {
            '"', '\'' -> {
                val closeIndex = scanYamlQuotedText(text, index, end)
                safeAddStyle(yamlStringStyle, index, closeIndex)
                index = closeIndex
            }
            '[', ']', '{', '}', ',', ':' -> {
                safeAddStyle(punctuationStyle, index, index + 1)
                index++
            }
            '&', '*', '!' -> {
                val tokenEnd = scanYamlTaggedToken(text, index, end)
                safeAddStyle(yamlAnchorStyle, index, tokenEnd)
                index = tokenEnd
            }
            '|', '>' -> {
                val tokenEnd = scanYamlBlockScalarHeader(text, index, end)
                safeAddStyle(punctuationStyle, index, tokenEnd)
                index = tokenEnd
            }
            ' ', '\t' -> index++
            else -> {
                if (text[index] == '?' && isYamlIndicatorBoundary(text, index + 1, end)) {
                    safeAddStyle(punctuationStyle, index, index + 1)
                    index++
                    continue
                }
                if (isYamlSequenceDash(text, index, end)) {
                    safeAddStyle(punctuationStyle, index, index + 1)
                    index = skipWhitespace(text, index + 1, end)
                    continue
                }
                val tokenEnd = scanYamlBareToken(text, index, end)
                applyYamlTokenHighlight(text, index, tokenEnd)
                index = if (tokenEnd > index) tokenEnd else index + 1
            }
        }
    }
}

private fun AnnotatedString.Builder.applyYamlTokenHighlight(text: String, start: Int, end: Int) {
    if (start >= end) return
    val token = text.substring(start, end)
    val normalizedToken = token.lowercase()
    when {
        normalizedToken in yamlLiteralTokens -> safeAddStyle(yamlLiteralStyle, start, end)
        normalizedToken in yamlSpecialNumberTokens || yamlNumberRegex.matches(token) -> safeAddStyle(yamlNumberStyle, start, end)
    }
}

private fun AnnotatedString.Builder.applyForgeCfgHighlight(text: String) {
    var lineStart = 0
    while (lineStart <= text.length) {
        val lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
        applyForgeCfgLineHighlight(text, lineStart, lineEnd)
        if (lineEnd == text.length) break
        lineStart = lineEnd + 1
    }
}

private fun AnnotatedString.Builder.applyForgeCfgLineHighlight(text: String, start: Int, end: Int) {
    if (start >= end) return
    val commentStart = findForgeCfgCommentStart(text, start, end)
    val codeEnd = commentStart ?: end
    if (commentStart != null) safeAddStyle(cfgCommentStyle, commentStart, end)
    val contentStart = skipWhitespace(text, start, codeEnd)
    if (contentStart >= codeEnd) return

    val content = text.substring(contentStart, codeEnd).trimEnd()
    val trimmedEnd = contentStart + content.length
    when {
        content == "}" || content == ">" -> safeAddStyle(punctuationStyle, contentStart, trimmedEnd)
        content.endsWith("{") -> {
            val sectionEnd = contentStart + content.dropLast(1).trimEnd().length
            safeAddStyle(cfgSectionStyle, contentStart, sectionEnd)
            safeAddStyle(punctuationStyle, trimmedEnd - 1, trimmedEnd)
        }
        content.length >= 2 && content[0] in forgeCfgTypeTokens && content[1] == ':' -> {
            safeAddStyle(cfgTypeStyle, contentStart, contentStart + 1)
            safeAddStyle(punctuationStyle, contentStart + 1, contentStart + 2)
            val equalsIndex = text.indexOf('=', contentStart + 2).takeIf { it in contentStart + 2 until codeEnd }
            val listIndex = text.indexOf('<', contentStart + 2).takeIf { it in contentStart + 2 until codeEnd }
            val separatorIndex = listOfNotNull(equalsIndex, listIndex).minOrNull()
            if (separatorIndex != null) {
                safeAddStyle(cfgKeyStyle, contentStart + 2, separatorIndex)
                safeAddStyle(punctuationStyle, separatorIndex, separatorIndex + 1)
                if (separatorIndex == equalsIndex) {
                    applyForgeCfgValueHighlight(text, separatorIndex + 1, codeEnd)
                }
            } else {
                safeAddStyle(cfgKeyStyle, contentStart + 2, trimmedEnd)
            }
        }
        else -> applyForgeCfgValueHighlight(text, contentStart, codeEnd)
    }
}

private fun AnnotatedString.Builder.applyForgeCfgValueHighlight(text: String, start: Int, end: Int) {
    var index = skipWhitespace(text, start, end)
    while (index < end) {
        when (text[index]) {
            '<', '>', '{', '}', ':' -> {
                safeAddStyle(punctuationStyle, index, index + 1)
                index++
            }
            ' ', '\t' -> index++
            else -> {
                val tokenEnd = scanForgeCfgValueToken(text, index, end)
                val token = text.substring(index, tokenEnd)
                when {
                    token.equals("true", ignoreCase = true) || token.equals("false", ignoreCase = true) ->
                        safeAddStyle(tomlLiteralStyle, index, tokenEnd)
                    forgeCfgNumberRegex.matches(token) -> safeAddStyle(cfgNumberStyle, index, tokenEnd)
                    token.isNotBlank() -> safeAddStyle(cfgValueStyle, index, tokenEnd)
                }
                index = if (tokenEnd > index) tokenEnd else index + 1
            }
        }
    }
}

private fun validateForgeCfgSyntaxMessage(text: String): String? {
    var sectionDepth = 0
    var listDepth = 0
    for ((lineIndex, rawLine) in text.lineSequence().withIndex()) {
        val lineNumber = lineIndex + 1
        val line = rawLine.trimEnd('\r')
        val code = stripForgeCfgComment(line).trim()
        if (code.isBlank()) continue

        if (listDepth > 0) {
            if (code == ">") listDepth--
            continue
        }

        when {
            code == "}" -> {
                if (sectionDepth == 0) return "第${lineNumber}行 多余的}"
                sectionDepth--
            }
            code == ">" -> return "第${lineNumber}行 多余的>"
            code.endsWith("{") -> {
                val sectionName = code.dropLast(1).trim()
                if (sectionName.isBlank() || !forgeCfgSectionNameRegex.matches(sectionName)) {
                    return "第${lineNumber}行 section名称无效"
                }
                sectionDepth++
            }
            forgeCfgListStartRegex.matches(code) -> listDepth++
            forgeCfgValueLineRegex.matches(code) -> Unit
            code.length >= 2 && code[1] == ':' && code[0] !in forgeCfgTypeTokens ->
                return "第${lineNumber}行 不支持的类型前缀${code[0]}"
            else -> return "第${lineNumber}行 无法识别的CFG配置行"
        }
    }
    return when {
        listDepth > 0 -> "缺少>"
        sectionDepth > 0 -> "缺少}"
        else -> null
    }
}

private fun findForgeCfgCommentStart(text: String, start: Int, end: Int): Int? {
    var index = start
    while (index < end) {
        if (text[index] == '#') return index
        index++
    }
    return null
}

private fun stripForgeCfgComment(line: String): String {
    val index = line.indexOf('#')
    return if (index == -1) line else line.substring(0, index)
}

private fun scanForgeCfgValueToken(text: String, start: Int, end: Int): Int {
    var index = start
    while (index < end && !text[index].isWhitespace() && text[index] !in charArrayOf('<', '>', '{', '}', ':')) index++
    return index
}

private fun scanQuotedText(text: String, start: Int, quote: Char): Int {
    var index = start + 1
    while (index < text.length) {
        when {
            text[index] == '\\' -> index += 2
            text[index] == quote -> return index + 1
            else -> index++
        }
    }
    return text.length
}

private fun findTomlCommentStart(text: String, start: Int, end: Int): Int? {
    var index = start
    var inDoubleQuote = false
    var inSingleQuote = false
    while (index < end) {
        when {
            !inSingleQuote && text[index] == '"' && !text.isEscaped(index) -> inDoubleQuote = !inDoubleQuote
            !inDoubleQuote && text[index] == '\'' -> inSingleQuote = !inSingleQuote
            !inDoubleQuote && !inSingleQuote && text[index] == '#' -> return index
        }
        index++
    }
    return null
}

private fun findTomlEquals(text: String, start: Int, end: Int): Int {
    var index = start
    var inDoubleQuote = false
    var inSingleQuote = false
    while (index < end) {
        when {
            !inSingleQuote && text[index] == '"' && !text.isEscaped(index) -> inDoubleQuote = !inDoubleQuote
            !inDoubleQuote && text[index] == '\'' -> inSingleQuote = !inSingleQuote
            !inDoubleQuote && !inSingleQuote && text[index] == '=' -> return index
        }
        index++
    }
    return -1
}

private fun detectTripleQuote(text: String, start: Int, end: Int, quote: Char): Int {
    return if (start + 2 < end && text[start + 1] == quote && text[start + 2] == quote) 3 else 1
}

private fun scanTomlString(text: String, start: Int, end: Int, quote: Char, quoteLength: Int): Int {
    var index = start + quoteLength
    while (index < end) {
        if (quoteLength == 3) {
            if (index + 2 < end && text[index] == quote && text[index + 1] == quote && text[index + 2] == quote) {
                return index + 3
            }
            index++
            continue
        }
        when {
            quote == '"' && text[index] == '\\' -> index += 2
            text[index] == quote -> return index + 1
            else -> index++
        }
    }
    return end
}

private fun scanTomlBareToken(text: String, start: Int, end: Int): Int {
    var index = start
    while (index < end && text[index] !in tomlTokenBreakChars) index++
    return index
}

private fun skipWhitespace(text: String, start: Int, end: Int = text.length): Int {
    var index = start
    while (index < end && text[index].isWhitespace()) index++
    return index
}

private fun findYamlCommentStart(text: String, start: Int, end: Int): Int? {
    var index = start
    var inDoubleQuote = false
    var inSingleQuote = false
    while (index < end) {
        val ch = text[index]
        when {
            inDoubleQuote -> when {
                ch == '\\' && index + 1 < end -> index += 2
                ch == '"' -> {
                    inDoubleQuote = false
                    index++
                }
                else -> index++
            }
            inSingleQuote -> when {
                ch == '\'' && index + 1 < end && text[index + 1] == '\'' -> index += 2
                ch == '\'' -> {
                    inSingleQuote = false
                    index++
                }
                else -> index++
            }
            ch == '"' -> {
                inDoubleQuote = true
                index++
            }
            ch == '\'' -> {
                inSingleQuote = true
                index++
            }
            ch == '#' -> {
                val previous = text.getOrNull(index - 1)
                if (index == start || previous?.isWhitespace() != false) return index
                index++
            }
            else -> index++
        }
    }
    return null
}

private fun isYamlDocumentMarker(text: String, start: Int, end: Int, marker: String): Boolean {
    if (!text.regionMatches(start, marker, 0, marker.length)) return false
    val markerEnd = start + marker.length
    return markerEnd >= end || text[markerEnd].isWhitespace()
}

private fun isYamlSequenceDash(text: String, index: Int, end: Int): Boolean {
    if (index >= end || text[index] != '-') return false
    return index + 1 >= end || isYamlIndicatorBoundary(text, index + 1, end)
}

private fun isYamlIndicatorBoundary(text: String, index: Int, end: Int): Boolean {
    if (index >= end) return true
    return text[index].isWhitespace() || text[index] in charArrayOf('[', '{', '}', ']', ',', '#', '&', '*', '!', '|', '>')
}

private fun findYamlKeySeparator(text: String, start: Int, end: Int): Int {
    var index = start
    var inDoubleQuote = false
    var inSingleQuote = false
    var flowDepth = 0
    while (index < end) {
        val ch = text[index]
        when {
            inDoubleQuote -> when {
                ch == '\\' && index + 1 < end -> index += 2
                ch == '"' -> {
                    inDoubleQuote = false
                    index++
                }
                else -> index++
            }
            inSingleQuote -> when {
                ch == '\'' && index + 1 < end && text[index + 1] == '\'' -> index += 2
                ch == '\'' -> {
                    inSingleQuote = false
                    index++
                }
                else -> index++
            }
            else -> {
                when (ch) {
                    '"' -> inDoubleQuote = true
                    '\'' -> inSingleQuote = true
                    '[', '{' -> flowDepth++
                    ']', '}' -> flowDepth = (flowDepth - 1).coerceAtLeast(0)
                    ':' -> if (flowDepth == 0 && isYamlIndicatorBoundary(text, index + 1, end)) return index
                }
                index++
            }
        }
    }
    return -1
}

private fun scanYamlQuotedText(text: String, start: Int, end: Int): Int {
    val quote = text[start]
    var index = start + 1
    while (index < end) {
        when (quote) {
            '"' -> when {
                text[index] == '\\' && index + 1 < end -> index += 2
                text[index] == '"' -> return index + 1
                else -> index++
            }
            '\'' -> when {
                text[index] == '\'' && index + 1 < end && text[index + 1] == '\'' -> index += 2
                text[index] == '\'' -> return index + 1
                else -> index++
            }
        }
    }
    return end
}

private fun scanYamlBareToken(text: String, start: Int, end: Int): Int {
    var index = start
    while (index < end && text[index] !in yamlTokenBreakChars) index++
    return index
}

private fun scanYamlTaggedToken(text: String, start: Int, end: Int): Int {
    var index = start + 1
    while (index < end && text[index] !in yamlTagBreakChars && !text[index].isWhitespace()) index++
    return index
}

private fun scanYamlBlockScalarHeader(text: String, start: Int, end: Int): Int {
    var index = start + 1
    while (index < end && (text[index] == '+' || text[index] == '-' || text[index].isDigit())) index++
    return index
}

private fun String.isEscaped(index: Int): Boolean {
    var slashCount = 0
    var current = index - 1
    while (current >= 0 && this[current] == '\\') {
        slashCount++
        current--
    }
    return slashCount % 2 == 1
}

private fun AnnotatedString.Builder.safeAddStyle(style: SpanStyle, start: Int, end: Int) {
    if (start in 0 until end) addStyle(style, start, end)
}

private val Throwable.shortMessage: String
    get() = message
        ?.lineSequence()
        ?.firstOrNull()
        ?.trim()
        ?.take(120)
        ?: "未知错误"
