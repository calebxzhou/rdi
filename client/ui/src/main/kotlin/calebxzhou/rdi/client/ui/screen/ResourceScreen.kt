package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.KeepAliveAnimatedTabHost
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.TitleTabBar
import calebxzau.rdi.client.ui.TitleTabItem
import calebxzhou.rdi.client.model.ModrinthProjectCardVo
import calebxzhou.rdi.client.modcatalog.CatalogMod
import calebxzhou.rdi.client.modcatalog.ModCatalog
import calebxzhou.rdi.client.service.ModpackLocalDir
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import kotlinx.serialization.Serializable

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

enum class ModpackContentType(val icon: String, val label: String) {
    Mods("\uF12E", "模组"),
    ResourcePacks("\uDB80\uDEA2", "资源包"),
    Shaders("\uDB83\uDC4A", "光影包")
}

private data class ModpackContentTarget(
    val pack: ModpackLocalDir,
    val type: ModpackContentType
)

private val topLevelResourceTabs = listOf(ResourceTab.All, ResourceTab.Installed, ResourceTab.McResources)

@Composable
fun ResourceScreen(
    modCatalog: ModCatalog,
    initialCategory: ResourceTab = ResourceTab.All,
    requiredMcVer: McVersion? = null,
    requiredLoader: ModLoader? = null,
    onBack: (() -> Unit) = {},
    onOpenUpload: (() -> Unit) = {},
    onOpenModpackInfo: (String) -> Unit = {},
    onOpenRemoteMod: (CatalogMod, ModpackLocalDir?) -> Unit = { _, _ -> },
    onOpenResourceInfo: (ModrinthProjectCardVo, ResourceInfoType, ModpackLocalDir?) -> Unit = { _, _, _ -> },
    onOpenPlay: ((McPlayArgs) -> Unit)? = null,
    onOpenTaskList: ((String) -> Unit)? = null
) {
    var category by rememberSaveable(initialCategory) { mutableStateOf(initialCategory) }
    var contentTarget by remember { mutableStateOf<ModpackContentTarget?>(null) }
    val localTarget = contentTarget

    fun closeContentTarget() {
        contentTarget = null
        category = ResourceTab.Installed
    }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            when {
                localTarget != null -> TitleRow(
                    "${localTarget.pack.vo.name} ${localTarget.pack.verName} · ${localTarget.type.label}",
                    ::closeContentTarget
                ) {
                    Text(
                        "${localTarget.pack.vo.mcVer.mcVer} · ${localTarget.pack.vo.modloader.displayName}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                category !in topLevelResourceTabs -> TitleRow("添加${category.label}", onBack)

                else -> TitleRow("资源", onBack) {
                    TitleTabBar(
                        items = remember {
                            topLevelResourceTabs.map { TitleTabItem(it, it.icon, it.label) }
                        },
                        selected = category,
                        onSelect = { category = it }
                    )
                }
            }

            ContentBody {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (localTarget != null) {
                        when (localTarget.type) {
                            ModpackContentType.Mods -> RemoteModScreen(
                                catalog = modCatalog,
                                requiredMcVer = localTarget.pack.vo.mcVer,
                                requiredLoader = localTarget.pack.vo.modloader,
                                modifier = Modifier.fillMaxSize(),
                                onOpenMod = { onOpenRemoteMod(it, localTarget.pack) }
                            )

                            ModpackContentType.ResourcePacks -> ResourcepackListScreen(
                                requiredMcVer = localTarget.pack.vo.mcVer,
                                modifier = Modifier.fillMaxSize(),
                                onOpenResourcepack = {
                                    onOpenResourceInfo(it, ResourceInfoType.ResourcePack, localTarget.pack)
                                }
                            )

                            ModpackContentType.Shaders -> ShaderListScreen(
                                requiredMcVer = localTarget.pack.vo.mcVer,
                                modifier = Modifier.fillMaxSize(),
                                onOpenShader = {
                                    onOpenResourceInfo(it, ResourceInfoType.Shader, localTarget.pack)
                                }
                            )
                        }
                    } else {
                        KeepAliveAnimatedTabHost(
                            selected = category,
                            order = ResourceTab.entries::indexOf,
                            modifier = Modifier.fillMaxSize(),
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
                                        onOpenContent = { pack, type ->
                                            contentTarget = ModpackContentTarget(pack, type)
                                        },
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
                                        requiredLoader = requiredLoader,
                                        modifier = Modifier.fillMaxSize(),
                                        onOpenMod = { onOpenRemoteMod(it, null) }
                                    )
                                }

                                ResourceTab.ResourcePacks -> {
                                    ResourcepackListScreen(
                                        requiredMcVer = requiredMcVer,
                                        modifier = Modifier.fillMaxSize(),
                                        onOpenResourcepack = {
                                            onOpenResourceInfo(it, ResourceInfoType.ResourcePack, null)
                                        }
                                    )
                                }

                                ResourceTab.Shaders -> {
                                    ShaderListScreen(
                                        requiredMcVer = requiredMcVer,
                                        modifier = Modifier.fillMaxSize(),
                                        onOpenShader = {
                                            onOpenResourceInfo(it, ResourceInfoType.Shader, null)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

private val ModLoader.displayName: String
    get() = when (this) {
        ModLoader.forge -> "Forge"
        ModLoader.neoforge -> "NeoForge"
        ModLoader.cleanroom -> "Cleanroom"
    }
