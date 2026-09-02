package calebxzhou.rdi.client.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.RColumn
import calebxzau.rdi.client.ui.RRow
import calebxzau.rdi.client.ui.RScrollableColumn
import calebxzau.rdi.client.ui.RSwitch
import calebxzau.rdi.client.ui.RTextField
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.asIconText
import calebxzau.rdi.client.ui.themeNow
import calebxzhou.rdi.common.util.humanFileSize
import calebxzhou.rdi.client.net.RServer
// import calebxzhou.rdi.client.service.LocalMinecraftReuseService
// import calebxzhou.rdi.client.service.LocalMinecraftScanState
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.common.model.DownloadQuota
import calebxzau.rdi.client.ui.viewmodel.SettingsDraft
import calebxzau.rdi.client.ui.viewmodel.SettingsEvent
import calebxzau.rdi.client.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.collect
import org.koin.compose.viewmodel.koinViewModel

/**
 * calebxzhou @ 2026-01-24 18:36
 */
private const val SETTING_PAGE_SLIDE_DURATION_MS = 180
private const val SETTING_PAGE_FADE_DURATION_MS = 120

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun SettingScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var category by remember { mutableStateOf(SettingCategory.General) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = uiState.draft

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            TitleRow("设置", onBack) {
                if (uiState.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = themeNow.primary
                    )
                    Space8w()
                }
                CircleIconButton(
                    icon = "\uF0C7",
                    label = "保存",
                    bgColor = themeNow.primary,
                    enabled = !uiState.saving
                ) {
                    viewModel.save()
                }
            }
            ContentBody {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val compactNav = maxWidth < 860.dp
                Row(modifier = Modifier.fillMaxSize()) {
                    SettingNav(
                        selected = category,
                        onSelect = { category = it },
                        compact = compactNav
                    )
                    Spacer(modifier = Modifier.width(if (compactNav) 8.dp else 16.dp))
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = category,
                            transitionSpec = {
                                val forward =
                                    SettingCategory.entries.indexOf(targetState) > SettingCategory.entries.indexOf(
                                        initialState
                                    )
                                val direction = if (forward) 1 else -1
                                (slideInVertically(
                                    animationSpec = tween(SETTING_PAGE_SLIDE_DURATION_MS),
                                    initialOffsetY = { it / 12 * direction }
                                ) + fadeIn(animationSpec = tween(SETTING_PAGE_FADE_DURATION_MS))) togetherWith
                                        (slideOutVertically(
                                            animationSpec = tween(SETTING_PAGE_SLIDE_DURATION_MS),
                                            targetOffsetY = { -it / 12 * direction }
                                        ) + fadeOut(animationSpec = tween(SETTING_PAGE_FADE_DURATION_MS))) using
                                        SizeTransform(clip = false)
                            },
                            modifier = Modifier.fillMaxSize(),
                            label = "SettingPageContent"
                        ) { activeCategory ->
                            RScrollableColumn(modifier = Modifier.fillMaxSize()) {
                                when (activeCategory) {
                                    SettingCategory.General -> {
                                        GeneralSettings(
                                            solidWindow = draft.solidWindow,
                                            onSolidWindowChange = {
                                                viewModel.updateDraft(draft.copy(solidWindow = it))
                                            }
                                        )
                                    }

                                    SettingCategory.Java -> {
                                        JavaSettings(
                                            totalMemoryMb = uiState.totalMemoryMb,
                                            maxMemoryText = draft.maxMemoryText,
                                            onMaxMemoryChange = {
                                                viewModel.updateDraft(draft.copy(maxMemoryText = it.trim()))
                                            }
                                        )
                                    }

                                    SettingCategory.Network -> {
                                        NetworkSettings(
                                            draft = draft,
                                            onDraftChange = viewModel::updateDraft,
                                            switchingNode = uiState.switchingNode,
                                            downloadQuota = uiState.downloadQuota,
                                            downloadQuotaLoading = uiState.downloadQuotaLoading,
                                            downloadQuotaError = uiState.downloadQuotaError,
                                            onRefreshDownloadQuota = viewModel::refreshDownloadQuota,
                                            onAutoSwitchFastestNode = { viewModel.switchNode() },
                                            onUseGameBackupNode = { viewModel.switchNode(gameBackup = true) },
                                            onUseMainNode = { viewModel.switchNode(forceMain = true) }
                                        )
                                    }

                                    //SettingCategory.Minecraft -> LocalMinecraftSettings()

                                }
                                uiState.errorMessage?.let {
                                    Text(
                                        it,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
    }


    private enum class SettingCategory(val icon: String, val label: String) {
        General("\uF013", "常用"),
        Java("\uEDAF", "Java"),
        Network("\uEF09", "网络");
        /** Whether this category is visible on the current platform */
        val visible: Boolean
            get() = when (this) {
              //  Minecraft -> System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
                else -> true
            }
    }

    @Composable
    private fun SettingNav(
        selected: SettingCategory,
        onSelect: (SettingCategory) -> Unit,
        compact: Boolean = false
    ) {
        NavigationRail(
            modifier = Modifier
                .width(if (compact) 96.dp else 112.dp)
                .fillMaxHeight(),
            containerColor = Color.Transparent
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SettingCategory.entries.filter { it.visible }.forEach { category ->
                NavigationRailItem(
                    selected = category == selected,
                    onClick = { onSelect(category) },
                    icon = {
                        SettingNavIcon(category.icon)
                    },
                    label = {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    alwaysShowLabel = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }
    }

    @Composable
    private fun SettingNavIcon(icon: String) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon.asIconText,
                fontSize = 24.sp,
                lineHeight = 24.sp,
                maxLines = 1
            )
        }
    }

    
    @Composable
    private fun GeneralSettings(
        solidWindow: Boolean,
        onSolidWindowChange: (Boolean) -> Unit
    ) {
        RColumn {
            RRow {
                RSwitch(checked = solidWindow, onCheckedChange = onSolidWindowChange)
                Text("使用实心窗口解决窗口不显示的问题")
            }
        }
    }

    @Composable
    private fun JavaSettings(
        totalMemoryMb: Int,
        maxMemoryText: String,
        onMaxMemoryChange: (String) -> Unit
    ) {
        RColumn {
            RRow {
                Text("总内存 ${totalMemoryMb}MB")
                RTextField("限制MC内存", maxMemoryText, modifier = Modifier.width(140.dp)) { onMaxMemoryChange(it) }
                Text("MB")
            }
        }
    }

    /*@Composable
    private fun LocalMinecraftSettings() {
        val scanState by LocalMinecraftReuseService.state.collectAsState()
        val scanning = scanState is LocalMinecraftScanState.Scanning
        val status = when (val current = scanState) {
            LocalMinecraftScanState.Idle -> "等待扫描"
            is LocalMinecraftScanState.Scanning -> "扫描中，已记录${current.previousCount}个实例"
            is LocalMinecraftScanState.Completed -> "已发现${current.foundCount}个实例"
            is LocalMinecraftScanState.Failed -> "扫描失败，仍可使用${current.foundCount}个已记录实例"
        }
        RColumn {
            RRow {
                Text("本地Minecraft实例")
                Text(status)
                CircleIconButton(
                    icon = "\uF021",
                    tooltip = if (scanning) "扫描中" else "重新扫描",
                    showText = false,
                    enabled = !scanning,
                    onClick = LocalMinecraftReuseService::rescan
                )
            }
            Text("每12h自动扫描本机Minecraft实例和Downloads，优先复用相同文件")
        }
    }*/

    
    @Composable
    private fun NetworkSettings(
        draft: SettingsDraft,
        onDraftChange: (SettingsDraft) -> Unit,
        switchingNode: Boolean,
        downloadQuota: DownloadQuota.Vo?,
        downloadQuotaLoading: Boolean,
        downloadQuotaError: String?,
        onRefreshDownloadQuota: () -> Unit,
        onAutoSwitchFastestNode: () -> Unit,
        onUseGameBackupNode: () -> Unit,
        onUseMainNode: () -> Unit,
    ) {
        RColumn {
            RRow {
                Text("使用BMCL-API国内镜像")
                RSwitch(
                    checked = draft.preferMcMirror,
                    onCheckedChange = { onDraftChange(draft.copy(preferMcMirror = it)) }
                )
                Text("下载MC资源")
                RSwitch(
                    checked = draft.preferModMirror,
                    onCheckedChange = { onDraftChange(draft.copy(preferModMirror = it)) }
                )
                Text("下载Mod")
            }
            RRow {
                Text("RDI CDN下载额度")
                val quotaText = when {
                    downloadQuotaLoading && downloadQuota == null -> "读取中"
                    downloadQuotaError != null && downloadQuota == null -> "读取失败"
                    else -> downloadQuota?.let {
                        "今日剩余${it.remainingBytes.humanFileSize}/${it.limitBytes.humanFileSize}"
                    } ?: "--"
                }
                Text(
                    text = quotaText,
                    color = if (downloadQuotaError != null && downloadQuota == null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    }
                )
                CircleIconButton(
                    icon = "\uF021",
                    tooltip = if (downloadQuotaLoading) "刷新中" else "刷新下载额度",
                    showText = false,
                    enabled = !downloadQuotaLoading,
                    onClick = onRefreshDownloadQuota
                )
            }
            AutoRouteStatus(
                switchingNode = switchingNode,
                onAutoSwitchFastestNode = onAutoSwitchFastestNode,
                onUseGameBackupNode = onUseGameBackupNode,
                onUseMainNode
            )
            val mode = when {
                !draft.proxyEnabled -> 0
                draft.proxySystem -> 1
                else -> 2
            }
            RRow {
                Text("代理")
                RadioButton(selected = mode == 0, onClick = {
                    onDraftChange(draft.copy(proxyEnabled = false, proxySystem = false))
                })
                Text("无代理")
                RadioButton(selected = mode == 1, onClick = {
                    onDraftChange(draft.copy(proxyEnabled = true, proxySystem = true))
                })
                Text("系统代理")
                RadioButton(selected = mode == 2, onClick = {
                    onDraftChange(draft.copy(proxyEnabled = true, proxySystem = false))
                })
                Text("自定义代理")
            }
            if (mode == 2) {
                RRow {

                    RTextField(
                        "主机",
                        value = draft.proxyHost,
                        onValueChange = { onDraftChange(draft.copy(proxyHost = it)) },
                        modifier = Modifier.width(240.dp)
                    )
                    RTextField(
                        "端口",
                        value = draft.proxyPortText,
                        onValueChange = { onDraftChange(draft.copy(proxyPortText = it)) },
                        modifier = Modifier.width(120.dp)
                    )
                    RTextField(
                        label = "用户名（可选）",
                        value = draft.proxyUsr,
                        onValueChange = { onDraftChange(draft.copy(proxyUsr = it)) },
                        modifier = Modifier.width(240.dp)
                    )
                    RTextField(
                        label = "密码（可选）",
                        value = draft.proxyPwd,
                        onValueChange = { onDraftChange(draft.copy(proxyPwd = it)) },
                        modifier = Modifier.width(240.dp)
                    )
                }
            }
        }
    }

    
    @Composable
    private fun AutoRouteStatus(
        switchingNode: Boolean,
        onAutoSwitchFastestNode: () -> Unit,
        onUseGameBackupNode: () -> Unit,
        onUseMainNode: () -> Unit,
    ) {
        val routeState by RServer.routeState.collectAsState()
        RRow {
            routeState.nodeName?.let {
                Text("当前节点 $it")
            }
            Text(if (routeState.useBackupNode) "加速入口" else "主入口")
            CircleIconButton(
                "\uDB80\uDC02",
                if (switchingNode) "已切换节点" else "自动节点",
                bgColor = themeNow.tertiary,
                enabled = !switchingNode,
                onClick = onAutoSwitchFastestNode
            )
            CircleIconButton(
                "\uDB80\uDC02",
                "临时备用节点",
                bgColor = MaterialTheme.colorScheme.primary,
                enabled = !switchingNode,
                onClick = onUseGameBackupNode
            )
            CircleIconButton(
                "\uDB80\uDC02",
                "临时主用节点",
                bgColor = themeNow.tertiary,
                enabled = !switchingNode,
                onClick = onUseMainNode
            )
        }
    }

