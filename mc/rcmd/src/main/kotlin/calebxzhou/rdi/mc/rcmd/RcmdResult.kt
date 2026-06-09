package calebxzhou.rdi.mc.rcmd

data class RcmdResult(val success: Boolean, val message: String) {
    fun success() = success

    fun message() = message

    companion object {
        @JvmStatic
        fun ok() = RcmdResult(true, "")

        @JvmStatic
        fun ok(message: String) = RcmdResult(true, message)

        @JvmStatic
        fun error(message: String?) = RcmdResult(false, message.orEmpty())
    }
}
