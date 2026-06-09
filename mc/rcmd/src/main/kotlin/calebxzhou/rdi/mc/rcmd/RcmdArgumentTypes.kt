package calebxzhou.rdi.mc.rcmd

object RcmdArgumentTypes {
    @JvmField
    val BOOL: RcmdArgumentType<Boolean> = BoolType()

    @JvmField
    val INT: RcmdArgumentType<Int> = IntType()

    @JvmField
    val LONG: RcmdArgumentType<Long> = LongType()

    @JvmField
    val DOUBLE: RcmdArgumentType<Double> = DoubleType()

    @JvmField
    val STRING: RcmdArgumentType<String> = StringType()

    @JvmField
    val MESSAGE: RcmdArgumentType<String> = MessageType()

    @JvmStatic
    fun enumOf(vararg values: String): RcmdArgumentType<String> {
        require(values.isNotEmpty()) { "enum选项不能为空" }
        return EnumType(values)
    }

    private fun requireToken(tokens: List<String>, tokenIndex: Int, typeName: String): String {
        if (tokenIndex >= tokens.size) {
            throw RcmdParseException("缺少${typeName}参数")
        }
        return tokens[tokenIndex]
    }

    class BoolType : RcmdArgumentType<Boolean> {
        override fun name() = "bool"

        override fun parse(tokens: List<String>, tokenIndex: Int): RcmdArgumentParseResult<Boolean> {
            val token = requireToken(tokens, tokenIndex, name())
            val value = when (token.lowercase()) {
                "true", "on", "yes", "1" -> true
                "false", "off", "no", "0" -> false
                else -> throw RcmdParseException("需要bool参数，收到：$token")
            }
            return RcmdArgumentParseResult(value, tokenIndex + 1)
        }
    }

    class IntType : RcmdArgumentType<Int> {
        override fun name() = "int"

        override fun parse(tokens: List<String>, tokenIndex: Int): RcmdArgumentParseResult<Int> {
            val token = requireToken(tokens, tokenIndex, name())
            return RcmdArgumentParseResult(
                token.toIntOrNull() ?: throw RcmdParseException("需要int参数，收到：$token"),
                tokenIndex + 1
            )
        }
    }

    class LongType : RcmdArgumentType<Long> {
        override fun name() = "long"

        override fun parse(tokens: List<String>, tokenIndex: Int): RcmdArgumentParseResult<Long> {
            val token = requireToken(tokens, tokenIndex, name())
            return RcmdArgumentParseResult(
                token.toLongOrNull() ?: throw RcmdParseException("需要long参数，收到：$token"),
                tokenIndex + 1
            )
        }
    }

    class DoubleType : RcmdArgumentType<Double> {
        override fun name() = "double"

        override fun parse(tokens: List<String>, tokenIndex: Int): RcmdArgumentParseResult<Double> {
            val token = requireToken(tokens, tokenIndex, name())
            return RcmdArgumentParseResult(
                token.toDoubleOrNull() ?: throw RcmdParseException("需要double参数，收到：$token"),
                tokenIndex + 1
            )
        }
    }

    class StringType : RcmdArgumentType<String> {
        override fun name() = "string"

        override fun parse(tokens: List<String>, tokenIndex: Int) =
            RcmdArgumentParseResult(requireToken(tokens, tokenIndex, name()), tokenIndex + 1)
    }

    class MessageType : RcmdArgumentType<String> {
        override fun name() = "message"

        override fun parse(tokens: List<String>, tokenIndex: Int): RcmdArgumentParseResult<String> {
            if (tokenIndex >= tokens.size) {
                throw RcmdParseException("需要message参数")
            }
            return RcmdArgumentParseResult(tokens.drop(tokenIndex).joinToString(" "), tokens.size)
        }
    }

    private class EnumType(values: Array<out String>) : RcmdArgumentType<String> {
        private val values: Set<String> = values.mapTo(linkedSetOf()) {
            require(it.isNotBlank()) { "enum选项不能包含空值" }
            it.lowercase()
        }

        override fun name() = "enum${values.toTypedArray().contentToString()}"

        override fun parse(tokens: List<String>, tokenIndex: Int): RcmdArgumentParseResult<String> {
            val token = requireToken(tokens, tokenIndex, "enum")
            val normalized = token.lowercase()
            if (normalized in values) {
                return RcmdArgumentParseResult(normalized, tokenIndex + 1)
            }
            val matches = values.filter { it.startsWith(normalized) }
            return when (matches.size) {
                0 -> throw RcmdParseException("未知enum选项：$token，可用：${values.joinToString("、")}")
                1 -> RcmdArgumentParseResult(matches.first(), tokenIndex + 1)
                else -> throw RcmdParseException("enum选项前缀不明确：$token，可匹配：${matches.joinToString("、")}")
            }
        }
    }
}
