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
import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.rdi.client.*
import calebxzhou.rdi.client.net.RServer
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.NodeRefreshCoordinator
// import calebxzhou.rdi.client.service.LocalMinecraftReuseService
// import calebxzhou.rdi.client.service.LocalMinecraftScanState
import calebxzhou.rdi.client.service.SettingsService
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.common.model.DownloadQuota
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * calebxzhou @ 2026-01-24 18:36
 */
private const val SETTING_PAGE_SLIDE_DURATION_MS = 180
private const val SETTING_PAGE_FADE_DURATION_MS = 120

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun SettingScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var category by remember { mutableStateOf(SettingCategory.General) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var switchingNode by remember { mutableStateOf(false) }

    // Launcher settings state
    var preferModMirror by remember { mutableStateOf(false) }
    var preferMcMirror by remember { mutableStateOf(false) }
    var maxMemoryText by remember { mutableStateOf("") }
    var proxyEnabled by remember { mutableStateOf(false) }
    var proxySystem by remember { mutableStateOf(false) }
    var proxyHost by remember { mutableStateOf("127.0.0.1") }
    var proxyPortText by remember { mutableStateOf("10808") }
    var proxyUsr by remember { mutableStateOf("") }
    var proxyPwd by remember { mutableStateOf("") }
    var solidWindow by remember { mutableStateOf(false) }
    var totalMemoryMb by remember { mutableStateOf(0) }
    // Load config on all platforms
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val config = AppConfig.load()
                preferModMirror = config.preferModMirror
                preferMcMirror = config.preferMcMirror
                maxMemoryText = if (config.maxMemory <= 0) "" else config.maxMemory.toString()
                proxyEnabled = config.proxyConfig?.enabled ?: false
                proxySystem = config.proxyConfig?.systemProxy ?: false
                proxyHost = config.proxyConfig?.host ?: "127.0.0.1"
                proxyPortText = (config.proxyConfig?.port ?: 10808).toString()
                proxyUsr = config.proxyConfig?.usr.orEmpty()
                proxyPwd = config.proxyConfig?.pwd.orEmpty()
                solidWindow = config.solidWindow
                totalMemoryMb = calebxzhou.rdi.client.service.SettingsService.getTotalPhysicalMemoryMb()
            }
        }
    }

    fun switchNode(
        gameBackup: Boolean = false,
        forceMain: Boolean = false
    ) {
        if (!switchingNode) {
            switchingNode = true
            scope.launch {
                NodeRefreshCoordinator.refreshCurrent(gameBackup, forceMain)
                    .onSuccess {
                        snackbarHostState.showSnackbar("已临时切换到${it.nodeName}")
                    }
                    .onFailure {
                        snackbarHostState.showSnackbar(
                            it.message ?: "备用节点刷新失败"
                        )
                    }
                switchingNode = false
            }
        }
    }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            TitleRow("设置", onBack) {
                if (saving) {
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
                    enabled = !saving
                ) {
                    if (saving) return@CircleIconButton
                    saving = true
                    scope.launch {
                        val svc = SettingsService

                        val memoryValidation = svc.validateMemory(maxMemoryText, totalMemoryMb)
                        if (!memoryValidation.success) {
                            errorMessage = memoryValidation.errorMessage
                            saving = false
                            return@launch
                        }
                        val proxyValidation = svc.validateProxyPort(proxyPortText)
                        if (!proxyValidation.success) {
                            errorMessage = proxyValidation.errorMessage
                            saving = false
                            return@launch
                        }
                        svc.saveSettings(
                            preferModMirror = preferModMirror,
                            preferMcMirror = preferMcMirror,
                            maxMemoryText = maxMemoryText,
                            proxyEnabled = proxyEnabled,
                            proxySystem = proxySystem,
                            proxyHost = proxyHost,
                            proxyPortText = proxyPortText,
                            proxyUsr = proxyUsr,
                            proxyPwd = proxyPwd,
                            solidWindow = solidWindow
                        ).onSuccess {
                            errorMessage = null
                            saving = false
                            snackbarHostState.showSnackbar("设置已保存")
                        }.onFailure {
                            errorMessage = "保存失败: ${it.message}"
                        }
                        saving = false
                    }
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
                                            solidWindow = solidWindow,
                                            onSolidWindowChange = { solidWindow = it }
                                        )
                                    }

                                    SettingCategory.Java -> {
                                        JavaSettings(
                                            totalMemoryMb = totalMemoryMb,
                                            maxMemoryText = maxMemoryText,
                                            onMaxMemoryChange = { maxMemoryText = it.trim() }
                                        )
                                    }

                                    SettingCategory.Network -> {
                                        NetworkSettings(
                                            preferModMirror = preferModMirror,
                                            onPreferModMirrorChange = { preferModMirror = it },
                                            preferMcMirror = preferMcMirror,
                                            onPreferMcMirrorChange = { preferMcMirror = it },
                                            proxyEnabled = proxyEnabled,
                                            proxySystem = proxySystem,
                                            proxyHost = proxyHost,
                                            proxyPort = proxyPortText,
                                            proxyUsr = proxyUsr,
                                            proxyPwd = proxyPwd,
                                            onProxyEnabledChange = { proxyEnabled = it },
                                            onProxySystemChange = { proxySystem = it },
                                            onProxyHostChange = { proxyHost = it },
                                            onProxyPortChange = { proxyPortText = it },
                                            onProxyUsrChange = { proxyUsr = it },
                                            onProxyPwdChange = { proxyPwd = it },
                                            switchingNode = switchingNode,
                                            onAutoSwitchFastestNode = { switchNode() },
                                            onUseGameBackupNode = { switchNode(true) },
                                            onUseMainNode = { switchNode(gameBackup = false, forceMain = true) }
                                        )
                                    }

                                    //SettingCategory.Minecraft -> LocalMinecraftSettings()

                                }
                                errorMessage?.let {
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
        preferModMirror: Boolean,
        onPreferModMirrorChange: (Boolean) -> Unit,
        preferMcMirror: Boolean,
        onPreferMcMirrorChange: (Boolean) -> Unit,
        proxyEnabled: Boolean,
        proxySystem: Boolean,
        proxyHost: String,
        proxyPort: String,
        proxyUsr: String,
        proxyPwd: String,
        onProxyEnabledChange: (Boolean) -> Unit,
        onProxySystemChange: (Boolean) -> Unit,
        onProxyHostChange: (String) -> Unit,
        onProxyPortChange: (String) -> Unit,
        onProxyUsrChange: (String) -> Unit,
        onProxyPwdChange: (String) -> Unit,
        switchingNode: Boolean,
        onAutoSwitchFastestNode: () -> Unit,
        onUseGameBackupNode: () -> Unit,
        onUseMainNode: () -> Unit,
    ) {
        val scope = rememberCoroutineScope()
        var dlQuota by remember { mutableStateOf<DownloadQuota.Vo?>(null) }
        var dlQuotaLoading by remember { mutableStateOf(false) }
        var dlQuotaError by remember { mutableStateOf<String?>(null) }

        fun loadDlQuota() {
            if (dlQuotaLoading) return
            dlQuotaLoading = true
            dlQuotaError = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { server.makeRequest<DownloadQuota.Vo>("download/quota") }
                }
                dlQuotaLoading = false
                result.onSuccess { response ->
                    if (response.ok) {
                        dlQuota = response.data
                    } else {
                        dlQuotaError = response.msg.ifBlank { "下载额度读取失败" }
                    }
                }.onFailure { error ->
                    dlQuotaError = error.message ?: "下载额度读取失败"
                }
            }
        }

        LaunchedEffect(Unit) {
            loadDlQuota()
        }

        RColumn {
            RRow {
                Text("使用BMCL-API国内镜像")
                RSwitch(checked = preferMcMirror, onCheckedChange = onPreferMcMirrorChange)
                Text("下载MC资源")
                RSwitch(checked = preferModMirror, onCheckedChange = onPreferModMirrorChange)
                Text("下载Mod")
            }
            RRow {
                Text("RDI CDN下载额度")
                val quotaText = when {
                    dlQuotaLoading && dlQuota == null -> "读取中"
                    dlQuotaError != null && dlQuota == null -> "读取失败"
                    else -> dlQuota?.let {
                        "今日剩余${it.remainingBytes.humanFileSize}/${it.limitBytes.humanFileSize}"
                    } ?: "--"
                }
                Text(
                    text = quotaText,
                    color = if (dlQuotaError != null && dlQuota == null) MaterialTheme.colorScheme.error else Color.Unspecified
                )
                CircleIconButton(
                    icon = "\uF021",
                    tooltip = if (dlQuotaLoading) "刷新中" else "刷新下载额度",
                    showText = false,
                    enabled = !dlQuotaLoading,
                    onClick = ::loadDlQuota
                )
            }
            AutoRouteStatus(
                switchingNode = switchingNode,
                onAutoSwitchFastestNode = onAutoSwitchFastestNode,
                onUseGameBackupNode = onUseGameBackupNode,
                onUseMainNode
            )
            val mode = when {
                !proxyEnabled -> 0
                proxySystem -> 1
                else -> 2
            }
            RRow {
                Text("代理")
                RadioButton(selected = mode == 0, onClick = {
                    onProxyEnabledChange(false)
                    onProxySystemChange(false)
                })
                Text("无代理")
                RadioButton(selected = mode == 1, onClick = {
                    onProxyEnabledChange(true)
                    onProxySystemChange(true)
                })
                Text("系统代理")
                RadioButton(selected = mode == 2, onClick = {
                    onProxyEnabledChange(true)
                    onProxySystemChange(false)
                })
                Text("自定义代理")
            }
            if (mode == 2) {
                RRow {

                    RTextField(
                        "主机",
                        value = proxyHost,
                        onValueChange = onProxyHostChange,
                        modifier = Modifier.width(240.dp)
                    )
                    RTextField(
                        "端口",
                        value = proxyPort,
                        onValueChange = onProxyPortChange,
                        modifier = Modifier.width(120.dp)
                    )
                    RTextField(
                        label = "用户名（可选）",
                        value = proxyUsr,
                        onValueChange = onProxyUsrChange,
                        modifier = Modifier.width(240.dp)
                    )
                    RTextField(
                        label = "密码（可选）",
                        value = proxyPwd,
                        onValueChange = onProxyPwdChange,
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

