package calebxzhou.rdi.mc.rcmd

object RcmdParser {
    @JvmStatic
    @Throws(RcmdParseException::class)
    fun tokenize(input: String?): List<String> {
        if (input.isNullOrEmpty()) {
            return emptyList()
        }
        val tokens = mutableListOf<String>()
        val token = StringBuilder()
        var quoted = false
        var quoteChar = '\u0000'
        var escaping = false

        for (c in input) {
            when {
                escaping -> {
                    token.append(c)
                    escaping = false
                }
                c == '\\' -> escaping = true
                quoted && c == quoteChar -> quoted = false
                quoted -> token.append(c)
                c == '"' || c == '\'' -> {
                    quoted = true
                    quoteChar = c
                }
                c.isWhitespace() -> addToken(tokens, token)
                else -> token.append(c)
            }
        }

        if (escaping) {
            token.append('\\')
        }
        if (quoted) {
            throw RcmdParseException("引号未闭合")
        }
        addToken(tokens, token)
        return tokens
    }

    private fun addToken(tokens: MutableList<String>, token: StringBuilder) {
        if (token.isEmpty()) {
            return
        }
        tokens += token.toString()
        token.clear()
    }
}
