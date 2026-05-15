package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.serialization.Serializable
import calebxzhou.rdi.client.model.ModrinthProjectCardVo
import calebxzhou.rdi.client.model.RemoteModCardVo
import calebxzhou.rdi.client.ui.MainColumn
import calebxzhou.rdi.client.ui.McPlayArgs
import calebxzhou.rdi.client.ui.Space8h
import calebxzhou.rdi.client.ui.TitleTabBar
import calebxzhou.rdi.client.ui.TitleTabItem
import calebxzhou.rdi.client.ui.TitleRow2
import calebxzhou.rdi.common.model.McVersion
import org.bson.types.ObjectId

/**
 * calebxzhou @ 2026-01-13 18:27
 */
typealias ResourceScreenTitleActions = @Composable RowScope.() -> Unit

@Serializable
enum class ResourceTab(
    val icon: String,
    val label: String
) {
    All("\uDB86\uDDD5", "全部整合包"),
    Installed("\uDB86\uDDD7", "已安装整合包"),
    McResources("\uDB80\uDF73", "MC资源"),
    Mods("\uF12E", "模组"),
    ResourcePacks("\uDB80\uDEA2", "资源包"),
    Shaders("\uDB83\uDC4A", "光影包");

    companion object {
        fun fromRouteValue(value: String?): ResourceTab {
            return entries.firstOrNull { it.name == value } ?: All
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceScreen(
    initialCategory: ResourceTab = ResourceTab.All,
    requiredMcVer: McVersion? = null,
    targetHostId: ObjectId? = null,
    onBack: (() -> Unit) = {},
    onOpenUpload: (() -> Unit) = {},
    onOpenModpackInfo: (String) -> Unit = {},
    onOpenPlay: ((McPlayArgs) -> Unit)? = null,
    onOpenTaskList: ((String) -> Unit)? = null
) {
    var category by rememberSaveable(initialCategory) { mutableStateOf(initialCategory) }
    var selectedShader by remember { mutableStateOf<ModrinthProjectCardVo?>(null) }
    var selectedResourcepack by remember { mutableStateOf<ModrinthProjectCardVo?>(null) }
    var selectedRemoteModStack by remember { mutableStateOf<List<RemoteModCardVo>>(emptyList()) }

    val currentShader = selectedShader
    val currentResourcepack = selectedResourcepack
    val currentRemoteMod = selectedRemoteModStack.lastOrNull()
    Box(modifier = Modifier.fillMaxSize()) {
        MainColumn {
            TitleRow2("资源", onBack) {
                TitleTabBar(
                    items = remember {
                        ResourceTab.entries.map { TitleTabItem(it, it.icon, it.label) }
                    },
                    selected = category,
                    onSelect = { category = it }
                )
            }
            Space8h()
            Box(modifier = Modifier.fillMaxSize()) {
                when (category) {
                    ResourceTab.All -> {
                        RemoteModpackScreen(
                            onOpenInfo = onOpenModpackInfo,
                            onOpenUpload = onOpenUpload,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    ResourceTab.Installed -> {
                        InstalledResourcePane(
                            onOpenPlay = onOpenPlay,
                            onOpenTaskList = onOpenTaskList,
                            showMcVersionShortcut = false,
                            showPaneActions = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    ResourceTab.McResources -> {
                        McVersionPane(
                            requiredMcVer = requiredMcVer,
                            onOpenTaskList = onOpenTaskList,
                            showPaneActions = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    ResourceTab.Mods -> {
                        RemoteModScreen(
                            requiredMcVer = requiredMcVer,
                            modifier = Modifier.fillMaxSize(),
                            onOpenMod = { selectedRemoteModStack = listOf(it) }
                        )
                    }

                    ResourceTab.ResourcePacks -> {
                        ResourcepackListScreen(
                            requiredMcVer = requiredMcVer,
                            modifier = Modifier.fillMaxSize(),
                            onOpenResourcepack = { selectedResourcepack = it }
                        )
                    }

                    ResourceTab.Shaders -> {
                        ShaderListScreen(
                            requiredMcVer = requiredMcVer,
                            modifier = Modifier.fillMaxSize(),
                            onOpenShader = { selectedShader = it }
                        )
                    }
                }
            }
        }
        when {
            currentShader != null -> {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    ShaderInfoScreen(
                        projectId = currentShader.projectId,
                        initialTitle = currentShader.title,
                        initialDownloadsText = currentShader.downloadsText,
                        initialFollowsText = currentShader.followsText,
                        onBack = { selectedShader = null }
                    )
                }
            }

            currentResourcepack != null -> {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    ResourcepackInfoScreen(
                        projectId = currentResourcepack.projectId,
                        initialTitle = currentResourcepack.title,
                        initialDownloadsText = currentResourcepack.downloadsText,
                        initialFollowsText = currentResourcepack.followsText,
                        onBack = { selectedResourcepack = null }
                    )
                }
            }

            currentRemoteMod != null -> {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    RemoteModInfoScreen(
                        mod = currentRemoteMod,
                        onBack = {
                            selectedRemoteModStack = selectedRemoteModStack.dropLast(1)
                        },
                        onOpenDependencyMod = {
                            selectedRemoteModStack = selectedRemoteModStack + it
                        },
                        targetHostId = targetHostId,
                        targetHostMcVer = requiredMcVer
                    )
                }
            }

        }
    }
}
