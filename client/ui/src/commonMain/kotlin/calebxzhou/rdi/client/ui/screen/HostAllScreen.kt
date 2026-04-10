package calebxzhou.rdi.client.ui.screen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import calebxzhou.rdi.client.ui.McPlayArgs
import calebxzhou.rdi.common.model.McVersion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostAllScreen(
    onBack: (() -> Unit),
    onOpenHostInfo: ((String) -> Unit)? = null,
    onOpenMcVersions: ((McVersion?) -> Unit)? = null,
    onOpenMcPlay: ((McPlayArgs) -> Unit)? = null,
    onOpenTaskList: ((String) -> Unit)? = null
) {
    HostBrowserScreen(
        title = "房间大厅",
        emptyStateText = "暂无可展示的房间",
        listPathForPage = { pageIndex -> "host/list/$pageIndex" },
        onBack = onBack,
        onOpenHostInfo = onOpenHostInfo,
        onOpenMcVersions = onOpenMcVersions,
        onOpenMcPlay = onOpenMcPlay,
        onOpenTaskList = onOpenTaskList
    )
}
