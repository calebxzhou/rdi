package calebxzhou.rdi.mc.rcmd

interface RcmdServerCommandHandler {
    fun ping(context: RcmdContext): RcmdResult

    fun setChatRange(context: RcmdContext): RcmdResult

    fun requestTpa(context: RcmdContext): RcmdResult

    fun acceptTpa(context: RcmdContext): RcmdResult

    fun setHome(context: RcmdContext): RcmdResult

    fun goHome(context: RcmdContext): RcmdResult

    fun listHome(context: RcmdContext): RcmdResult

    fun deleteHome(context: RcmdContext): RcmdResult

    fun togglePosLock(context: RcmdContext): RcmdResult

    fun setFirmSection(context: RcmdContext): RcmdResult

    fun unsetFirmSection(context: RcmdContext): RcmdResult

    fun listFirmSections(context: RcmdContext): RcmdResult

    fun setFirmSectionAutoSet(context: RcmdContext): RcmdResult

    fun testEntity(context: RcmdContext): RcmdResult
}
