package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzhou.mykotutils.std.javaExePath
import calebxzhou.rdi.client.AiConfig
import calebxzhou.rdi.client.AiPriceCurrency
import calebxzhou.rdi.client.AiProvider
import calebxzhou.rdi.client.AiProviderProfile
import calebxzhou.rdi.client.AiReasoningEffort
import calebxzhou.rdi.client.AppConfig
import calebxzhou.rdi.client.net.RServer
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.NodeRefreshCoordinator
import calebxzhou.rdi.client.service.PlayerService
import calebxzhou.rdi.client.service.SettingsService
import calebxzhou.rdi.client.service.playerInfoCache
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.RPasswordField
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.MsaAccountInfo
import calebxzhou.rdi.common.util.getDateTimeNow
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode

/**
 * calebxzhou @ 2026-01-24 18:36
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var category by remember { mutableStateOf(SettingCategory.Account) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showChangeProfile by remember { mutableStateOf(false) }
    var switchingNode by remember { mutableStateOf(false) }

    // Desktop-only settings state
    var preferModMirror by remember { mutableStateOf(false) }
    var preferMcMirror by remember { mutableStateOf(false) }
    var maxMemoryText by remember { mutableStateOf("") }
    var jre25Path by remember { mutableStateOf("") }
    var jre21Path by remember { mutableStateOf("") }
    var jre8Path by remember { mutableStateOf("") }
    var proxyEnabled by remember { mutableStateOf(false) }
    var proxySystem by remember { mutableStateOf(false) }
    var proxyHost by remember { mutableStateOf("127.0.0.1") }
    var proxyPortText by remember { mutableStateOf("10808") }
    var proxyUsr by remember { mutableStateOf("") }
    var proxyPwd by remember { mutableStateOf("") }
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
                jre8Path = config.jre8Path.orEmpty()
                proxyEnabled = config.proxyConfig?.enabled ?: false
                proxySystem = config.proxyConfig?.systemProxy ?: false
                proxyHost = config.proxyConfig?.host ?: "127.0.0.1"
                proxyPortText = (config.proxyConfig?.port ?: 10808).toString()
                proxyUsr = config.proxyConfig?.usr.orEmpty()
                proxyPwd = config.proxyConfig?.pwd.orEmpty()
                val normalizedAiConfig = config.aiConfig.normalized()
                aiProfiles.clear()
                aiProfiles.addAll(normalizedAiConfig.profiles)
                activeAiProfileId = normalizedAiConfig.activeProfileId
                selectedAiProfileId = normalizedAiConfig.activeProfileId
                if (isDesktop) {
                    totalMemoryMb = calebxzhou.rdi.client.service.SettingsService.getTotalPhysicalMemoryMb()
                }
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


    MainBox {
        MainColumn {
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
                        val svc = calebxzhou.rdi.client.service.SettingsService

                        if (isDesktop) {
                            // Validate memory
                            val memoryValidation = svc.validateMemory(maxMemoryText, totalMemoryMb)
                            if (!memoryValidation.success) {
                                errorMessage = memoryValidation.errorMessage
                                saving = false
                                return@launch
                            }
                            // Validate proxy port
                            val proxyValidation = svc.validateProxyPort(proxyPortText)
                            if (!proxyValidation.success) {
                                errorMessage = proxyValidation.errorMessage
                                saving = false
                                return@launch
                            }
                            // Validate Java paths
                            val jre25 = jre25Path.trim().takeIf { it.isNotEmpty() }
                            val jre21 = jre21Path.trim().takeIf { it.isNotEmpty() }
                            val jre8 = jre8Path.trim().takeIf { it.isNotEmpty() }
                            val java25Ok = withContext(Dispatchers.IO) {
                                jre25?.let { svc.validateJavaPath(it, 25) } ?: Result.success(Unit)
                            }
                            val java21Ok = withContext(Dispatchers.IO) {
                                jre21?.let { svc.validateJavaPath(it, 21) } ?: Result.success(Unit)
                            }
                            val java8Ok = withContext(Dispatchers.IO) {
                                jre8?.let { svc.validateJavaPath(it, 8) } ?: Result.success(Unit)
                            }
                            java25Ok.exceptionOrNull()?.let {
                                errorMessage = it.message ?: "Java 25 路径无效"
                                saving = false
                                return@launch
                            }
                            java21Ok.exceptionOrNull()?.let {
                                errorMessage = it.message ?: "Java 21 路径无效"
                                saving = false
                                return@launch
                            }
                            java8Ok.exceptionOrNull()?.let {
                                errorMessage = it.message ?: "Java 8 路径无效"
                                saving = false
                                return@launch
                            }
                        }

                        val aiConfig = buildAiConfig()
                        val requireActiveAiProfile = category == SettingCategory.AI

                        // Save settings
                        val aiValidation = svc.validateAiConfig(aiConfig, requireActiveAiProfile)
                        if (!aiValidation.success) {
                            errorMessage = aiValidation.errorMessage
                            saving = false
                            return@launch
                        }
                        svc.saveSettings(
                            preferModMirror = preferModMirror,
                            preferMcMirror = preferMcMirror,
                            maxMemoryText = maxMemoryText,
                            jre25Path = jre25Path,
                            jre21Path = jre21Path,
                            jre8Path = jre8Path,
                            proxyEnabled = proxyEnabled,
                            proxySystem = proxySystem,
                            proxyHost = proxyHost,
                            proxyPortText = proxyPortText,
                            proxyUsr = proxyUsr,
                            proxyPwd = proxyPwd,
                            aiConfig = aiConfig,
                            requireActiveAiProfile = requireActiveAiProfile
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
            Space8h()
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
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            when (category) {
                                SettingCategory.Account -> {
                                    AccountSettings(
                                        onChangeProfile = { showChangeProfile = true }
                                    )
                                }

                                SettingCategory.Java -> {
                                    if (isDesktop) {
                                        JavaSettings(
                                            totalMemoryMb = totalMemoryMb,
                                            maxMemoryText = maxMemoryText,
                                            onMaxMemoryChange = { maxMemoryText = it.trim() },
                                            jre25Path = jre25Path,
                                            onJre25Change = { jre25Path = it },
                                            onPickJre25 = {
                                                scope.launch {
                                                    pickJavaExecutable("选择Java25可执行文件")?.let { jre25Path = it }
                                                }
                                            },
                                            jre21Path = jre21Path,
                                            onJre21Change = { jre21Path = it },
                                            onPickJre21 = {
                                                scope.launch {
                                                    pickJavaExecutable("选择Java21可执行文件")?.let { jre21Path = it }
                                                }
                                            },
                                            jre8Path = jre8Path,
                                            onJre8Change = { jre8Path = it },
                                            onPickJre8 = {
                                                scope.launch {
                                                    pickJavaExecutable("选择Java8可执行文件")?.let { jre8Path = it }
                                                }
                                            }
                                        )
                                    }
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
                                        onAutoSwitchFastestNode = {
                                            if (!switchingNode) {
                                                switchingNode = true
                                                scope.launch {
                                                    NodeRefreshCoordinator.refreshFromPrimary("manual-setting")
                                                        .onSuccess {
                                                            snackbarHostState.showSnackbar("已切换到${it.nodeName}")
                                                        }
                                                        .onFailure {
                                                            snackbarHostState.showSnackbar(
                                                                it.message ?: "节点刷新失败"
                                                            )
                                                        }
                                                    switchingNode = false
                                                }
                                            }
                                        },
                                        onUseGameBackupNode = {
                                            if (!switchingNode) {
                                                switchingNode = true
                                                scope.launch {
                                                    NodeRefreshCoordinator.refreshGameBackup("manual-setting")
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
                                    )
                                }

                                SettingCategory.AI -> {
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
                                                val deleteIndex = aiProfiles.indexOfFirst { it.id == selectedAiProfileId }
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
                                                    profile.copy(tokenPrice = profile.tokenPrice.copy(inputCacheMiss1M = price))
                                                }
                                            }
                                        },
                                        onInputCacheHit1MPriceChange = { text ->
                                            parseAiPriceInput(text)?.let { price ->
                                                updateSelectedAiProfile { profile ->
                                                    profile.copy(tokenPrice = profile.tokenPrice.copy(inputCacheHit1M = price))
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
                                                                    updateAiProfile(refreshProfile.id) { it.copy(model = model) }
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
                                                                            aiModelRefreshError = it.message ?: "刷新余额失败"
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
                                }
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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
        if (showChangeProfile) {
            ChangeProfileDialog(
                onDismiss = { showChangeProfile = false },
                onSuccess = {
                    showChangeProfile = false
                    scope.launch {
                        snackbarHostState.showSnackbar("修改成功")
                    }
                }
            )
        }
    }
}


private enum class SettingCategory(val icon: String, val label: String) {
    Account("\uEB99", "账号"),
    Java("\uEDAF", "Java"),
    Network("\uEF09", "网络"),
    AI("\uDB84\uDECA", "AI");

    /** Whether this category is visible on the current platform */
    val visible: Boolean
        get() = when (this) {
            Java -> isDesktop
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSettings(
    onChangeProfile: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var startMsBind by remember { mutableStateOf(false) }
    var pendingBind by remember { mutableStateOf(false) }
    var msaInfo by remember { mutableStateOf<MsaAccountInfo?>(null) }
    var msaDeviceCode by remember { mutableStateOf<MsaDeviceCode?>(null) }
    var errMsg by remember { mutableStateOf<String?>(null) }
    var msAccountBound by remember { mutableStateOf(loggedAccount.hasMsid) }

    fun clearMsaState() {
        startMsBind = false
        msaInfo = null
        msaDeviceCode = null
    }
    RColumn {
        RRow {
            Text("QQ：${loggedAccount.qq}")
            Text("昵称：${loggedAccount.name}")
            CircleIconButton("\uE690", "修改个人信息") {
                onChangeProfile()
            }
            errMsg?.let { ErrorText(it) }
        }

        if (!msAccountBound && startMsBind) {
            Text("即将登录微软账号，点击复制浏览器中打开链接，请在5分钟内登录")
            Text("不要切换到其他页面！", fontWeight = FontWeight.Bold)
            Text("登录完成后稍等10秒，会自动读取账号信息以进行下一步")
        }
        msaDeviceCode?.let { msaDeviceCode ->
            Text(
                text = msaDeviceCode.directVerificationUri,
                color = MaterialTheme.colorScheme.primary,
                style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                modifier = Modifier
                    .clickable {
                        copyToClipboard(msaDeviceCode.directVerificationUri)
                    }
            )
        }

        Space8h()
        if (msAccountBound) {
            Text("已绑定微软MC正版号")
        } else if (!startMsBind) {
            RowV {
                Text("绑定微软MC正版号，可获得更丰富的RDI体验 👉")
                Space8w()
                CircleIconButton("\uE70F", "绑定微软MC账号") {
                    startMsBind = true
                    scope.launch(Dispatchers.IO) {
                        val manager = PlayerService.microsoftLogin { code ->
                            msaDeviceCode = code
                            openMsaVerificationUrl(code.directVerificationUri)
                        }.getOrElse {
                            it.printStackTrace()
                            errMsg = "登录微软MC失败：${it.message}"
                            return@launch
                        }
                        msaInfo = MsaAccountInfo(
                            manager.minecraftProfile.upToDate.id,
                            manager.minecraftProfile.upToDate.name,
                            manager.minecraftToken.upToDate.token,
                        )

                    }
                }
            }
        }

        msaInfo?.let { info ->
            Text("读取信息成功！昵称：${info.name} MSID: ${info.uuid}")
            RowV {
                Text("绑定后将不能修改，如果确定账号信息正确，点击OK按钮。")
                Space8w()
                CircleIconButton(
                    "\uDB82\uDE50",
                    "ok",
                    bgColor = MaterialColor.GREEN_900.color,
                    enabled = !pendingBind
                ) {
                    pendingBind = true
                    scope.rdiRequestU("player/bind-ms", body = info.json, onDone = {
                        clearMsaState()
                        pendingBind = false
                    }, onErr = {
                        errMsg = "绑定失败：${it.message}，请重试"
                    }, onOk = {
                        msAccountBound = true
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                loggedAccount = PlayerService.login(loggedAccount._id.toHexString(), loggedAccount.pwd).getOrThrow()
                            }.getOrElse {
                                errMsg = "重新登录失败：${it.message}"
                            }
                        }
                    })
                }
            }
        }

    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JavaSettings(
    totalMemoryMb: Int,
    maxMemoryText: String,
    onMaxMemoryChange: (String) -> Unit,
    jre25Path: String,
    onJre25Change: (String) -> Unit,
    onPickJre25: () -> Unit,
    jre21Path: String,
    onJre21Change: (String) -> Unit,
    onPickJre21: () -> Unit,
    jre8Path: String,
    onJre8Change: (String) -> Unit,
    onPickJre8: () -> Unit
) {
    RColumn {
        RRow {
            Text("总内存 ${totalMemoryMb}MB")
            RTextField("限制MC内存",maxMemoryText, modifier = Modifier.width(140.dp)){onMaxMemoryChange(it)}
            Text("MB")
        }
        Text("当前Java：${javaExePath}")
        RRow{
            RTextField(
                label = "Java25主程序路径",
                value = jre25Path,
                onValueChange = onJre25Change,
                modifier = Modifier.weight(1f)
            )
            CircleIconButton(
                icon = "\uE8B6",
                tooltip = "选择Java25",
            ) {
                onPickJre25()
            }
        }
        RRow {
            RTextField(
                label = "Java21主程序路径（可选）",
                value = jre21Path,
                onValueChange = onJre21Change,
                modifier = Modifier.weight(1f)
            )
            CircleIconButton(
                icon = "\uE8B6",
                tooltip = "选择Java21"
            ) {
                onPickJre21()
            }
        }
        RRow{
            RTextField(
                label = "Java8主程序路径（可选）",
                value = jre8Path,
                onValueChange = onJre8Change,
                modifier = Modifier.weight(1f)
            )
            CircleIconButton(
                icon = "\uE8B6",
                tooltip = "选择Java8"
            ) {
                onPickJre8()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    RColumn{
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
                        bgColor = MaterialColor.BLUE_800.color,
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
                bgColor = MaterialColor.BLUE_900.color,
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
    RTextField(
        label = "$label/1M",
        value = formatAiPrice(value),
        onValueChange = onValueChange,
        modifier = Modifier.width(140.dp)
    )
}

private fun parseAiPriceInput(text: String): Double? {
    val input = text.trim()
    if (input.isEmpty()) return 0.0
    return input.toDoubleOrNull()
        ?.takeIf { !it.isNaN() && !it.isInfinite() && it >= 0.0 }
}

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
) {
    RColumn {
        RRow {
            Text("使用BMCL-API国内镜像")
            RSwitch(checked = preferMcMirror, onCheckedChange = onPreferMcMirrorChange)
            Text("下载MC资源")
            RSwitch(checked = preferModMirror, onCheckedChange = onPreferModMirrorChange)
            Text("下载Mod")
        }
        AutoRouteStatus(
            switchingNode = switchingNode,
            onAutoSwitchFastestNode = onAutoSwitchFastestNode,
            onUseGameBackupNode = onUseGameBackupNode
        )
        // Proxy settings — desktop only
        if (isDesktop) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoRouteStatus(
    switchingNode: Boolean,
    onAutoSwitchFastestNode: () -> Unit,
    onUseGameBackupNode: () -> Unit
) {
    val routeState by RServer.routeState.collectAsState()
    RRow {
        routeState.nodeName?.let {
            Text("当前节点 $it")
        }
        Text(if (routeState.useBackupNode) "加速入口" else "主入口")
        CircleIconButton(
            "\uDB80\uDC02",
            if (switchingNode) "已切换节点" else "切换最快节点",
            bgColor = MaterialColor.TEAL_900.color,
            enabled = !switchingNode,
            onClick = onAutoSwitchFastestNode
        )
        CircleIconButton(
            "\uDB80\uDC02",
            "临时备用节点",
            bgColor = MaterialColor.BLUE_900.color,
            enabled = !switchingNode,
            onClick = onUseGameBackupNode
        )
    }
}

@Composable
private fun ChangeProfileDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val account = loggedAccount
    var name by remember { mutableStateOf(account.name) }
    var pwd by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("修改信息") },
        text = {
            Column {
                RTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "昵称",
                    enabled = !submitting,
                )
                RPasswordField(
                    value = pwd,
                    onValueChange = { pwd = it },
                    label = "新密码 留空则不修改",
                    enabled = !submitting,
                )
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    val validation = SettingsService.validateProfileChange(name, pwd, account.name)
                    if (!validation.success) {
                        errorMessage = validation.errorMessage
                        return@TextButton
                    }

                    val params = mutableMapOf<String, Any>()
                    if (name != account.name) params["name"] = name
                    if (pwd.isNotEmpty() && pwd != account.pwd) params["pwd"] = pwd

                    submitting = true
                    errorMessage = null
                    scope.launch {
                        runCatching {
                            server.makeRequest<Unit>("player/profile", HttpMethod.Put, params)
                            val loginPwd = pwd.takeIf(String::isNotEmpty) ?: account.pwd
                            loggedAccount = PlayerService.login(account._id.toHexString(), loginPwd).getOrThrow()
                            playerInfoCache -= loggedAccount._id.toHexString()
                        }.getOrElse {
                            errorMessage = "修改失败: ${it.message}"
                            submitting = false
                            return@launch
                        }
                        submitting = false
                        onSuccess()
                    }
                }
            ) {
                if (submitting) {
                    CircularProgressIndicator(modifier = Modifier.width(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("修改")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!submitting) onDismiss() }) {
                Text("取消")
            }
        }
    )
}

