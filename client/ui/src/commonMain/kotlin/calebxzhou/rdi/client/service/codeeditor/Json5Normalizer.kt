package calebxzhou.rdi.client.service.codeeditor

private val jsonNumberRegex = Regex("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")

internal fun normalizeJson5ToJson(text: String): String = Json5Normalizer(text).normalize()

private class Json5Normalizer(private val source: String) {
    private var index = 0

    fun normalize(): String {
        skipTrivia()
        val normalized = parseValue()
        skipTrivia()
        if (index != source.length) {
            fail("多余内容")
        }
        return normalized
    }

    private fun parseValue(): String {
        skipTrivia()
        if (index >= source.length) fail("缺少值")
        return when (val ch = source[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"', '\'' -> parseString(ch)
            '+', '-', '.' -> parseNumberOrIdentifier()
            in '0'..'9' -> parseNumberOrIdentifier()
            else -> {
                if (isIdentifierStart(ch)) {
                    parseIdentifierValue()
                } else {
                    fail("无效字符 ${ch}")
                }
            }
        }
    }

    private fun parseObject(): String {
        expect('{')
        val builder = StringBuilder()
        builder.append('{')
        skipTrivia()
        var first = true
        while (!consumeIf('}')) {
            if (!first) builder.append(',')
            first = false
            builder.append(parseObjectKey())
            skipTrivia()
            expect(':')
            builder.append(':')
            builder.append(parseValue())
            skipTrivia()
            if (consumeIf(',')) {
                skipTrivia()
                if (consumeIf('}')) {
                    builder.append('}')
                    return builder.toString()
                }
            } else {
                expect('}')
                builder.append('}')
                return builder.toString()
            }
        }
        builder.append('}')
        return builder.toString()
    }

    private fun parseArray(): String {
        expect('[')
        val builder = StringBuilder()
        builder.append('[')
        skipTrivia()
        var first = true
        while (!consumeIf(']')) {
            if (!first) builder.append(',')
            first = false
            builder.append(parseValue())
            skipTrivia()
            if (consumeIf(',')) {
                skipTrivia()
                if (consumeIf(']')) {
                    builder.append(']')
                    return builder.toString()
                }
            } else {
                expect(']')
                builder.append(']')
                return builder.toString()
            }
        }
        builder.append(']')
        return builder.toString()
    }

    private fun parseObjectKey(): String {
        skipTrivia()
        if (index >= source.length) fail("缺少对象键")
        return when (val ch = source[index]) {
            '"', '\'' -> parseString(ch)
            else -> {
                if (!isIdentifierStart(ch)) fail("无效对象键")
                quoteJsonString(readIdentifier())
            }
        }
    }

    private fun parseIdentifierValue(): String {
        val identifier = readIdentifier()
        return when (identifier) {
            "true", "false", "null" -> identifier
            "Infinity", "+Infinity", "-Infinity", "NaN", "+NaN", "-NaN" -> "0"
            else -> fail("无效标识符 $identifier")
        }
    }

    private fun parseNumberOrIdentifier(): String {
        val token = readToken()
        return canonicalizeJson5Token(token)
    }

    private fun parseString(quote: Char): String {
        expect(quote)
        val content = StringBuilder()
        while (index < source.length) {
            val ch = source[index++]
            when (ch) {
                quote -> return quoteJsonString(content.toString())
                '\\' -> content.append(parseEscape())
                '\r', '\n' -> fail("字符串缺少结束引号")
                else -> content.append(ch)
            }
        }
        fail("字符串缺少结束引号")
    }

    private fun parseEscape(): String {
        if (index >= source.length) fail("无效转义")
        val escaped = source[index++]
        return when (escaped) {
            '\r' -> {
                if (index < source.length && source[index] == '\n') index++
                ""
            }
            '\n' -> ""
            '\'', '"', '\\', '/' -> escaped.toString()
            'b' -> "\b"
            'f' -> "\u000C"
            'n' -> "\n"
            'r' -> "\r"
            't' -> "\t"
            'v' -> "\u000B"
            '0' -> {
                if (index < source.length && source[index].isDigit()) {
                    fail("不支持八进制转义")
                }
                "\u0000"
            }
            'x' -> readHexEscape(2)
            'u' -> readHexEscape(4)
            else -> escaped.toString()
        }
    }

    private fun readHexEscape(length: Int): String {
        if (index + length > source.length) fail("无效Unicode转义")
        val hex = source.substring(index, index + length)
        if (!hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            fail("无效Unicode转义")
        }
        index += length
        return hex.toInt(16).toChar().toString()
    }

    private fun readIdentifier(): String {
        val start = index
        index++
        while (index < source.length && isIdentifierPart(source[index])) {
            index++
        }
        return source.substring(start, index)
    }

    private fun readToken(): String {
        val start = index
        while (index < source.length) {
            val ch = source[index]
            if (ch.isWhitespace() || ch == ',' || ch == ']' || ch == '}') break
            if (ch == '/' && index + 1 < source.length) {
                val next = source[index + 1]
                if (next == '/' || next == '*') break
            }
            index++
        }
        return source.substring(start, index)
    }

    private fun canonicalizeJson5Token(token: String): String {
        if (token.isBlank()) fail("缺少值")
        return when (token) {
            "true", "false", "null" -> token
            "Infinity", "+Infinity", "-Infinity", "NaN", "+NaN", "-NaN" -> "0"
            else -> canonicalizeJson5Number(token)
        }
    }

    private fun canonicalizeJson5Number(token: String): String {
        val normalized = when {
            token.startsWith("+0x", ignoreCase = true) || token.startsWith("-0x", ignoreCase = true) || token.startsWith("0x", ignoreCase = true) -> {
                val sign = if (token.startsWith('-')) "-" else ""
                val digits = token.removePrefix("+").removePrefix("-").removePrefix("0x").removePrefix("0X")
                if (digits.isEmpty() || !digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                    fail("无效十六进制数字 $token")
                }
                val decimal = digits.toLongOrNull(16) ?: fail("十六进制数字过大 $token")
                sign + decimal.toString()
            }
            else -> token.removePrefix("+")
                .replace(Regex("^\\."), "0.")
                .replace(Regex("^-\\."), "-0.")
                .replace(Regex("\\.$"), ".0")
        }
        if (!jsonNumberRegex.matches(normalized)) {
            fail("无效数字 $token")
        }
        return normalized
    }

    private fun skipTrivia() {
        while (index < source.length) {
            when (source[index]) {
                ' ', '\t', '\r', '\n' -> index++
                '/' -> {
                    if (index + 1 >= source.length) return
                    when (source[index + 1]) {
                        '/' -> {
                            index += 2
                            while (index < source.length && source[index] != '\r' && source[index] != '\n') {
                                index++
                            }
                        }
                        '*' -> {
                            index += 2
                            while (index + 1 < source.length && !(source[index] == '*' && source[index + 1] == '/')) {
                                index++
                            }
                            if (index + 1 >= source.length) fail("注释未闭合")
                            index += 2
                        }
                        else -> return
                    }
                }
                else -> return
            }
        }
    }

    private fun expect(ch: Char) {
        skipTrivia()
        if (!consumeIf(ch)) fail("缺少字符 $ch")
    }

    private fun consumeIf(ch: Char): Boolean {
        if (index < source.length && source[index] == ch) {
            index++
            return true
        }
        return false
    }

    private fun isIdentifierStart(ch: Char): Boolean =
        ch == '_' || ch == '$' || ch.isLetter()

    private fun isIdentifierPart(ch: Char): Boolean =
        isIdentifierStart(ch) || ch.isDigit()

    private fun fail(message: String): Nothing {
        throw IllegalArgumentException("${message}，位置${index + 1}")
    }
}

private fun quoteJsonString(text: String): String = buildString(text.length + 2) {
    append('"')
    text.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (ch < ' ') {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
    }
    append('"')
}
