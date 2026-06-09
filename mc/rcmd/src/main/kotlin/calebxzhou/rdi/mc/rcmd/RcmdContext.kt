package calebxzhou.rdi.mc.rcmd

class RcmdContext(
    val source: RcmdSource,
    val rawInput: String,
    val spec: RcmdCommandSpec,
    tokens: List<String>,
    arguments: Map<String, Any>
) {
    val tokens: List<String> = tokens.toList()
    val arguments: Map<String, Any> = arguments.toMap()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(name: String): T = arguments[name] as T

    fun getString(name: String): String = get(name)

    fun getBool(name: String): Boolean = get(name)

    fun getInt(name: String): Int = get(name)

    fun getLong(name: String): Long = get(name)

    fun getDouble(name: String): Double = get(name)
}
