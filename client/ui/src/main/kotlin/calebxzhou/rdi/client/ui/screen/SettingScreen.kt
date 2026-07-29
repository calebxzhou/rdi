package calebxzhou.rdi.client.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import calebxzau.rdi.client.ui.RSwitch
import calebxzau.rdi.client.ui.RTextField
import calebxzau.rdi.client.ui.Space8h
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.asIconText
import calebxzau.rdi.client.ui.baseShapeRadius
import calebxzau.rdi.client.ui.openUrl
import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.rdi.client.*
import calebxzhou.rdi.client.net.RServer
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.NodeRefreshCoordinator
// import calebxzhou.rdi.client.service.LocalMinecraftReuseService
// import calebxzhou.rdi.client.service.LocalMinecraftScanState
import calebxzhou.rdi.client.service.SettingsService
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.RPasswordField
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
    var jre25Path by remember { mutableStateOf("") }
    var jre21Path by remember { mutableStateOf("") }
    var proxyEnabled by remember { mutableStateOf(false) }
    var proxySystem by remember { mutableStateOf(false) }
    var proxyHost by remember { mutableStateOf("127.0.0.1") }
    var proxyPortText by remember { mutableStateOf("10808") }
    var proxyUsr by remember { mutableStateOf("") }
    var proxyPwd by remember { mutableStateOf("") }
    var solidWindow by remember { mutableStateOf(false) }
    var totalMemoryMb by remember { mutableStateOf(0) }
    val defaultAiProfile = remember { AiConfig().activeProfile() }
    val aiProfiles = remember { mutableStateListOf(defaultAiProfile) }
    var activeAiProfileId by remember { mutableStateOf(defaultAiProfile.id) }
    var selectedAiProfileId by remember { mutableStateOf(defaultAiProfile.id) }
    var fetchedAiModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var refreshingAiModels by remember { mutableStateOf(false) }
    var aiModelRefreshError by remember { mutableStateOf<String?>(null) }
    var aiModelRefreshMessage by remember { mutableStateOf<String?>(null) }
    var aiBalanceMessage by remember { mutableStateOf<String?>(null) }
    var showAiApiKey by remember { mutableStateOf(false) }
    // Load config on all platforms
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val config = AppConfig.load()
                preferModMirror = config.preferModMirror
                preferMcMirror = config.preferMcMirror
                maxMemoryText = if (config.maxMemory <= 0) "" else config.maxMemory.toString()
                jre25Path = config.jre25Path.orEmpty()
                jre21Path = config.jre21Path.orEmpty()
                proxyEnabled = config.proxyConfig?.enabled ?: false
                proxySystem = config.proxyConfig?.systemProxy ?: false
                proxyHost = config.proxyConfig?.host ?: "127.0.0.1"
                proxyPortText = (config.proxyConfig?.port ?: 10808).toString()
                proxyUsr = config.proxyConfig?.usr.orEmpty()
                proxyPwd = config.proxyConfig?.pwd.orEmpty()
                solidWindow = config.solidWindow
                val normalizedAiConfig = config.aiConfig.normalized()
                aiProfiles.clear()
                aiProfiles.addAll(normalizedAiConfig.profiles)
                activeAiProfileId = normalizedAiConfig.activeProfileId
                selectedAiProfileId = normalizedAiConfig.activeProfileId
                totalMemoryMb = calebxzhou.rdi.client.service.SettingsService.getTotalPhysicalMemoryMb()
            }
        }
    }

    fun selectedAiProfile(): AiProviderProfile =
        aiProfiles.firstOrNull { it.id == selectedAiProfileId }
            ?: aiProfiles.first()

    fun updateAiProfile(profileId: String, transform: (AiProviderProfile) -> AiProviderProfile) {
        val index = aiProfiles.indexOfFirst { it.id == profileId }
        if (index >= 0) {
            aiProfiles[index] = transform(aiProfiles[index])
        }
    }

    fun updateSelectedAiProfile(transform: (AiProviderProfile) -> AiProviderProfile) {
        updateAiProfile(selectedAiProfileId, transform)
    }

    fun clearAiModelFetchState(clearModels: Boolean = true) {
        if (clearModels) {
            fetchedAiModels = emptyList()
        }
        aiModelRefreshError = null
        aiModelRefreshMessage = null
        aiBalanceMessage = null
    }

    fun buildAiConfig(): AiConfig =
        AiConfig(
            activeProfileId = activeAiProfileId,
            profiles = aiProfiles.toList()
        ).normalized()


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
                        color = MaterialColor.GREEN_900.color
                    )
                    Space8w()
                }
                CircleIconButton(
                    icon = "\uF0C7",
                    tooltip = "保存",
                    bgColor = MaterialColor.GREEN_900.color,
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
                        val jre25 = jre25Path.trim().takeIf { it.isNotEmpty() }
                        val jre21 = jre21Path.trim().takeIf { it.isNotEmpty() }
                        val java25Ok = withContext(Dispatchers.IO) {
                            jre25?.let { svc.validateJavaPath(it, 25) } ?: Result.success(Unit)
                        }
                        val java21Ok = withContext(Dispatchers.IO) {
                            jre21?.let { svc.validateJavaPath(it, 21) } ?: Result.success(Unit)
                        }
                        java25Ok.exceptionOrNull()?.let {
                            errorMessage = it.message ?: "Java25路径无效"
                            saving = false
                            return@launch
                        }
                        java21Ok.exceptionOrNull()?.let {
                            errorMessage = it.message ?: "Java21路径无效"
                            saving = false
                            return@launch
                        }

                        val aiConfig = buildAiConfig()
                        // val requireActiveAiProfile = category == SettingCategory.AI

                        // Save settings
                        /*val aiValidation = svc.validateAiConfig(aiConfig, requireActiveAiProfile)
                        if (!aiValidation.success) {
                            errorMessage = aiValidation.errorMessage
                            saving = false
                            return@launch
                        }*/
                        svc.saveSettings(
                            preferModMirror = preferModMirror,
                            preferMcMirror = preferMcMirror,
                            maxMemoryText = maxMemoryText,
                            jre25Path = jre25Path,
                            jre21Path = jre21Path,
                            proxyEnabled = proxyEnabled,
                            proxySystem = proxySystem,
                            proxyHost = proxyHost,
                            proxyPortText = proxyPortText,
                            proxyUsr = proxyUsr,
                            proxyPwd = proxyPwd,
                            solidWindow = solidWindow,
                            aiConfig = aiConfig,
                            requireActiveAiProfile = false//requireActiveAiProfile
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
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
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

                                    /*SettingCategory.AI -> {
                                        val currentAiProfile = selectedAiProfile()
                                        AiSettings(
                                            profiles = aiProfiles.toList(),
                                            selectedProfileId = selectedAiProfileId,
                                            activeProfileId = activeAiProfileId,
                                            profile = currentAiProfile,
                                            fetchedModels = fetchedAiModels,
                                            refreshingModels = refreshingAiModels,
                                            modelRefreshError = aiModelRefreshError,
                                            modelRefreshMessage = aiModelRefreshMessage,
                                            balanceMessage = aiBalanceMessage,
                                            onSelectProfile = {
                                                selectedAiProfileId = it
                                                clearAiModelFetchState()
                                            },
                                            onAddProfile = {
                                                val newProfile = AiProviderProfile(
                                                    id = "ai-${System.currentTimeMillis()}",
                                                    name = "AI配置${aiProfiles.size + 1}",
                                                    provider = AiProvider.OPENAI,
                                                    baseUrl = AiProvider.OPENAI.defaultBaseUrl
                                                )
                                                aiProfiles.add(newProfile)
                                                selectedAiProfileId = newProfile.id
                                                if (aiProfiles.size == 1) {
                                                    activeAiProfileId = newProfile.id
                                                }
                                                clearAiModelFetchState()
                                            },
                                            onDeleteProfile = {
                                                if (aiProfiles.size <= 1) {
                                                    errorMessage = "至少保留1个AI配置"
                                                } else {
                                                    val deleteIndex =
                                                        aiProfiles.indexOfFirst { it.id == selectedAiProfileId }
                                                    if (deleteIndex >= 0) {
                                                        val deletedProfile = aiProfiles.removeAt(deleteIndex)
                                                        val nextProfile =
                                                            aiProfiles.getOrNull(deleteIndex.coerceAtMost(aiProfiles.lastIndex))
                                                                ?: aiProfiles.first()
                                                        selectedAiProfileId = nextProfile.id
                                                        if (activeAiProfileId == deletedProfile.id) {
                                                            activeAiProfileId = nextProfile.id
                                                        }
                                                        clearAiModelFetchState()
                                                    }
                                                }
                                            },
                                            onSetActiveProfile = {
                                                activeAiProfileId = selectedAiProfileId
                                            },
                                            onProfileNameChange = { name ->
                                                updateSelectedAiProfile { it.copy(name = name) }
                                            },
                                            onProviderChange = { provider ->
                                                val keepCustomBaseUrl = currentAiProfile.baseUrl.isNotBlank() &&
                                                        currentAiProfile.baseUrl != currentAiProfile.provider.defaultBaseUrl
                                                updateSelectedAiProfile {
                                                    it.copy(
                                                        provider = provider,
                                                        baseUrl = when {
                                                            provider == AiProvider.DEEPSEEK -> provider.defaultBaseUrl
                                                            keepCustomBaseUrl -> it.baseUrl
                                                            else -> provider.defaultBaseUrl
                                                        },
                                                        model = "",
                                                        reasoningEffort = it.reasoningEffort.normalizedFor(provider)
                                                    )
                                                }
                                                clearAiModelFetchState()
                                            },
                                            onBaseUrlChange = {
                                                updateSelectedAiProfile { profile -> profile.copy(baseUrl = it) }
                                                clearAiModelFetchState()
                                            },
                                            onApiKeyChange = {
                                                updateSelectedAiProfile { profile -> profile.copy(apiKey = it) }
                                                clearAiModelFetchState()
                                            },
                                            onModelChange = { model ->
                                                updateSelectedAiProfile { profile -> profile.copy(model = model) }
                                            },
                                            onReasoningEffortChange = { effort ->
                                                updateSelectedAiProfile { profile -> profile.copy(reasoningEffort = effort) }
                                            },
                                            onContextLimitChange = {
                                                it.filter(Char::isDigit).take(7).toIntOrNull()?.let { limit ->
                                                    updateSelectedAiProfile { profile -> profile.copy(contextLimitTokens = limit) }
                                                }
                                            },
                                            onPriceCurrencyChange = { currency ->
                                                updateSelectedAiProfile { profile ->
                                                    profile.copy(tokenPrice = profile.tokenPrice.copy(currency = currency))
                                                }
                                            },
                                            onInputCacheMiss1MPriceChange = { text ->
                                                parseAiPriceInput(text)?.let { price ->
                                                    updateSelectedAiProfile { profile ->
                                                        profile.copy(
                                                            tokenPrice = profile.tokenPrice.copy(
                                                                inputCacheMiss1M = price
                                                            )
                                                        )
                                                    }
                                                }
                                            },
                                            onInputCacheHit1MPriceChange = { text ->
                                                parseAiPriceInput(text)?.let { price ->
                                                    updateSelectedAiProfile { profile ->
                                                        profile.copy(
                                                            tokenPrice = profile.tokenPrice.copy(
                                                                inputCacheHit1M = price
                                                            )
                                                        )
                                                    }
                                                }
                                            },
                                            onOutput1MPriceChange = { text ->
                                                parseAiPriceInput(text)?.let { price ->
                                                    updateSelectedAiProfile { profile ->
                                                        profile.copy(tokenPrice = profile.tokenPrice.copy(output1M = price))
                                                    }
                                                }
                                            },
                                            onRefreshModels = {
                                                if (!refreshingAiModels) {
                                                    val refreshProfile = selectedAiProfile()
                                                    refreshingAiModels = true
                                                    aiModelRefreshError = null
                                                    aiModelRefreshMessage = null
                                                    aiBalanceMessage = null
                                                    scope.launch {
                                                        SettingsService.fetchAiModels(
                                                            refreshProfile.provider,
                                                            refreshProfile.baseUrl,
                                                            refreshProfile.apiKey
                                                        )
                                                            .onSuccess { ids ->
                                                                val modelIds = ids.map(String::trim)
                                                                    .filter(String::isNotBlank)
                                                                    .distinct()
                                                                if (selectedAiProfileId == refreshProfile.id) {
                                                                    fetchedAiModels = modelIds
                                                                    aiModelRefreshMessage =
                                                                        "[${getDateTimeNow("HH:mm:ss")}]已获取${modelIds.size}个模型"
                                                                }
                                                                val selectedModel = refreshProfile.model.trim()
                                                                if (selectedModel.isBlank()) {
                                                                    modelIds.firstOrNull()?.let { model ->
                                                                        updateAiProfile(refreshProfile.id) {
                                                                            it.copy(
                                                                                model = model
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                                if (refreshProfile.provider == AiProvider.DEEPSEEK) {
                                                                    SettingsService.fetchDeepSeekBalance(refreshProfile.apiKey)
                                                                        .onSuccess { balance ->
                                                                            if (selectedAiProfileId == refreshProfile.id) {
                                                                                aiBalanceMessage = "余额$balance"
                                                                            }
                                                                        }
                                                                        .onFailure {
                                                                            if (selectedAiProfileId == refreshProfile.id) {
                                                                                aiModelRefreshError =
                                                                                    it.message ?: "刷新余额失败"
                                                                            }
                                                                        }
                                                                }
                                                            }
                                                            .onFailure {
                                                                if (selectedAiProfileId == refreshProfile.id) {
                                                                    fetchedAiModels = emptyList()
                                                                    aiBalanceMessage = null
                                                                    aiModelRefreshError = it.message ?: "刷新模型失败"
                                                                }
                                                            }
                                                        refreshingAiModels = false
                                                    }
                                                }
                                            }
                                        )
                                    }*/
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
        Network("\uEF09", "网络"),
       // Minecraft("\uE7C4", "Minecraft"),
        //AI("\uDB84\uDECA", "AI");
;
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
    private fun AiSettings(
        profiles: List<AiProviderProfile>,
        selectedProfileId: String,
        activeProfileId: String,
        profile: AiProviderProfile,
        fetchedModels: List<String>,
        refreshingModels: Boolean,
        modelRefreshError: String?,
        modelRefreshMessage: String?,
        balanceMessage: String?,
        onSelectProfile: (String) -> Unit,
        onAddProfile: () -> Unit,
        onDeleteProfile: () -> Unit,
        onSetActiveProfile: () -> Unit,
        onProfileNameChange: (String) -> Unit,
        onProviderChange: (AiProvider) -> Unit,
        onBaseUrlChange: (String) -> Unit,
        onApiKeyChange: (String) -> Unit,
        onModelChange: (String) -> Unit,
        onReasoningEffortChange: (AiReasoningEffort) -> Unit,
        onContextLimitChange: (String) -> Unit,
        onPriceCurrencyChange: (AiPriceCurrency) -> Unit,
        onInputCacheMiss1MPriceChange: (String) -> Unit,
        onInputCacheHit1MPriceChange: (String) -> Unit,
        onOutput1MPriceChange: (String) -> Unit,
        onRefreshModels: () -> Unit
    ) {
        var modelMenuExpanded by remember { mutableStateOf(false) }
        var reasoningMenuExpanded by remember { mutableStateOf(false) }
        val dropdownModels = remember(fetchedModels) {
            fetchedModels
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
        }
        RColumn {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AI供应商", style = MaterialTheme.typography.titleLarge)
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        profiles.forEach { item ->
                            val selected = item.id == selectedProfileId
                            val active = item.id == activeProfileId
                            Text(
                                text = "${if (active) "\uF00C 使用中·".asIconText else ""}${item.name.ifBlank { item.provider.displayName }}",
                                color = if (selected) Color.White else MaterialColor.GRAY_900.color,
                                modifier = Modifier
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialColor.GRAY_200.color,
                                        RoundedCornerShape(baseShapeRadius.dp)
                                    )
                                    .clickable { onSelectProfile(item.id) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        CircleIconButton(
                            icon = "\uF067",
                            tooltip = "新增",
                            bgColor = MaterialTheme.colorScheme.primary,
                            onClick = onAddProfile
                        )
                        CircleIconButton(
                            icon = "\uF1F8",
                            tooltip = "删除",
                            bgColor = MaterialColor.RED_700.color,
                            enabled = profiles.size > 1,
                            onClick = onDeleteProfile
                        )
                        CircleIconButton(
                            icon = "\uF00C",
                            tooltip = "使用",
                            bgColor = MaterialColor.GREEN_900.color,
                            enabled = profile.id != activeProfileId,
                            onClick = onSetActiveProfile
                        )
                    }
                }
            }

            RRow {
                Text("API类别")
                AiProvider.entries.forEach { provider ->
                    RadioButton(
                        selected = profile.provider == provider,
                        onClick = { onProviderChange(provider) }
                    )
                    Text(provider.displayName)
                }

            }
            RRow {
                RTextField(
                    label = "配置名称",
                    value = profile.name,
                    onValueChange = onProfileNameChange,
                    modifier = Modifier.width(120.dp)
                )
                RTextField(
                    label = "API Base URL",
                    value = profile.baseUrl,
                    onValueChange = onBaseUrlChange,
                    enabled = profile.provider != AiProvider.DEEPSEEK,
                    modifier = Modifier.width(360.dp)
                )
                RPasswordField(
                    value = profile.apiKey,
                    onValueChange = onApiKeyChange,
                    label = "API Key",
                    modifier = Modifier.width(360.dp)
                )
                RTextField(
                    label = "上下文长度",
                    value = profile.contextLimitTokens.toString(),
                    onValueChange = onContextLimitChange,
                    modifier = Modifier.width(120.dp)
                )
            }
            RRow {
                Text("价格/1M tokens")
                AiPriceCurrency.entries.forEach { currency ->
                    RadioButton(
                        selected = profile.tokenPrice.currency == currency,
                        onClick = { onPriceCurrencyChange(currency) }
                    )
                    Text(currency.mark)
                }
                AiPriceField(
                    label = "${profile.tokenPrice.currency.mark}输入未缓存",
                    value = profile.tokenPrice.inputCacheMiss1M,
                    onValueChange = onInputCacheMiss1MPriceChange
                )
                AiPriceField(
                    label = "${profile.tokenPrice.currency.mark}输入缓存",
                    value = profile.tokenPrice.inputCacheHit1M,
                    onValueChange = onInputCacheHit1MPriceChange
                )
                AiPriceField(
                    label = "${profile.tokenPrice.currency.mark}输出",
                    value = profile.tokenPrice.output1M,
                    onValueChange = onOutput1MPriceChange
                )
            }
            RRow {
                RTextField(
                    label = "模型",
                    value = profile.model,
                    onValueChange = onModelChange,
                    singleLine = true,
                    modifier = Modifier.width(240.dp),
                    trailingIcon = {
                        Text(
                            text = "\uEB6E".asIconText,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickable(enabled = dropdownModels.isNotEmpty()) { modelMenuExpanded = true }
                        )
                    }
                )
                DropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false }
                ) {
                    dropdownModels.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate) },
                            onClick = {
                                onModelChange(candidate)
                                modelMenuExpanded = false
                            }
                        )
                    }
                }
                CircleIconButton(
                    icon = "\uDB85\uDEC4",
                    tooltip = if (refreshingModels) "刷新中" else "获取模型列表",
                    bgColor = MaterialTheme.colorScheme.primary,
                    enabled = !refreshingModels,
                    onClick = onRefreshModels
                )
                if (profile.provider == AiProvider.DEEPSEEK) {
                    CircleIconButton(
                        icon = "\uF157",
                        tooltip = "DeepSeek充值",
                        bgColor = MaterialColor.TEAL_900.color,
                        onClick = { openUrl("https://platform.deepseek.com/top_up") }
                    )
                }
            }
            /* Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Box {
                Text(
                    text = "${profile.reasoningEffort.displayName} \uE70D".asIconText,
                    color = MaterialColor.GRAY_900.color,
                    modifier = Modifier
                        .background(MaterialColor.GRAY_200.color, RoundedCornerShape(8.dp))
                        .clickable { reasoningMenuExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )

            }
        }*/
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {


            }
            modelRefreshError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            modelRefreshMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialColor.GRAY_800.color,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            balanceMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialColor.GRAY_800.color,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    @Composable
    private fun AiPriceField(
        label: String,
        value: Double,
        onValueChange: (String) -> Unit
    ) {
        var text by remember { mutableStateOf(formatAiPrice(value)) }
        LaunchedEffect(value) {
            val parsedText = parseAiPriceInput(text)
            if (parsedText == null || parsedText != value) {
                text = formatAiPrice(value)
            }
        }
        RTextField(
            label = "$label/1M",
            value = text,
            onValueChange = { input ->
                val nextText = input.trim()
                if (!isAiPriceInputCandidate(nextText)) return@RTextField
                text = nextText
                parseAiPriceInput(nextText)?.let { onValueChange(nextText) }
            },
            modifier = Modifier.width(140.dp)
        )
    }

    private fun parseAiPriceInput(text: String): Double? {
        val input = text.trim()
        if (input.isEmpty()) return 0.0
        if (!isAiPriceInputCandidate(input) || input == ".") return null
        return input.toDoubleOrNull()
            ?.takeIf { !it.isNaN() && !it.isInfinite() && it >= 0.0 }
    }

    private fun isAiPriceInputCandidate(text: String): Boolean =
        text.matches(Regex("""\d*(\.\d{0,8})?"""))

    private fun formatAiPrice(value: Double): String =
        if (value.isNaN() || value.isInfinite()) {
            "0"
        } else if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            value.toString()
        }


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
                bgColor = MaterialColor.TEAL_900.color,
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
                bgColor = MaterialColor.TEAL_900.color,
                enabled = !switchingNode,
                onClick = onUseMainNode
            )
        }
    }

