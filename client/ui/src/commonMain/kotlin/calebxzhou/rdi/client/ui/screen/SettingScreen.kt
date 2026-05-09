package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import calebxzhou.mykotutils.std.javaExePath
import calebxzhou.rdi.client.AiConfig
import calebxzhou.rdi.client.AiProvider
import calebxzhou.rdi.client.AiProviderProfile
import calebxzhou.rdi.client.AiReasoningEffort
import calebxzhou.rdi.client.AppConfig
import calebxzhou.rdi.client.net.RServer
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.NodeRefreshCoordinator
import calebxzhou.rdi.client.service.PlayerService
import calebxzhou.rdi.client.service.SettingsService
import calebxzhou.rdi.client.service.playerInfoCache
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.PasswordField
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.MsaAccountInfo
import calebxzhou.rdi.common.model.RAccount
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
    val scaffoldState = rememberScaffoldState()
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
                            scaffoldState.snackbarHostState.showSnackbar("设置已保存")
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
                                                            scaffoldState.snackbarHostState.showSnackbar("已切换到${it.nodeName}")
                                                        }
                                                        .onFailure {
                                                            scaffoldState.snackbarHostState.showSnackbar(
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
                                                            scaffoldState.snackbarHostState.showSnackbar("已临时切换到${it.nodeName}")
                                                        }
                                                        .onFailure {
                                                            scaffoldState.snackbarHostState.showSnackbar(
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
                                        showApiKey = showAiApiKey,
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
                                        onToggleApiKey = { showAiApiKey = !showAiApiKey },
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
                                    color = MaterialTheme.colors.error,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
        BottomSnakebar(scaffoldState.snackbarHostState)
        if (showChangeProfile) {
            ChangeProfileDialog(
                onDismiss = { showChangeProfile = false },
                onSuccess = {
                    showChangeProfile = false
                    scope.launch {
                        scaffoldState.snackbarHostState.showSnackbar("修改成功")
                    }
                }
            )
        }
    }
}


private enum class SettingCategory(val icon: String, val label: String) {
    Account("\uEB99", "账号"),
    Java("\uE738", "Java"),
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
    Column(
        modifier = Modifier
            .width(if (compact) 64.dp else 160.dp)
            .fillMaxHeight()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SettingCategory.entries.filter { it.visible }.forEach { category ->
            val isSelected = category == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(category) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(20.dp)
                        .background(if (isSelected) MaterialTheme.colors.primary else Color.Transparent)
                )
                Space8w()
                Text(
                    text = category.icon.asIconText,
                    color = if (isSelected) MaterialTheme.colors.primary else Color.Unspecified,
                    style = when (category) {
                        SettingCategory.Java -> MaterialTheme.typography.h5
                        else -> MaterialTheme.typography.subtitle1
                    }
                )
                if (!compact) {
                    Space8w()
                    Text(
                        text = category.label,
                        color = if (isSelected) MaterialTheme.colors.primary else Color.Unspecified,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSettings(
    onChangeProfile: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var invitedPlayers by remember { mutableStateOf<List<RAccount.Dto>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var startMsBind by remember { mutableStateOf(false) }
    var pendingBind by remember { mutableStateOf(false) }
    var msaInfo by remember { mutableStateOf<MsaAccountInfo?>(null) }
    var msaDeviceCode by remember { mutableStateOf<MsaDeviceCode?>(null) }
    var errMsg by remember { mutableStateOf<String?>(null) }
    // Load invited players on first composition
    LaunchedEffect(Unit) {
        loading = true
        scope.rdiRequest<List<RAccount.Dto>>(
            "player/invite",
            onDone = { loading = false },
            onErr = {
                // Silently fail, just show empty list
            }
        ) {
            it.data?.let { invitedPlayers = it }
        }
    }
    fun clearMsaState() {
        startMsBind = false
        msaInfo = null
        msaDeviceCode = null
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        RowV {
            Text("账号信息", style = MaterialTheme.typography.h6)
            Space8w()
            CircleIconButton("\uE690", "修改个人信息") {
                onChangeProfile()
            }
            Space8w()
            errMsg?.let { ErrorText(it) }
        }
        Space8h()
        Text("QQ：${loggedAccount.qq}")
        Text("昵称：${loggedAccount.name}")
        Space8h()
        Space8h()
        if (startMsBind) {
            Text("即将登录微软账号，点击复制浏览器中打开链接，请在5分钟内登录")
            Text("不要切换到其他页面！", fontWeight = FontWeight.Bold)
            Text("登录完成后稍等10秒，会自动读取账号信息以进行下一步")
        }
        msaDeviceCode?.let { msaDeviceCode ->
            Text(
                text = msaDeviceCode.directVerificationUri,
                color = MaterialTheme.colors.primary,
                style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                modifier = Modifier
                    .clickable {
                        copyToClipboard(msaDeviceCode.directVerificationUri)
                    }
            )
        }

        Space8h()
        if (!startMsBind) {
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
        } else {
            Text("已绑定微软MC正版号")
        }

        msaInfo?.let { info ->
            Text("读取信息成功！昵称：${info.name} MSID: ${info.uuid}")
            RowV {
                Text("绑定后将不能修改，如果确定账号信息正确，")
                Space8w()
                CircleIconButton(
                    "\uDB82\uDE50",
                    "ok",
                    bgColor = MaterialColor.GREEN_900.color,
                    enabled = !pendingBind
                ) {
                    scope.rdiRequestU("player/bind-ms", body = info.json, onDone = {
                        clearMsaState()
                        pendingBind = false
                    }, onErr = {
                        errMsg = "绑定失败：${it.message}，请重试"
                    }) {
                        val jwt = loggedAccount.jwt
                        loggedAccount = loggedAccount.copy(msid = info.uuid).also { it.jwt = jwt }
                    }
                }
            }
        }

    }

    if (showInviteDialog) {
        InvitePlayerDialog(
            onDismiss = { showInviteDialog = false },
            onSuccess = {
                showInviteDialog = false
                // Refresh invited players list
                loading = true
                scope.rdiRequest<List<RAccount.Dto>>(
                    "player/invite",
                    onDone = { loading = false },
                    onErr = {}
                ) {
                    it.data?.let { invitedPlayers = it }
                }
            }
        )
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("内存信息 总可用 ${totalMemoryMb}MB")
        Space8h()
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(end = 16.dp)) {
                OutlinedTextField(
                    label = { Text("限制MC可用内存 (MB，0 或空为不限制)") },
                    value = maxMemoryText,
                    onValueChange = onMaxMemoryChange,
                    singleLine = true,
                    modifier = Modifier.width(260.dp)
                )
            }
            // HwSpec memory display is desktop-only and handled by SettingsService
        }
        Space8h()
        Text("当前Java：${javaExePath}")
        Space8h()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                label = { Text("Java25主程序路径") },
                value = jre25Path,
                onValueChange = onJre25Change,
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            CircleIconButton(
                icon = "\uE8B6",
                tooltip = "选择Java25",
                bgColor = MaterialColor.BLUE_800.color,
                size = 36,
                showText = false
            ) {
                onPickJre25()
            }
        }
        Space8h()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                label = { Text("Java21主程序路径（可选）") },
                value = jre21Path,
                onValueChange = onJre21Change,
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            CircleIconButton(
                icon = "\uE8B6",
                tooltip = "选择Java21",
                bgColor = MaterialColor.BLUE_800.color,
                size = 36,
                showText = false
            ) {
                onPickJre21()
            }
        }
        Space8h()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                label = { Text("Java8主程序路径（可选）") },
                value = jre8Path,
                onValueChange = onJre8Change,
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            CircleIconButton(
                icon = "\uE8B6",
                tooltip = "选择Java8",
                bgColor = MaterialColor.BLUE_800.color,
                size = 36,
                showText = false
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
    showApiKey: Boolean,
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
    onToggleApiKey: () -> Unit,
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("AI设置", style = MaterialTheme.typography.h6)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            profiles.forEach { item ->
                val selected = item.id == selectedProfileId
                val active = item.id == activeProfileId
                Text(
                    text = "${if (active) "\uF00C ".asIconText else ""}${item.name.ifBlank { item.provider.displayName }}",
                    color = if (selected) Color.White else MaterialColor.GRAY_900.color,
                    modifier = Modifier
                        .background(
                            if (selected) MaterialTheme.colors.primary else MaterialColor.GRAY_200.color,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectProfile(item.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            CircleIconButton(
                icon = "\uF067",
                tooltip = "新增AI配置",
                bgColor = MaterialColor.BLUE_800.color,
                size = 34,
                showText = false,
                onClick = onAddProfile
            )
            CircleIconButton(
                icon = "\uF1F8",
                tooltip = "删除AI配置",
                bgColor = MaterialColor.RED_700.color,
                size = 34,
                showText = false,
                enabled = profiles.size > 1,
                onClick = onDeleteProfile
            )
            CircleIconButton(
                icon = "\uF00C",
                tooltip = "使用此AI配置",
                bgColor = MaterialColor.GREEN_900.color,
                size = 34,
                showText = false,
                enabled = profile.id != activeProfileId,
                onClick = onSetActiveProfile
            )
        }
        OutlinedTextField(
            label = { Text("配置名称") },
            value = profile.name,
            onValueChange = onProfileNameChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            AiProvider.entries.forEach { provider ->
                RadioButton(
                    selected = profile.provider == provider,
                    onClick = { onProviderChange(provider) }
                )
                Text(provider.displayName)
                Space8w()
            }

        }
        OutlinedTextField(
            label = { Text("API Base URL") },
            value = profile.baseUrl,
            onValueChange = onBaseUrlChange,
            singleLine = true,
            enabled = profile.provider != AiProvider.DEEPSEEK,
            modifier = Modifier.fillMaxWidth()
        )
        PasswordField(
            value = profile.apiKey,
            onValueChange = onApiKeyChange,
            label = "API Key",
            showPassword = showApiKey,
            onToggleVisibility = onToggleApiKey,
            onEnter = {}
        )
        OutlinedTextField(
            label = { Text("上下文上限Tokens") },
            value = profile.contextLimitTokens.toString(),
            onValueChange = onContextLimitChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "范围64000-1000000",
            color = MaterialColor.GRAY_700.color,
            style = MaterialTheme.typography.body2
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("推理强度")
            Box {
                Text(
                    text = "${profile.reasoningEffort.displayName} \uE70D".asIconText,
                    color = MaterialColor.GRAY_900.color,
                    modifier = Modifier
                        .background(MaterialColor.GRAY_200.color, RoundedCornerShape(8.dp))
                        .clickable { reasoningMenuExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                DropdownMenu(
                    expanded = reasoningMenuExpanded,
                    onDismissRequest = { reasoningMenuExpanded = false }
                ) {
                    AiReasoningEffort.optionsFor(profile.provider).forEach { effort ->
                        DropdownMenuItem(onClick = {
                            onReasoningEffortChange(effort)
                            reasoningMenuExpanded = false
                        }) {
                            Text(effort.displayName)
                        }
                    }
                }
            }
            Text(
                text = "Auto不发送参数",
                color = MaterialColor.GRAY_700.color,
                style = MaterialTheme.typography.body2
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            OutlinedTextField(
                label = { Text("模型") },
                value = profile.model,
                onValueChange = onModelChange,
                singleLine = true,
                modifier = Modifier.weight(1f),
                trailingIcon = {
                    Text(
                        text = "\uE70D".asIconText,
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
                    DropdownMenuItem(onClick = {
                        onModelChange(candidate)
                        modelMenuExpanded = false
                    }) {
                        Text(candidate)
                    }
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
        modelRefreshError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.body2
            )
        }
        modelRefreshMessage?.let { message ->
            Text(
                text = message,
                color = MaterialColor.GRAY_800.color,
                style = MaterialTheme.typography.body2
            )
        }
        balanceMessage?.let { message ->
            Text(
                text = message,
                color = MaterialColor.GRAY_800.color,
                style = MaterialTheme.typography.body2
            )
        }
    }
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
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = preferMcMirror, onCheckedChange = onPreferMcMirrorChange)
            Text("优先使用国内镜像下载MC资源")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = preferModMirror, onCheckedChange = onPreferModMirrorChange)
            Text("优先使用国内镜像下载Mod")
        }
        Space8h()
        AutoRouteStatus(
            switchingNode = switchingNode,
            onAutoSwitchFastestNode = onAutoSwitchFastestNode,
            onUseGameBackupNode = onUseGameBackupNode
        )

        // Proxy settings — desktop only
        if (isDesktop) {
            Space8h()
            Text("代理设置")
            val mode = when {
                !proxyEnabled -> 0
                proxySystem -> 1
                else -> 2
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == 0, onClick = {
                        onProxyEnabledChange(false)
                        onProxySystemChange(false)
                    })
                    Text("不使用代理")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == 1, onClick = {
                        onProxyEnabledChange(true)
                        onProxySystemChange(true)
                    })
                    Text("使用系统代理")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == 2, onClick = {
                        onProxyEnabledChange(true)
                        onProxySystemChange(false)
                    })
                    Text("使用自定义代理")
                }
            }

            if (mode == 2) {
                Space8h()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        label = { Text("代理主机") },
                        value = proxyHost,
                        onValueChange = onProxyHostChange,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        label = { Text("端口") },
                        value = proxyPort,
                        onValueChange = onProxyPortChange,
                        singleLine = true,
                        modifier = Modifier.width(120.dp)
                    )
                }
                Space8h()
                OutlinedTextField(
                    label = { Text("用户名（可选）") },
                    value = proxyUsr,
                    onValueChange = onProxyUsrChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Space8h()
                OutlinedTextField(
                    label = { Text("密码（可选）") },
                    value = proxyPwd,
                    onValueChange = onProxyPwdChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "网络入口"
        )
        routeState.nodeName?.let {
            Text("当前节点: $it")
        }
        Text(if (routeState.useBackupNode) "当前使用加速入口" else "当前使用主入口")
        FlowRowV {

            CircleIconButton(
                "\uDB80\uDC02",
                if (switchingNode) "已切换节点" else "自动切换最快节点",
                bgColor = MaterialColor.TEAL_900.color,
                enabled = !switchingNode,
                onClick = onAutoSwitchFastestNode
            )
            Space8w()
            CircleIconButton(
                "\uDB80\uDC02",
                "临时使用备用节点",
                bgColor = MaterialColor.BLUE_900.color,
                enabled = !switchingNode,
                onClick = onUseGameBackupNode
            )
        }
    }
}

@Composable
private fun ChangeProfileDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var showPassword by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val account = loggedAccount
    var name by remember { mutableStateOf(account.name) }
    var pwd by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    androidx.compose.material.AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("修改信息") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("昵称") },
                    singleLine = true,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth()
                )
                PasswordField(
                    value = pwd,
                    onValueChange = { pwd = it },
                    label = "新密码 留空则不修改",
                    enabled = !submitting,
                    showPassword = showPassword,
                    onToggleVisibility = { showPassword = !showPassword },
                    onEnter = {}
                )
                errorMessage?.let { Text(it, color = MaterialTheme.colors.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    val validation =
                        calebxzhou.rdi.client.service.SettingsService.validateProfileChange(name, pwd, account.name)
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
                            if (pwd.isNotEmpty()) {
                                loggedAccount = PlayerService.login(account._id.toHexString(), pwd).getOrThrow()
                            }
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

@Composable
private fun InvitePlayerDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var regCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    androidx.compose.material.AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("邀请朋友注册") },
        text = {
            Column {
                Text(
                    "在下方粘贴朋友发给你的注册码。",
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = regCode,
                    onValueChange = { regCode = it },
                    label = { Text("注册码") },
                    placeholder = { Text("粘贴注册码...") },
                    singleLine = false,
                    maxLines = 5,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let {
                    Space8h()
                    Text(it, color = MaterialTheme.colors.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting && regCode.isNotBlank(),
                onClick = {
                    if (regCode.isBlank()) {
                        errorMessage = "请输入注册码"
                        return@TextButton
                    }

                    submitting = true
                    errorMessage = null
                    scope.rdiRequestU(
                        "player/invite",
                        body = regCode,
                        onDone = { submitting = false },
                        onErr = { errorMessage = "邀请失败: ${it.message}" }
                    ) {
                        onSuccess()
                    }
                }
            ) {
                if (submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("确定")
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
