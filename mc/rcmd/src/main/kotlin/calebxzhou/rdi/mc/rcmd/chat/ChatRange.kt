package calebxzhou.rdi.mc.rcmd.chat

enum class ChatRange(val displayName: String) {
    HOST("本房间"),
    GLOBAL("公共");


    companion object {
        fun fromRcmdValue(value: String): ChatRange {
            return when (value.lowercase()) {
                "global" -> ChatRange.GLOBAL
                else -> ChatRange.HOST
            }
        }
    }
}
