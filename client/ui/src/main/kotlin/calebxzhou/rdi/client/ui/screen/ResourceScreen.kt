package calebxzhou.rdi.client.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.TitleTabBar
import calebxzau.rdi.client.ui.TitleTabItem
import calebxzhou.rdi.client.model.ModrinthProjectCardVo
import calebxzhou.rdi.client.modcatalog.CatalogMod
import calebxzhou.rdi.client.modcatalog.ModCatalog
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.common.model.McVersion
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * calebxzhou @ 2026-01-13 18:27
 */
typealias ResourceScreenTitleActions = @Composable RowScope.() -> Unit

private const val RESOURCE_TAB_FADE_DURATION_MS = 140
private const val RESOURCE_TAB_SLIDE_DURATION_MS = 180

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ResourceScreen(
    modCatalog: ModCatalog,
    initialCategory: ResourceTab = ResourceTab.All,
    requiredMcVer: McVersion? = null,
    targetHostId: ObjectId? = null,
    targetHost2Id: String? = null,
    onBack: (() -> Unit) = {},
    onOpenUpload: (() -> Unit) = {},
    onOpenModpackInfo: (String) -> Unit = {},
    onOpenPlay: ((McPlayArgs) -> Unit)? = null,
    onOpenTaskList: ((String) -> Unit)? = null
) {
    var category by rememberSaveable(initialCategory) { mutableStateOf(initialCategory) }
    var selectedShader by remember { mutableStateOf<ModrinthProjectCardVo?>(null) }
    var selectedResourcepack by remember { mutableStateOf<ModrinthProjectCardVo?>(null) }
    var selectedRemoteModStack by remember { mutableStateOf<List<CatalogMod>>(emptyList()) }

    val currentShader = selectedShader
    val currentResourcepack = selectedResourcepack
    val currentRemoteMod = selectedRemoteModStack.lastOrNull()
    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            TitleRow("资源", onBack) {
                TitleTabBar(
                    items = remember {
                        ResourceTab.entries.map { TitleTabItem(it, it.icon, it.label) }
                    },
                    selected = category,
                    onSelect = { category = it }
                )
            }

            ContentBody {
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = category,
                        transitionSpec = {
                            val forward =
                                ResourceTab.entries.indexOf(targetState) > ResourceTab.entries.indexOf(initialState)
                            val direction = if (forward) 1 else -1
                            (slideInHorizontally(
                                animationSpec = tween(RESOURCE_TAB_SLIDE_DURATION_MS),
                                initialOffsetX = { it / 10 * direction }
                            ) + fadeIn(animationSpec = tween(RESOURCE_TAB_FADE_DURATION_MS))) togetherWith
                                    (slideOutHorizontally(
                                        animationSpec = tween(RESOURCE_TAB_SLIDE_DURATION_MS),
                                        targetOffsetX = { -it / 10 * direction }
                                    ) + fadeOut(animationSpec = tween(RESOURCE_TAB_FADE_DURATION_MS))) using
                                    SizeTransform(clip = false)
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "ResourceTabContent"
                    ) { activeCategory ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (activeCategory) {
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
                                        catalog = modCatalog,
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
                            Box(modifier = Modifier.fillMaxSize()) {
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
                            Box(modifier = Modifier.fillMaxSize()) {
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
                            Box(modifier = Modifier.fillMaxSize()) {
                                RemoteModInfoScreen(
                                    catalog = modCatalog,
                                    mod = currentRemoteMod,
                                    onBack = {
                                        selectedRemoteModStack = selectedRemoteModStack.dropLast(1)
                                    },
                                    onOpenDependencyMod = {
                                        selectedRemoteModStack = selectedRemoteModStack + it
                                    },
                                    targetHostId = targetHostId,
                                    targetHost2Id = targetHost2Id,
                                    targetHostMcVer = requiredMcVer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
