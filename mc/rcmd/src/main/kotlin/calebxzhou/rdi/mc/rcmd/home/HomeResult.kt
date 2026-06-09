package calebxzhou.rdi.mc.rcmd.home


data class HomeResult(val success: Boolean, val message: String) {
    companion object {
        fun ok(message: String=""): HomeResult {
            return HomeResult(true, message)
        }

        fun error(message: String=""): HomeResult {
            return HomeResult(false, message)
        }
    }
}
