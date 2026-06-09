package calebxzhou.rdi.mc.rcmd

data class RcmdArgument<T>(val name: String, val type: RcmdArgumentType<T>) {
    init {
        require(name.isNotBlank()) { "参数名不能为空" }
    }
}

data class RcmdArgumentParseResult<T>(val value: T, val nextTokenIndex: Int)

interface RcmdArgumentType<T> {
    fun name(): String

    @Throws(RcmdParseException::class)
    fun parse(tokens: List<String>, tokenIndex: Int): RcmdArgumentParseResult<T>
}
