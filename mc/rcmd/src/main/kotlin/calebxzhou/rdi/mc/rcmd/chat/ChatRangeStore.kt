package calebxzhou.rdi.mc.rcmd.chat

interface ChatRangeStore {
    fun load(): ChatRange?

    fun save(range: ChatRange)
}
