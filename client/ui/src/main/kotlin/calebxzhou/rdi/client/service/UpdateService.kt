package calebxzhou.rdi.client.service

object UpdateService {
    suspend fun startUpdateFlow(
        onStatus: (String) -> Unit,
        onDetail: (String) -> Unit
    ): UpdateFlowResult = startUpdateFlow(
        onStatus = onStatus,
        onDetail = onDetail,
        updateMcCores = McCoreUpdater::update,
        updateUpdater = UpdaterUpdater::update
    )

    internal suspend fun startUpdateFlow(
        onStatus: (String) -> Unit,
        onDetail: (String) -> Unit,
        updateMcCores: suspend ((String) -> Unit, (String) -> Unit) -> Result<McCoreUpdateResult>,
        updateUpdater: suspend ((String) -> Unit, (String) -> Unit) -> Result<UpdaterUpdateResult>
    ): UpdateFlowResult {
        onStatus("正在检查更新...")

        val mcCoreResult = updateMcCores(onStatus, onDetail).onFailure {
            it.printStackTrace()
            onDetail("MC核心更新失败: ${it.message ?: "未知错误"}")
        }
        val updaterResult = updateUpdater(onStatus, onDetail).onFailure {
            it.printStackTrace()
            onDetail("启动程序更新失败，已继续启动: ${it.message ?: "未知错误"}")
        }

        val result = UpdateFlowResult(mcCoreResult, updaterResult)
        onStatus(result.statusText)
        if (result.succeeded) onDetail("")
        return result
    }
}

data class UpdateFlowResult(
    val mcCore: Result<McCoreUpdateResult>,
    val updater: Result<UpdaterUpdateResult>
) {
    val succeeded
        get() = mcCore.isSuccess && updater.isSuccess

    val statusText: String
        get() {
            val mcCoreUpdate = mcCore.getOrNull()
            val updaterUpdate = updater.getOrNull()
            return when {
                mcCore.isFailure && updater.isFailure -> "核心和启动程序更新失败"
                mcCore.isFailure && updaterUpdate == UpdaterUpdateResult.UPDATED ->
                    "MC核心更新失败，启动程序更新完成，下次启动生效"
                mcCore.isFailure -> "MC核心更新失败，启动程序检查已完成"
                updater.isFailure -> "核心更新完成，启动程序更新失败"
                updaterUpdate == UpdaterUpdateResult.UPDATED -> "启动程序更新完成，下次启动生效"
                updaterUpdate == UpdaterUpdateResult.SKIPPED_LOG_MODE ->
                    if (mcCoreUpdate?.updatedCount == 0) "核心已是最新版，日志模式下已跳过启动程序更新"
                    else "核心更新完成，日志模式下已跳过启动程序更新"
                mcCoreUpdate?.updatedCount == 0 -> "当前已是最新版核心"
                else -> "核心更新完成"
            }
        }
}
