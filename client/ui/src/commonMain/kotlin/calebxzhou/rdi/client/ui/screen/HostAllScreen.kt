package calebxzhou.rdi.client.ui.screen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import calebxzhou.rdi.client.ui.McPlayArgs
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Task

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostAllScreen(
    onBack: (() -> Unit),
    onOpenHostInfo: ((String) -> Unit)? = null,
    onOpenMcVersions: ((McVersion?) -> Unit)? = null,
    onOpenMcPlay: ((McPlayArgs) -> Unit)? = null,
    onOpenTask: ((Task) -> Unit)? = null
) {
    HostBrowserScreen(
        title = "地图大厅",
        emptyStateText = "暂无可展示的地图",
        listPathForPage = { pageIndex -> "host/list/$pageIndex" },
        onBack = onBack,
        onOpenHostInfo = onOpenHostInfo,
        onOpenMcVersions = onOpenMcVersions,
        onOpenMcPlay = onOpenMcPlay,
        onOpenTask = onOpenTask
    )
}
