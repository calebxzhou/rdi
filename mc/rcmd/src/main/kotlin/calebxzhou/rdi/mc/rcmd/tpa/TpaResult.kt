package calebxzhou.rdi.mc.rcmd.tpa


data class TpaResult(val success: Boolean, val message: String) {
    companion object {
        fun ok(message: String): TpaResult {
            return TpaResult(true, message)
        }

        fun error(message: String): TpaResult {
            return TpaResult(false, message)
        }
    }
}
