package calebxzhou.rdi.mc.rcmd

object Rcmd {
    const val PREFIX: Char = '\\'

    @JvmStatic
    fun isRcmd(input: String) = input.startsWith(PREFIX)

    @JvmStatic
    fun stripPrefix(input: String) = if (isRcmd(input)) input.substring(1) else input
}
