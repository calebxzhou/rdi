package calebxzhou.rdi.mc.rcmd

data class RcmdDispatchResult(val found: Boolean, val result: RcmdResult) {
    fun found() = found

    fun result() = result

    companion object {
        @JvmStatic
        fun found(result: RcmdResult) = RcmdDispatchResult(true, result)

        @JvmStatic
        fun notFound() = RcmdDispatchResult(false, RcmdResult.ok())
    }
}
