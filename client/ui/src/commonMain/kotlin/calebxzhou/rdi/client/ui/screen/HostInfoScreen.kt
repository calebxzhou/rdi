package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField as M3OutlinedTextField
import androidx.compose.material3.RadioButton as M3RadioButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import calebxzhou.mykotutils.std.secondsToHumanDateTime
import calebxzhou.rdi.client.auth.LocalCredentials
import calebxzhou.rdi.client.auth.updateLastPlayHost
import calebxzhou.rdi.client.model.UiMod
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.net.sse
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.service.GithubExtraModService
import calebxzhou.rdi.client.service.GithubRelease
import calebxzhou.rdi.client.service.GithubReleaseAsset
import calebxzhou.rdi.client.service.GithubRepoRef
import calebxzhou.rdi.client.service.StartPlayResult
import calebxzhou.rdi.client.service.codeeditor.CodeLanguage
import calebxzhou.rdi.client.service.codeeditor.validateCodeContent
import calebxzhou.rdi.client.service.hydrateToUiMods
import calebxzhou.rdi.client.service.startPlay
import calebxzhou.rdi.client.service.toUiMods
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.*
import calebxzhou.rdi.common.extension.isAdmin
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.service.ModService
import calebxzhou.rdi.model.Role
import io.ktor.client.plugins.sse.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bson.types.ObjectId
import java.net.URI

/**
 * calebxzhou @ 2026-01-15 19:38
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HostInfoScreen(
    hostId: ObjectId,
    onBack: () -> Unit = {},
    onOpenModpackInfo: (String) -> Unit,
    onOpenMcPlay: (McPlayArgs) -> Unit,
    onOpenMcVersions: (McVersion?) -> Unit,
    onOpenHostEdit: (Host.DetailVo) -> Unit,
    onOpenTaskList: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var okMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var extraModsLoading by remember { mutableStateOf(false) }
    var hostDetail by remember { mutableStateOf<Host.DetailVo?>(null) }
    var modpackDetail by remember { mutableStateOf<Modpack.DetailVo?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteWorldWhenDeleteHost by remember { mutableStateOf(false) }
    var showUpdateConfirm by remember { mutableStateOf(false) }
    var roleChangeConfirm by remember { mutableStateOf<RoleChange?>(null) }
    var transferConfirm by remember { mutableStateOf<ObjectId?>(null) }
    var kickConfirm by remember { mutableStateOf<ObjectId?>(null) }
    var quitConfirm by remember { mutableStateOf(false) }
    var stopConfirm by remember { mutableStateOf(false) }
    var restartConfirm by remember { mutableStateOf(false) }
    var forceStopConfirm by remember { mutableStateOf(false) }
    var showCommandDialog by remember { mutableStateOf(false) }
    var hostCommand by remember { mutableStateOf("") }
    var hostCommandSending by remember { mutableStateOf(false) }
    var hostCommandResult by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var consoleState by remember { mutableStateOf(ConsoleState()) }
    var logStreamSseJob by remember { mutableStateOf<Job?>(null) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteQq by remember { mutableStateOf("") }
    var installConfirmTask by remember { mutableStateOf<Task2?>(null) }
    var showAddExtraModDialog by remember { mutableStateOf(false) }
    var addExtraModLoading by remember { mutableStateOf(false) }
    var selectAllExtraMods by remember { mutableStateOf(false) }
    var addExtraModLoadingText by remember { mutableStateOf("") }
    var addExtraModDialogError by remember { mutableStateOf<String?>(null) }
    var extraModPlatform by remember { mutableStateOf("github") }
    val extraModProjectIdState = rememberTextFieldState()
    val extraModSlugState = rememberTextFieldState()
    val extraModFileIdState = rememberTextFieldState()
    val extraModHashState = rememberTextFieldState()
    val extraModDownloadUrlsState = rememberTextFieldState()
    val extraModGithubRepoUrlState = rememberTextFieldState()
    var extraModGithubRepo by remember { mutableStateOf<GithubRepoRef?>(null) }
    var extraModGithubReleases by remember { mutableStateOf<List<GithubRelease>>(emptyList()) }
    var selectedGithubAsset by remember { mutableStateOf<GithubReleaseAsset?>(null) }
    var extraModSide by remember { mutableStateOf(Mod.Side.BOTH) }
    var privateThingsSubTab by remember { mutableStateOf(0) }
    var selectedExtraModKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var disabledMods by remember { mutableStateOf<List<Mod>>(emptyList()) }
    var selectAllModListMods by remember { mutableStateOf(false) }
    var selectedModListKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectAllDisabledMods by remember { mutableStateOf(false) }
    var selectedDisabledModKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var removeExtraModConfirm by remember { mutableStateOf<Mod?>(null) }
    var hydratedExtraUiMods by remember { mutableStateOf<List<UiMod>>(emptyList()) }
    var extraModsLoadVersion by remember { mutableStateOf(0) }
    var configFilesLoading by remember { mutableStateOf(false) }
    var configContentLoading by remember { mutableStateOf(false) }
    var configSaving by remember { mutableStateOf(false) }
    var configFiles by remember { mutableStateOf<List<Host.ConfigFileEntry>>(emptyList()) }
    var selectedConfigPath by remember { mutableStateOf<String?>(null) }
    var configEditorText by remember { mutableStateOf("") }
    var configOriginalText by remember { mutableStateOf("") }
    var configSyntaxErrorMessage by remember { mutableStateOf<String?>(null) }
    var configStatusMessage by remember { mutableStateOf<String?>(null) }
    var configEditorOpen by remember { mutableStateOf(false) }

    val memberTabIndex = 0
    val extraModsTabIndex = 1
    val consoleTabIndex = 2
    val configTabIndex = 3

    fun resetAddExtraModDialog() {
        addExtraModLoading = false
        addExtraModLoadingText = ""
        addExtraModDialogError = null
        extraModPlatform = "github"
        extraModProjectIdState.clearText()
        extraModSlugState.clearText()
        extraModFileIdState.clearText()
        extraModHashState.clearText()
        extraModDownloadUrlsState.clearText()
        extraModGithubRepoUrlState.clearText()
        extraModGithubRepo = null
        extraModGithubReleases = emptyList()
        selectedGithubAsset = null
        extraModSide = Mod.Side.BOTH
    }

    fun refreshExtraMods(rawMods: List<Mod>, currentHostId: ObjectId?) {
        val loadVersion = extraModsLoadVersion + 1
        extraModsLoadVersion = loadVersion
        hydratedExtraUiMods = emptyList()
        extraModsLoading = rawMods.isNotEmpty()
        if (rawMods.isEmpty()) return
        if (currentHostId == null) {
            extraModsLoading = false
            return
        }
        scope.launch {
            try {
                val hydratedUiMods = withContext(Dispatchers.IO) {
                    runCatching { rawMods.hydrateToUiMods() }
                        .getOrDefault(rawMods.toUiMods())
                }
                if (hostDetail?._id == currentHostId && extraModsLoadVersion == loadVersion) {
                    hydratedExtraUiMods = hydratedUiMods
                }
            } finally {
                if (hostDetail?._id == currentHostId && extraModsLoadVersion == loadVersion) {
                    extraModsLoading = false
                }
            }
        }
    }

    fun applyExtraMods(updatedMods: List<Mod>) {
        val currentHostId = hostDetail?._id
        hostDetail = hostDetail?.copy(extraMods = updatedMods)
        refreshExtraMods(updatedMods, currentHostId)
    }

    fun applyDisabledMods(updatedMods: List<Mod>) {
        disabledMods = updatedMods
    }

    fun loadConfigFile(path: String) {
        val switchingFile = selectedConfigPath != path
        selectedConfigPath = path
        configStatusMessage = "正在读取 $path"
        configSyntaxErrorMessage = null
        if (switchingFile) {
            configEditorText = ""
            configOriginalText = ""
        }
        configContentLoading = true
        scope.rdiRequest<Host.ConfigFileContentVo>(
            path = "host/$hostId/config/file",
            params = mapOf("path" to path),
            onOk = { response ->
                val file = response.data ?: run {
                    errorMessage = "读取配置文件失败"
                    return@rdiRequest
                }
                selectedConfigPath = file.path
                configEditorText = file.content
                configOriginalText = file.content
                configSyntaxErrorMessage = validateCodeContent(
                    text = file.content,
                    language = CodeLanguage.fromPath(file.path)
                )?.takeIf { !it.isValid }?.message
                configStatusMessage = "已打开 ${file.path}"
            },
            onErr = { errorMessage = it.message ?: "读取配置文件失败" },
            onDone = { configContentLoading = false }
        )
    }

    fun loadConfigFiles(preferredPath: String? = selectedConfigPath) {
        configFilesLoading = true
        scope.rdiRequest<List<Host.ConfigFileEntry>>(
            path = "host/$hostId/config/files",
            onOk = { response ->
                val files = response.data ?: emptyList()
                configFiles = files
                if (files.isEmpty()) {
                    configEditorOpen = false
                    selectedConfigPath = null
                    configEditorText = ""
                    configOriginalText = ""
                    configSyntaxErrorMessage = null
                    configStatusMessage = "当前没有可编辑配置文件"
                    return@rdiRequest
                }

                when {
                    preferredPath != null && files.any { it.path == preferredPath } && selectedConfigPath == null -> {
                        loadConfigFile(preferredPath)
                    }

                    selectedConfigPath == null -> {
                        loadConfigFile(files.first().path)
                    }

                    selectedConfigPath != null && files.none { it.path == selectedConfigPath } -> {
                        if (configEditorText == configOriginalText) {
                            loadConfigFile(files.first().path)
                        } else {
                            configStatusMessage = "当前文件已不在配置列表中，请先保存或还原内容"
                        }
                    }
                }
            },
            onErr = { errorMessage = it.message ?: "加载配置文件列表失败" },
            onDone = { configFilesLoading = false }
        )
    }

    fun saveConfigFile() {
        val path = selectedConfigPath ?: return
        configSaving = true
        scope.rdiRequest<Host.ConfigFileContentVo>(
            path = "host/$hostId/config/file",
            method = HttpMethod.Put,
            body = serdesJson.encodeToString(
                Host.ConfigFileSaveDto(
                    path = path,
                    content = configEditorText
                )
            ),
            onOk = { response ->
                val saved = response.data ?: run {
                    errorMessage = "保存配置文件失败"
                    return@rdiRequest
                }
                selectedConfigPath = saved.path
                configOriginalText = saved.content
                configEditorText = saved.content
                configSyntaxErrorMessage = null
                configStatusMessage = "已保存 ${saved.path}"
                configFiles = configFiles.map { entry ->
                    if (entry.path == saved.path) {
                        entry.copy(size = saved.size, updateTime = saved.updateTime)
                    } else {
                        entry
                    }
                }
                if (configFiles.none { it.path == saved.path }) {
                    configFiles = (configFiles + Host.ConfigFileEntry(saved.path, saved.size, saved.updateTime))
                        .sortedBy { it.path.lowercase() }
                }
            },
            onErr = { errorMessage = it.message ?: "保存配置文件失败" },
            onDone = { configSaving = false }
        )
    }

    fun reload() {
        loading = true
        errorMessage = null
        scope.rdiRequest<Host.DetailVo>(
            path = "host/$hostId/detail",
            onOk = { response ->
                val detail = response.data
                if (detail == null) {
                    errorMessage = "无法加载房间信息"
                    loading = false
                    return@rdiRequest
                }
                hostDetail = detail
                refreshExtraMods(detail.extraMods, detail._id)
                applyDisabledMods(detail.disabledMods)
                scope.rdiRequest<Modpack.DetailVo>(
                    path = "modpack/${detail.modpack.id}",
                    onOk = { modpackResponse ->
                        modpackDetail = modpackResponse.data
                    },
                    onErr = {
                        errorMessage = "加载整合包信息失败: ${it.message}"
                        modpackDetail = null
                    },
                    onDone = { loading = false }
                )
            },
            onErr = {
                errorMessage = "加载房间信息失败: ${it.message}"
            },
            onDone = { loading = false }
        )
    }

    LaunchedEffect(hostId) {
        privateThingsSubTab = 0
        hydratedExtraUiMods = emptyList()
        selectedExtraModKeys = emptySet()
        disabledMods = emptyList()
        selectedModListKeys = emptySet()
        selectedDisabledModKeys = emptySet()
        configFiles = emptyList()
        selectedConfigPath = null
        configEditorText = ""
        configOriginalText = ""
        configSyntaxErrorMessage = null
        configStatusMessage = null
        reload()
    }
    LaunchedEffect(selectedTab) {
        if (selectedTab > configTabIndex) {
            selectedTab = memberTabIndex
        }
    }
    LaunchedEffect(okMessage) {
        okMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            okMessage = null
        }
    }

    val host = hostDetail
    val meAdmin = host?.let { it.isAdmin(loggedAccount) || loggedAccount.isDav } ?: false
    val meOwner = host?.let { it.ownerId == loggedAccount._id || loggedAccount.isDav } ?: false
    val canManageExtraMods = meAdmin || meOwner
    val canManageConfigFiles = meAdmin || meOwner
    val extraMods = host?.extraMods.orEmpty()
    val extraUiMods = when {
        hydratedExtraUiMods.isNotEmpty() -> hydratedExtraUiMods
        extraMods.isNotEmpty() -> extraMods.toUiMods()
        else -> emptyList()
    }
    val baseVersionMods = modpackDetail?.versions
        ?.firstOrNull { it.name == host?.packVer }
        ?.mods
        ?.filterNot { versionMod -> disabledMods.any { sameMod(it, versionMod) } }
        .orEmpty()
    val configDirty = selectedConfigPath != null && configEditorText != configOriginalText

    fun switchExtraModPlatform(platform: String) {
        extraModPlatform = platform
        addExtraModDialogError = null
        if (platform != "github") {
            extraModGithubRepo = null
            extraModGithubReleases = emptyList()
            selectedGithubAsset = null
        }
    }

    fun submitExtraMod(mod: Mod) {
        val dedupeResult = filterExtraModsForAdding(
            candidateMods = listOf(mod),
            existingMods = extraMods + baseVersionMods
        )
        val acceptedMod = dedupeResult.acceptedMods.singleOrNull()
        if (acceptedMod == null) {
            addExtraModDialogError = dedupeResult.rejectedMessages.firstOrNull() ?: "该Mod无法添加"
            addExtraModLoading = false
            addExtraModLoadingText = ""
            return
        }
        addExtraModLoading = true
        addExtraModLoadingText = "添加中..."
        addExtraModDialogError = null
        scope.rdiRequestU(
            path = "host/$hostId/mods/extra",
            method = HttpMethod.Post,
            body = serdesJson.encodeToString(listOf(acceptedMod)),
            onOk = {
                okMessage = "已提交附加Mod添加任务，请在邮件中查看进度"
                showAddExtraModDialog = false
                resetAddExtraModDialog()
            },
            onErr = {
                addExtraModDialogError = it.message ?: "添加附加Mod失败"
            },
            onDone = {
                addExtraModLoading = false
                addExtraModLoadingText = ""
            }
        )
    }

    fun loadGithubReleases() {
        addExtraModLoading = true
        addExtraModLoadingText = "读取GitHub Release..."
        addExtraModDialogError = null
        scope.launch {
            GithubExtraModService.fetchReleases(extraModGithubRepoUrlState.text.toString())
                .onSuccess { (repo, releases) ->
                    extraModGithubRepo = repo
                    extraModGithubReleases = releases
                    selectedGithubAsset = null
                }
                .onFailure {
                    addExtraModDialogError = it.message ?: "读取GitHub Release失败"
                }
            addExtraModLoading = false
            addExtraModLoadingText = ""
        }
    }

    fun submitGithubExtraMod() {
        val repo = extraModGithubRepo
        val asset = selectedGithubAsset
        if (repo == null || extraModGithubReleases.isEmpty()) {
            loadGithubReleases()
            return
        }
        if (asset == null) {
            addExtraModDialogError = "请先选择1个Release文件"
            return
        }
        addExtraModLoading = true
        addExtraModLoadingText = "准备GitHub Mod..."
        addExtraModDialogError = null
        scope.launch {
            GithubExtraModService.buildExtraModFromAsset(
                repo = repo,
                asset = asset,
                side = extraModSide,
                onProgress = { addExtraModLoadingText = it }
            ).onSuccess { mod ->
                submitExtraMod(mod)
            }.onFailure {
                addExtraModDialogError = it.message ?: "GitHub Mod处理失败"
                addExtraModLoading = false
                addExtraModLoadingText = ""
            }
        }
    }

    LaunchedEffect(extraMods) {
        val currentKeys = extraMods.map(::extraModKey).toSet()
        selectedExtraModKeys = selectedExtraModKeys.intersect(currentKeys)
    }

    LaunchedEffect(extraMods, selectedExtraModKeys) {
        selectAllExtraMods = extraMods.isNotEmpty() && selectedExtraModKeys.size == extraMods.size
    }

    LaunchedEffect(baseVersionMods) {
        val currentKeys = baseVersionMods.map(::extraModKey).toSet()
        selectedModListKeys = selectedModListKeys.intersect(currentKeys)
    }

    LaunchedEffect(baseVersionMods, selectedModListKeys) {
        selectAllModListMods = baseVersionMods.isNotEmpty() && selectedModListKeys.size == baseVersionMods.size
    }

    LaunchedEffect(disabledMods) {
        val currentKeys = disabledMods.map(::extraModKey).toSet()
        selectedDisabledModKeys = selectedDisabledModKeys.intersect(currentKeys)
    }

    LaunchedEffect(disabledMods, selectedDisabledModKeys) {
        selectAllDisabledMods = disabledMods.isNotEmpty() && selectedDisabledModKeys.size == disabledMods.size
    }

    LaunchedEffect(selectedTab, host?._id, canManageConfigFiles) {
        if (selectedTab == configTabIndex && host != null && canManageConfigFiles && configFiles.isEmpty() && !configFilesLoading) {
            loadConfigFiles()
        }
    }

    DisposableEffect(selectedTab, hostId) {
        if (selectedTab != consoleTabIndex) {
            onDispose { }
        } else {
            consoleState.clear()
            logStreamSseJob?.cancel()
            logStreamSseJob = scope.sse(
                path = "host/$hostId/log/stream",
                bufferPolicy = SSEBufferPolicy.LastEvents(50),
                onEvent = { event ->
                    if (event.event == "heartbeat") return@sse
                    if (event.event == "error") {
                        errorMessage = "读取日志错误: ${event.data ?: "unknown"}"
                        logStreamSseJob?.cancel()
                        logStreamSseJob = null
                        return@sse
                    }
                    val payload = event.data?.ifBlank { null } ?: return@sse
                    scope.launch {
                        payload.lineSequence()
                            .map { it.trimEnd('\r') }
                            .filter { it.isNotBlank() }
                            .forEach { consoleState.append(it) }
                    }
                },
                onError = { throwable ->
                    errorMessage = "读取日志错误: ${throwable.message}"
                    logStreamSseJob?.cancel()
                    logStreamSseJob = null
                }
            )
            onDispose {
                logStreamSseJob?.cancel()
                logStreamSseJob = null
            }
        }
    }

    errorMessage?.let { message ->
        AlertErr(message) { errorMessage = null }
    }

    fun startPlay(host: Host.DetailVo) {
        scope.launch {
            val args = try {
                host.startPlay()
            } catch (e: Exception) {
                errorMessage = e.message ?: "无法开始游玩"
                return@launch
            }
            when (args) {
                is StartPlayResult.Ready -> {
                    LocalCredentials.read().updateLastPlayHost(
                        id = host._id.toHexString(),
                        name = host.name
                    )
                    if (onOpenMcPlay != null) {
                        onOpenMcPlay(args.args)
                    } else {
                        errorMessage = "暂不支持在此页面游玩"
                    }
                }

                is StartPlayResult.NeedInstall -> {
                    installConfirmTask = args.task
                }

                is StartPlayResult.NeedMc -> {
                    errorMessage = "未安装MC版本资源：${args.ver.mcVer}，请先下载"
                    onOpenMcVersions?.invoke(args.ver)
                }
            }
        }
    }

    MainBox {
        MainColumn {
            TitleRow(title = host?.name ?: "房间详情", onBack = onBack) {

                    host?.let { host ->
                        Text("房主：")
                        HeadButton(host.ownerId)
                        Space8w()
                        CircleIconButton(
                            icon = "\uF04B",
                            tooltip = "开始游玩",
                            bgColor = MaterialColor.GREEN_900.color,
                        ) {
                            startPlay(host)
                        }
                        Space8w()
                        if (meAdmin) {
                            CircleIconButton(
                                icon = "\uF013",
                                tooltip = "设置",
                                bgColor = MaterialColor.YELLOW_900.color
                            ) {
                                onOpenHostEdit(host)
                            }
                            if (modpackDetail != null) {
                                Space8w()
                                CircleIconButton(
                                    icon = "\uDB80\uDFD5",
                                    tooltip = "更新"
                                ) { showUpdateConfirm = true }
                            }
                        }
                        if (meOwner) {
                            Space8w()
                            CircleIconButton(
                                icon = "\uEA81",
                                tooltip = "删除",
                                bgColor = MaterialColor.RED_900.color
                            ) {
                                deleteWorldWhenDeleteHost = false
                                showDeleteConfirm = true
                            }
                        }
                    }

            }

            Space8h()

            when {
                loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                host == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = errorMessage ?: "无法加载房间信息", color = MaterialColor.RED_900.color)
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val hostInfoSection = @Composable {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val difficultyText = when (host.difficulty) {
                                    0 -> "和平"
                                    1 -> "简单"
                                    2 -> "普通"
                                    3 -> "困难"
                                    else -> host.difficulty.toString()
                                }
                                val gameModeText = when (host.gameMode) {
                                    0 -> "生存"
                                    1 -> "创造"
                                    2 -> "冒险"
                                    3 -> "旁观"
                                    else -> host.gameMode.toString()
                                }
                                Text("难度：$difficultyText")
                                Text("游戏模式：$gameModeText")
                                Text("世界类型：${host.levelType}")
                                Text("白名单：${if (host.whitelist) "开启" else "关闭"}")
                                Text("允许作弊：${if (host.allowCheats) "开启" else "关闭"}")
                                if (host.gameRules.isNotEmpty()) {
                                    Text("游戏规则覆盖：")
                                    host.gameRules.entries
                                        .sortedBy { it.key }
                                        .forEach { (rule, value) ->
                                            Text(" - $rule = $value")
                                        }
                                } else {
                                    Text("游戏规则覆盖：无")
                                }
                            }
                        }

                        val tabs = listOf(
                            "\uEF69 信息",
                            "\uF02D 私货",
                            "\uDB80\uDD8D 后台",
                            "\uE5FC 配置"
                        )
                        TabRow(
                            selectedTabIndex = selectedTab,
                            backgroundColor = Color.White,
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    modifier = Modifier.padding(0.dp),
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    text = {
                                        Text(
                                            text = title.asIconText,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Clip,
                                            letterSpacing = TextUnit(0f, TextUnitType.Sp)
                                        )
                                    }
                                )
                            }
                        }

                        when (selectedTab) {
                            0 -> {
                                val meMember = host.members.any { it.id == loggedAccount._id }
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item {
                                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                            val infoColumn = @Composable {
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text("整合包：${host.modpack.name} ${host.packVer}".asIconText)
                                                    FlowRowV {
                                                        Text("在线人数：${host.onlinePlayerIds.size}人")
                                                        Space8w()
                                                        host.onlinePlayerIds.forEach {
                                                            HeadButton(it)
                                                        }
                                                    }
                                                    Row {
                                                        Text("创建时间：${host._id.timestamp.secondsToHumanDateTime}".asIconText)
                                                    }
                                                }
                                            }
                                            val modpackCard = @Composable{
                                                host.modpack.ModpackCard(
                                                    modifier = Modifier.width(300.dp),
                                                    onClick = { onOpenModpackInfo?.invoke(host.modpack.id.toHexString()) }
                                                )
                                            }
                                            val compactLayout = maxWidth < 760.dp
                                            if (compactLayout) {
                                                Column(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    infoColumn()
                                                    modpackDetail?.let {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.End
                                                        ) {
                                                            modpackCard()
                                                        }
                                                    }
                                                }
                                            } else {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    infoColumn()
                                                    modpackDetail?.let {
                                                        modpackCard()
                                                    }
                                                }
                                            }
                                        }
                                        hostInfoSection()
                                    }
                                    item {
                                        Divider(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            color = MaterialColor.GRAY_300.color
                                        )
                                        Space8h()
                                        Text("受邀成员：")
                                        Space8h()
                                    }
                                    items(
                                        items = host.members,
                                        key = { member -> member.id }
                                    ) { member ->
                                        val memberColor = when (member.role) {
                                            Role.OWNER -> MaterialColor.YELLOW_900.color
                                            Role.ADMIN -> Color(0xFFC0C0C0)
                                            Role.MEMBER -> Color(0xFFCD7F32)
                                            else -> Color.White
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                HeadButton(
                                                    uid = member.id,
                                                    avatarSize = 28.dp,
                                                    showName = true
                                                )
                                                Space8w()
                                                Box(
                                                    modifier = Modifier
                                                        .background(memberColor, RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = when (member.role) {
                                                            Role.OWNER -> "所有者"
                                                            Role.ADMIN -> "管理员"
                                                            Role.MEMBER -> "成员"
                                                            else -> ""
                                                        },
                                                        fontSize = 12.sp,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.weight(1f))
                                            FlowRowV {
                                                if (meOwner && member.role != Role.OWNER) {
                                                    CircleIconButton(
                                                        "\uDB81\uDEAE", "转让",
                                                        showText = false
                                                    ) {
                                                        transferConfirm = member.id
                                                    }
                                                }
                                                if (meOwner && member.role != Role.OWNER) {
                                                    Space8w()
                                                    val newRole =
                                                        if (member.role == Role.ADMIN) Role.MEMBER else Role.ADMIN
                                                    CircleIconButton(
                                                        "\uEFA6",
                                                        if (member.role == Role.ADMIN) "取消管理员" else "设为管理员",
                                                        showText = false, bgColor = MaterialColor.TEAL_900.color
                                                    ) {
                                                        roleChangeConfirm = RoleChange(member.id, newRole)
                                                    }
                                                }
                                                if (meAdmin && member.role != Role.OWNER) {
                                                    Space8w()
                                                    CircleIconButton(
                                                        "\uF2FE",
                                                        "踢出",
                                                        bgColor = MaterialColor.RED_900.color,
                                                        showText = false
                                                    ) { kickConfirm = member.id }

                                                }
                                            }
                                        }
                                    }
                                    item {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (meMember && !meOwner) {
                                                TextButton(onClick = { quitConfirm = true }) {
                                                    Text("退出受邀成员列表", color = MaterialColor.RED_900.color)
                                                }
                                            }
                                            if (meAdmin) {
                                                TextButton(onClick = { showInviteDialog = true }) {
                                                    Text("+ 邀请成员")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            extraModsTabIndex -> {
                                val selectedExtraMods =
                                    extraMods.filter { extraModKey(it) in selectedExtraModKeys }
                                val selectedModListMods =
                                    baseVersionMods.filter { extraModKey(it) in selectedModListKeys }
                                val selectedDisabledMods =
                                    disabledMods.filter { extraModKey(it) in selectedDisabledModKeys }
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TabRow(
                                        selectedTabIndex = privateThingsSubTab,
                                        backgroundColor = Color.White,
                                    ) {
                                        Tab(
                                            selected = privateThingsSubTab == 0,
                                            onClick = { privateThingsSubTab = 0 },
                                            text = { Text("附加mod") }
                                        )
                                        Tab(
                                            selected = privateThingsSubTab == 1,
                                            onClick = { privateThingsSubTab = 1 },
                                            text = { Text("mod总表") }
                                        )
                                        Tab(
                                            selected = privateThingsSubTab == 2,
                                            onClick = { privateThingsSubTab = 2 },
                                            text = { Text("已停用的mod") }
                                        )
                                        Tab(
                                            selected = privateThingsSubTab == 3,
                                            onClick = { privateThingsSubTab = 3 },
                                            text = { Text("KubeJS（开发中）") }
                                        )
                                    }
                                    when (privateThingsSubTab) {
                                        0 -> HostExtraModsPane(
                                            extraUiMods = extraUiMods,
                                            selectedExtraMods = selectedExtraMods,
                                            selectedExtraModKeys = selectedExtraModKeys,
                                            selectAllExtraMods = selectAllExtraMods,
                                            extraModsLoading = extraModsLoading,
                                            canManageExtraMods = canManageExtraMods,
                                            addExtraModLoading = addExtraModLoading,
                                            addExtraModLoadingText = addExtraModLoadingText,
                                            onToggleSelectAll = {
                                                selectAllExtraMods = it
                                                selectedExtraModKeys = if (it) extraMods.map(::extraModKey).toSet() else emptySet()
                                            },
                                            onToggleSelected = { mod, selected ->
                                                val key = extraModKey(mod)
                                                selectedExtraModKeys = if (selected) {
                                                    selectedExtraModKeys + key
                                                } else {
                                                    selectedExtraModKeys - key
                                                }
                                            },
                                            onDownloadSelected = {
                                                val runId = ClientTaskManager.submit(ModService.downloadModsTask2(selectedExtraMods))
                                                if (onOpenTaskList != null) {
                                                    onOpenTaskList(runId)
                                                } else {
                                                    okMessage = "已加入任务列表"
                                                }
                                            },
                                            onRemoveSelected = {
                                                removeExtraModConfirm = selectedExtraMods.firstOrNull()
                                            },
                                            onAddExtraMod = {
                                                resetAddExtraModDialog()
                                                showAddExtraModDialog = true
                                            }
                                        )

                                        1 -> HostModListPane(
                                            baseVersionMods = baseVersionMods,
                                            selectedModListMods = selectedModListMods,
                                            selectedModListKeys = selectedModListKeys,
                                            selectAllModListMods = selectAllModListMods,
                                            onToggleSelectAll = {
                                                selectAllModListMods = it
                                                selectedModListKeys = if (it) {
                                                    baseVersionMods.map(::extraModKey).toSet()
                                                } else {
                                                    emptySet()
                                                }
                                            },
                                            onToggleSelected = { mod, selected ->
                                                val key = extraModKey(mod)
                                                selectedModListKeys = if (selected) {
                                                    selectedModListKeys + key
                                                } else {
                                                    selectedModListKeys - key
                                                }
                                            },
                                            onDisableSelected = {
                                                scope.rdiRequest<List<Mod>>(
                                                    path = "host/$hostId/mods/disabled",
                                                    method = HttpMethod.Post,
                                                    body = serdesJson.encodeToString(selectedModListMods),
                                                    onOk = { response ->
                                                        applyDisabledMods(response.data ?: emptyList())
                                                        selectedModListKeys = emptySet()
                                                        okMessage = "已停用选中的mod，重启房间后生效"
                                                    },
                                                    onErr = { errorMessage = it.message ?: "停用mod失败" }
                                                )
                                            }
                                        )

                                        2 -> HostDisabledModsPane(
                                            disabledMods = disabledMods,
                                            selectedDisabledMods = selectedDisabledMods,
                                            selectedDisabledModKeys = selectedDisabledModKeys,
                                            selectAllDisabledMods = selectAllDisabledMods,
                                            onToggleSelectAll = {
                                                selectAllDisabledMods = it
                                                selectedDisabledModKeys = if (it) {
                                                    disabledMods.map(::extraModKey).toSet()
                                                } else {
                                                    emptySet()
                                                }
                                            },
                                            onToggleSelected = { mod, selected ->
                                                val key = extraModKey(mod)
                                                selectedDisabledModKeys = if (selected) {
                                                    selectedDisabledModKeys + key
                                                } else {
                                                    selectedDisabledModKeys - key
                                                }
                                            },
                                            onEnableSelected = {
                                                scope.rdiRequest<List<Mod>>(
                                                    path = "host/$hostId/mods/disabled",
                                                    method = HttpMethod.Delete,
                                                    body = serdesJson.encodeToString(selectedDisabledMods),
                                                    onOk = { response ->
                                                        applyDisabledMods(response.data ?: emptyList())
                                                        selectedDisabledModKeys = emptySet()
                                                        okMessage = "已恢复选中的mod，重启房间后生效"
                                                    },
                                                    onErr = { errorMessage = it.message ?: "启用mod失败" }
                                                )
                                            }
                                        )
                                        3->{}
                                        else -> {}
                                    }
                                }
                            }

                            consoleTabIndex -> {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Console(state = consoleState, modifier = Modifier.fillMaxSize())
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        CircleIconButton(
                                            icon = "\uF04B",
                                            tooltip = "启动",
                                            bgColor = MaterialColor.GREEN_900.color,
                                            showText = false
                                        ) {
                                            scope.rdiRequestU(
                                                path = "host/$hostId/start",
                                                method = HttpMethod.Post,
                                                onOk = { okMessage = "启动指令已发送" },
                                                onErr = { errorMessage = it.message ?: "启动失败" }
                                            )
                                        }
                                        if (meAdmin || meOwner) {
                                            CircleIconButton(
                                                icon = "\uF120",
                                                tooltip = "发送命令",
                                                bgColor = MaterialColor.TEAL_900.color,
                                                showText = false
                                            ) {
                                                hostCommandResult = null
                                                showCommandDialog = true
                                            }
                                        }
                                        CircleIconButton(
                                            icon = "\uF01E",
                                            tooltip = "重启",
                                            bgColor = MaterialColor.BLUE_800.color,
                                            showText = false
                                        ) {
                                            restartConfirm = true
                                        }
                                        CircleIconButton(
                                            icon = "\uF04D",
                                            tooltip = "停止",
                                            bgColor = MaterialColor.RED_700.color,
                                            showText = false
                                        ) {
                                            stopConfirm = true
                                        }
                                        CircleIconButton(
                                            icon = "\uF05E",
                                            tooltip = "强制停止",
                                            bgColor = MaterialColor.RED_900.color,
                                            showText = false
                                        ) {
                                            forceStopConfirm = true
                                        }
                                    }
                                }
                            }

                            configTabIndex -> {
                                if (!canManageConfigFiles) {
                                    Text("仅房间管理员可编辑配置文件", color = MaterialColor.GRAY_700.color)
                                } else {
                                    HostConfigEditor(
                                        files = configFiles,
                                        selectedPath = selectedConfigPath,
                                        loadingFiles = configFilesLoading,
                                        statusMessage = configStatusMessage,
                                        onSelectFile = { path ->
                                            if (configDirty && path != selectedConfigPath) {
                                                errorMessage = "当前配置有未保存修改，请先保存或还原"
                                            } else {
                                                configEditorOpen = true
                                                loadConfigFile(path)
                                            }
                                        },
                                        onReloadList = {
                                            loadConfigFiles()
                                        }
                                    )
                                }
                            }

                        }
                    }
                }

            }
        }
        BottomSnakebar(snackbarHostState)
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("确认删除房间吗？")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = deleteWorldWhenDeleteHost,
                            enabled = hostDetail?.worldId != null,
                            onCheckedChange = { deleteWorldWhenDeleteHost = it }
                        )
                        Text(
                            if (hostDetail?.worldId != null) {
                                "同时删除关联存档（不可恢复）"
                            } else {
                                "该房间没有关联存档"
                            },
                            color = if (hostDetail?.worldId != null) {
                                MaterialTheme.colors.onSurface
                            } else {
                                MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            }
                        )
                    }
                    Text(
                        if (deleteWorldWhenDeleteHost && hostDetail?.worldId != null) {
                            "房间和存档都会被删除，无法恢复。"
                        } else {
                            "默认仅删除房间，存档会保留，可导出或复用。"
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.rdiRequestU(
                            path = "host/$hostId",
                            method = HttpMethod.Delete,
                            body = serdesJson.encodeToString(
                                Host.DeleteDto(
                                    deleteWorld = deleteWorldWhenDeleteHost && hostDetail?.worldId != null
                                )
                            ),
                            onOk = {
                                okMessage = "已删除"
                                onBack()
                            },
                            onErr = { errorMessage = it.message ?: "删除失败" }
                        )
                        showDeleteConfirm = false
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showUpdateConfirm && modpackDetail != null) {
        ConfirmDialog(
            title = "确认更新",
            message = "将更新房间当前的整合包《${modpackDetail!!.name}》到最新版本。",
            onConfirm = {
                scope.rdiRequestU(
                    path = "host/$hostId/update",
                    method = HttpMethod.Post,
                    onOk = {
                        okMessage = "已提交更新"
                        reload()
                    },
                    onErr = { errorMessage = it.message ?: "更新失败" }
                )
                showUpdateConfirm = false
            },
            onDismiss = { showUpdateConfirm = false }
        )
    }

    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = { Text("邀请成员") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("请输入对方QQ号：")
                    OutlinedTextField(
                        value = inviteQq,
                        onValueChange = { inviteQq = it },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val qq = inviteQq.trim()
                    if (qq.isBlank()) {
                        errorMessage = "QQ不能为空"
                        return@TextButton
                    }
                    scope.rdiRequestU(
                        path = "host/$hostId/member/$qq",
                        method = HttpMethod.Post,
                        onOk = {
                            okMessage = "已发送邀请"
                            reload()
                        },
                        onErr = { errorMessage = it.message ?: "邀请失败" }
                    )
                    showInviteDialog = false
                }) {
                    Text("邀请")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showCommandDialog) {
        val normalizedCommand = hostCommand.trim().removePrefix("/")
        AlertDialog(
            onDismissRequest = {
                if (!hostCommandSending) {
                    showCommandDialog = false
                }
            },
            title = { Text("发送服务器命令") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("命令会以服务器控制台身份执行，不需要输入开头的/")
                    OutlinedTextField(
                        value = hostCommand,
                        onValueChange = {
                            hostCommand = it
                            hostCommandResult = null
                        },
                        enabled = !hostCommandSending,
                        singleLine = true,
                        label = { Text("命令") },
                        placeholder = { Text("say hello") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    hostCommandResult?.let { result ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("命令返回：")
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1B1D1F), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = result,
                                    color = Color(0xFFE0E0E0),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = normalizedCommand.isNotBlank() && !hostCommandSending,
                    onClick = {
                        hostCommandSending = true
                        hostCommandResult = null
                        scope.rdiRequest<String>(
                            path = "host/$hostId/command",
                            method = HttpMethod.Post,
                            params = mapOf("command" to normalizedCommand),
                            onOk = { response ->
                                hostCommandResult = response.data?.ifBlank { "OK" } ?: "OK"
                                okMessage = "命令已执行"
                            },
                            onErr = { errorMessage = it.message ?: "发送命令失败" },
                            onDone = { hostCommandSending = false }
                        )
                    }
                ) {
                    Text(if (hostCommandSending) "发送中..." else "发送")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !hostCommandSending,
                    onClick = { showCommandDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showAddExtraModDialog) {
        Dialog(
            onDismissRequest = {
                if (!addExtraModLoading) {
                    resetAddExtraModDialog()
                    showAddExtraModDialog = false
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .fillMaxHeight(0.86f),
                shape = RoundedCornerShape(20.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("添加附加Mod", style = MaterialTheme.typography.h6)
                    Text("请填写远程Mod文件信息。添加后会作为房间附加Mod下载并同步给玩家。")
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("平台", color = MaterialColor.GRAY_700.color)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    M3RadioButton(
                                        selected = extraModPlatform == "github",
                                        enabled = !addExtraModLoading,
                                        onClick = { switchExtraModPlatform("github") }
                                    )
                                    Text("GitHub")
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    M3RadioButton(
                                        selected = extraModPlatform == "mr",
                                        enabled = !addExtraModLoading,
                                        onClick = { switchExtraModPlatform("mr") }
                                    )
                                    Text("Modrinth")
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    M3RadioButton(
                                        selected = extraModPlatform == "cf",
                                        enabled = !addExtraModLoading,
                                        onClick = { switchExtraModPlatform("cf") }
                                    )
                                    Text("CurseForge")
                                }
                            }
                        }
                        if (extraModPlatform == "github") {
                            if (extraModGithubReleases.isEmpty()) {
                                item {
                                    ManualExtraModTextField(
                                        state = extraModGithubRepoUrlState,
                                        enabled = !addExtraModLoading,
                                        label = "GitHub仓库链接"
                                    )
                                }
                            } else {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = extraModGithubRepo?.projectId.orEmpty(),
                                                fontSize = 18.sp
                                            )
                                            Text(
                                                text = "选择要添加的Mod。务必选择正确的文件，否则房间将无法启动。详见群文档附加Mod章节",
                                                color = MaterialColor.GRAY_700.color,
                                                fontSize = 14.sp
                                            )
                                        }
                                        TextButton(
                                            enabled = !addExtraModLoading,
                                            onClick = {
                                                extraModGithubRepo = null
                                                extraModGithubReleases = emptyList()
                                                selectedGithubAsset = null
                                            }
                                        ) {
                                            Text("重新输入")
                                        }
                                    }
                                }
                                extraModGithubReleases.forEach { release ->
                                    item {
                                        Text(
                                            text = release.name.ifBlank { release.tagName },
                                            fontSize = 17.sp,
                                            color = MaterialColor.GRAY_900.color
                                        )
                                    }
                                    items(release.assets, key = { it.key }) { asset ->
                                        GithubReleaseAssetRow(
                                            asset = asset,
                                            selected = selectedGithubAsset?.key == asset.key,
                                            enabled = !addExtraModLoading,
                                            onClick = {
                                                selectedGithubAsset = asset
                                                addExtraModDialogError = null
                                            }
                                        )
                                    }
                                }
                                item {
                                    Text(
                                        text = selectedGithubAsset?.let { "已选择 ${it.name}" } ?: "请选择1个jar文件",
                                        color = if (selectedGithubAsset == null) MaterialColor.GRAY_700.color else MaterialColor.PURPLE_700.color
                                    )
                                }
                            }
                        } else {
                            item {
                                Row(){
                                    Text("本界面仅供备用，正常请使用“资源-模组”界面添加附加mod。详情阅读群文档...")
                                }
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ManualExtraModTextField(
                                        state = extraModProjectIdState,
                                        enabled = !addExtraModLoading,
                                        label = "projectID",
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    )
                                    ManualExtraModTextField(
                                        state = extraModSlugState,
                                        enabled = !addExtraModLoading,
                                        label = "slug",
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    )
                                    ManualExtraModTextField(
                                        state = extraModFileIdState,
                                        enabled = !addExtraModLoading,
                                        label = "fileID",
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    )
                                }
                            }
                        }
                        if (extraModPlatform != "github") {
                            item {
                                ManualExtraModTextField(
                                    state = extraModHashState,
                                    enabled = !addExtraModLoading,
                                    label = "SHA1 hash"
                                )
                            }
                            item {
                                ManualExtraModTextField(
                                    state = extraModDownloadUrlsState,
                                    enabled = !addExtraModLoading,
                                    label = "download URL",
                                    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 4),
                                    modifier = Modifier.fillMaxWidth().height(104.dp)
                                )
                            }
                        }
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("使用侧", color = MaterialColor.GRAY_700.color)
                                listOf(Mod.Side.BOTH, Mod.Side.CLIENT, Mod.Side.SERVER).forEach { side ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        M3RadioButton(
                                            selected = extraModSide == side,
                                            enabled = !addExtraModLoading,
                                            onClick = { extraModSide = side }
                                        )
                                        Text(side.text)
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (addExtraModLoading && addExtraModLoadingText.isNotBlank()) {
                            Text(
                                text = addExtraModLoadingText,
                                color = MaterialColor.GRAY_700.color,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Space8w()
                        }
                        addExtraModDialogError?.let {
                            Text(
                                text = it,
                                color = MaterialColor.RED_900.color,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Space8w()
                        }
                        TextButton(
                            enabled = !addExtraModLoading,
                            onClick = {
                                resetAddExtraModDialog()
                                showAddExtraModDialog = false
                            }
                        ) {
                            Text("取消")
                        }
                        Space8w()
                        TextButton(
                            enabled = !addExtraModLoading,
                            onClick = {
                                if (extraModPlatform == "github") {
                                    submitGithubExtraMod()
                                } else {
                                    val mod = buildManualExtraMod(
                                        platform = extraModPlatform,
                                        projectId = extraModProjectIdState.text.toString(),
                                        slug = extraModSlugState.text.toString(),
                                        fileId = extraModFileIdState.text.toString(),
                                        hash = extraModHashState.text.toString(),
                                        side = extraModSide,
                                        downloadUrlsText = extraModDownloadUrlsState.text.toString()
                                    ).getOrElse {
                                        addExtraModDialogError = it.message ?: "附加Mod信息无效"
                                        return@TextButton
                                    }
                                    submitExtraMod(mod)
                                }
                            }
                        ) {
                            Text(
                                when {
                                    addExtraModLoading -> "处理中..."
                                    extraModPlatform == "github" && extraModGithubReleases.isEmpty() -> "下一步"
                                    else -> "添加"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    HostConfigEditorOverlay(
        visible = configEditorOpen,
        selectedPath = selectedConfigPath,
        editorText = configEditorText,
        loadingContent = configContentLoading,
        saving = configSaving,
        dirty = configDirty,
        validationErrorMessage = configSyntaxErrorMessage,
        onClose = { configEditorOpen = false },
        onReload = {
            val path = selectedConfigPath
            if (path == null) {
                errorMessage = "请先选择配置文件"
            } else {
                loadConfigFile(path)
            }
        },
        onSave = {
            if (selectedConfigPath == null) {
                errorMessage = "请先选择配置文件"
            } else if (configSyntaxErrorMessage != null) {
                errorMessage = configSyntaxErrorMessage
            } else {
                saveConfigFile()
            }
        },
        onEditorChange = {
            configEditorText = it
            configSyntaxErrorMessage = validateCodeContent(
                text = it,
                language = CodeLanguage.fromPath(selectedConfigPath)
            )?.takeIf { validation -> !validation.isValid }?.message
        },
        onValidationChange = { validation ->
            configSyntaxErrorMessage = validation?.takeIf { !it.isValid }?.message
        }
    )

    installConfirmTask?.let { task ->
        ConfirmDialog(
            title = "未下载整合包",
            message = "未下载此房间的整合包，是否立即下载？",
            onConfirm = {
                installConfirmTask = null
                val runId = ClientTaskManager.submit(task)
                if (onOpenTaskList != null) {
                    onOpenTaskList(runId)
                } else {
                    okMessage = "已加入任务列表"
                }
            },
            onDismiss = { installConfirmTask = null }
        )
    }

    // options dialog removed

    removeExtraModConfirm?.let { mod ->
        val selectedProjectIds = extraMods
            .filter { extraModKey(it) in selectedExtraModKeys }
            .map { it.projectId }
            .distinct()
        val targetProjectIds = selectedProjectIds.ifEmpty { listOf(mod.projectId) }
        val removeCount = targetProjectIds.size
        ConfirmDialog(
            title = "删除附加Mod",
            message = if (removeCount > 1) {
                "确定删除已选择的${removeCount}个附加Mod吗？"
            } else {
                "确定删除附加Mod《${mod.displaySlugOrProject}》吗？"
            },
            onConfirm = {
                scope.rdiRequest<List<Mod>>(
                    path = "host/$hostId/mods/extra",
                    method = HttpMethod.Delete,
                    body = serdesJson.encodeToString(targetProjectIds),
                    onOk = { response ->
                        applyExtraMods(response.data ?: emptyList())
                        selectedExtraModKeys = emptySet()
                        okMessage = "已删除附加Mod"
                    },
                    onErr = { errorMessage = it.message ?: "删除附加Mod失败" }
                )
                removeExtraModConfirm = null
            },
            onDismiss = { removeExtraModConfirm = null }
        )
    }

    roleChangeConfirm?.let { change ->
        val msg = if (change.newRole == Role.ADMIN) "确定设置该成员为管理员？" else "确定取消管理员身份？"
        ConfirmDialog(
            title = "确认操作",
            message = msg,
            onConfirm = {
                scope.rdiRequestU(
                    path = "host/$hostId/member/${change.memberId}/role/${change.newRole.name}",
                    method = HttpMethod.Put,
                    onOk = {
                        okMessage = "已更新"
                        reload()
                    },
                    onErr = { errorMessage = it.message ?: "操作失败" }
                )
                roleChangeConfirm = null
            },
            onDismiss = { roleChangeConfirm = null }
        )
    }

    transferConfirm?.let { memberId ->
        ConfirmDialog(
            title = "确认转让",
            message = "确定将房间所有权转让给该成员吗？",
            onConfirm = {
                scope.rdiRequestU(
                    path = "host/$hostId/transfer/$memberId",
                    method = HttpMethod.Post,
                    onOk = {
                        okMessage = "已转让"
                        reload()
                    },
                    onErr = { errorMessage = it.message ?: "转让失败" }
                )
                transferConfirm = null
            },
            onDismiss = { transferConfirm = null }
        )
    }

    kickConfirm?.let { memberId ->
        ConfirmDialog(
            title = "确认踢出",
            message = "要踢出该成员吗？",
            onConfirm = {
                scope.rdiRequestU(
                    path = "host/$hostId/member/$memberId",
                    method = HttpMethod.Delete,
                    onOk = {
                        okMessage = "已踢出"
                        reload()
                    },
                    onErr = { errorMessage = it.message ?: "踢出失败" }
                )
                kickConfirm = null
            },
            onDismiss = { kickConfirm = null }
        )
    }

    if (restartConfirm) {
        ConfirmDialog(
            title = "确认重启",
            message = "确定重启该房间吗？",
            onConfirm = {
                scope.rdiRequestU(
                    path = "host/$hostId/restart",
                    method = HttpMethod.Post,
                    onOk = { okMessage = "重启指令已发送" },
                    onErr = { errorMessage = it.message ?: "重启失败" }
                )
                restartConfirm = false
            },
            onDismiss = { restartConfirm = false }
        )
    }

    if (stopConfirm) {
        ConfirmDialog(
            title = "确认停止",
            message = "确定停止该房间吗？",
            onConfirm = {
                scope.rdiRequestU(
                    path = "host/$hostId/stop",
                    method = HttpMethod.Post,
                    onOk = { okMessage = "停止指令已发送" },
                    onErr = { errorMessage = it.message ?: "停止失败" }
                )
                stopConfirm = false
            },
            onDismiss = { stopConfirm = false }
        )
    }

    if (forceStopConfirm) {
        ConfirmDialog(
            title = "确认强制停止",
            message = "确定强制停止该房间吗？",
            onConfirm = {
                scope.rdiRequestU(
                    path = "host/$hostId/force-stop",
                    method = HttpMethod.Post,
                    onOk = { okMessage = "强制停止指令已发送" },
                    onErr = { errorMessage = it.message ?: "强制停止失败" }
                )
                forceStopConfirm = false
            },
            onDismiss = { forceStopConfirm = false }
        )
    }

    if (quitConfirm) {
        ConfirmDialog(
            title = "退出房间",
            message = "确定退出该房间吗？",
            onConfirm = {
                scope.rdiRequestU(
                    path = "host/$hostId/quit",
                    method = HttpMethod.Put,
                    onOk = {
                        okMessage = "已退出房间"
                        reload()
                    },
                    onErr = { errorMessage = it.message ?: "退出失败" }
                )
                quitConfirm = false
            },
            onDismiss = { quitConfirm = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HostExtraModsPane(
    extraUiMods: List<UiMod>,
    selectedExtraMods: List<Mod>,
    selectedExtraModKeys: Set<String>,
    selectAllExtraMods: Boolean,
    extraModsLoading: Boolean,
    canManageExtraMods: Boolean,
    addExtraModLoading: Boolean,
    addExtraModLoadingText: String,
    onToggleSelectAll: (Boolean) -> Unit,
    onToggleSelected: (Mod, Boolean) -> Unit,
    onDownloadSelected: () -> Unit,
    onRemoveSelected: () -> Unit,
    onAddExtraMod: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compactToolbar = maxWidth < 560.dp
        val actionRow: @Composable () -> Unit = {
            FlowRowV(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "已选择${selectedExtraMods.size}个",
                    color = MaterialColor.GRAY_700.color
                )
                Checkbox(selectAllExtraMods, onCheckedChange = { onToggleSelectAll(it) })
                Text("全选")
                CircleIconButton(
                    icon = "\uF019",
                    tooltip = "下载",
                    enabled = selectedExtraMods.isNotEmpty(),
                    bgColor = MaterialColor.GREEN_900.color,
                ) {
                    onDownloadSelected()
                }
                if (canManageExtraMods) {
                    CircleIconButton(
                        icon = "\uEA81",
                        tooltip = "删除",
                        enabled = selectedExtraMods.isNotEmpty(),
                        bgColor = MaterialColor.RED_900.color,
                    ) {
                        onRemoveSelected()
                    }
                    CircleIconButton(
                        icon = "\uF067",
                        tooltip = if (addExtraModLoading) {
                            addExtraModLoadingText.ifBlank { "匹配中..." }
                        } else {
                            "附加Mod"
                        },
                        enabled = !addExtraModLoading,
                        bgColor = MaterialColor.PURPLE_700.color,
                    ) {
                        onAddExtraMod()
                    }
                }
            }
        }

        if (compactToolbar) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "可以在整合包之外再添加更多Mod。",
                    color = MaterialColor.GRAY_700.color,
                    modifier = Modifier.fillMaxWidth()
                )
                actionRow()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "可以在整合包之外再添加更多Mod。",
                    color = MaterialColor.GRAY_700.color,
                    modifier = Modifier.weight(1f)
                )
                actionRow()
            }
        }
    }

    if (extraModsLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator()
                Text("正在载入附加Mod信息...", color = MaterialColor.GRAY_700.color)
            }
        }
    }

    if (!extraModsLoading && extraUiMods.isEmpty()) {
        Text("当前没有附加Mod", color = MaterialColor.GRAY_700.color)
    } else if (!extraModsLoading) {
        ModGrid(
            mods = extraUiMods,
            modifier = Modifier.fillMaxSize(),
            selectedKeys = selectedExtraModKeys,
            emptyText = "当前没有附加Mod",
            onModClick = { uiMod ->
                val selected = uiMod.key in selectedExtraModKeys
                onToggleSelected(uiMod.mod, !selected)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HostModListPane(
    baseVersionMods: List<Mod>,
    selectedModListMods: List<Mod>,
    selectedModListKeys: Set<String>,
    selectAllModListMods: Boolean,
    onToggleSelectAll: (Boolean) -> Unit,
    onToggleSelected: (Mod, Boolean) -> Unit,
    onDisableSelected: () -> Unit
) {
    val uiMods by produceState(initialValue = baseVersionMods.toUiMods(), baseVersionMods) {
        value = if (baseVersionMods.isEmpty()) {
            emptyList()
        } else {
            runCatching {
                withContext(Dispatchers.IO) {
                    baseVersionMods.hydrateToUiMods()
                }
            }.getOrDefault(baseVersionMods.toUiMods())
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compactToolbar = maxWidth < 560.dp
        val actionRow: @Composable () -> Unit = {
            FlowRowV(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "已选择${selectedModListMods.size}个",
                    color = MaterialColor.GRAY_700.color
                )
                Checkbox(selectAllModListMods, onCheckedChange = { onToggleSelectAll(it) })
                Text("全选")
                CircleIconButton(
                    icon = "\uF2ED",
                    tooltip = "停用选中的mod",
                    enabled = selectedModListMods.isNotEmpty(),
                    bgColor = MaterialColor.RED_900.color,
                ) {
                    onDisableSelected()
                }
            }
        }

        if (compactToolbar) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "可以停用整合包中的mod",
                    color = MaterialColor.GRAY_700.color,
                    modifier = Modifier.fillMaxWidth()
                )
                actionRow()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "可以停用整合包中的mod",
                    color = MaterialColor.GRAY_700.color,
                    modifier = Modifier.weight(1f)
                )
                actionRow()
            }
        }
    }

    if (baseVersionMods.isEmpty()) {
        Text("当前整合包版本没有可显示的Mod", color = MaterialColor.GRAY_700.color)
    } else {
        ModGrid(
            mods = uiMods,
            modifier = Modifier.fillMaxSize(),
            selectedKeys = selectedModListKeys,
            emptyText = "当前整合包版本没有可显示的Mod",
            onModClick = { uiMod ->
                val selected = uiMod.key in selectedModListKeys
                onToggleSelected(uiMod.mod, !selected)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HostDisabledModsPane(
    disabledMods: List<Mod>,
    selectedDisabledMods: List<Mod>,
    selectedDisabledModKeys: Set<String>,
    selectAllDisabledMods: Boolean,
    onToggleSelectAll: (Boolean) -> Unit,
    onToggleSelected: (Mod, Boolean) -> Unit,
    onEnableSelected: () -> Unit
) {
    val uiMods by produceState(initialValue = disabledMods.toUiMods(), disabledMods) {
        value = if (disabledMods.isEmpty()) {
            emptyList()
        } else {
            runCatching {
                withContext(Dispatchers.IO) {
                    disabledMods.hydrateToUiMods()
                }
            }.getOrDefault(disabledMods.toUiMods())
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compactToolbar = maxWidth < 560.dp
        val actionRow: @Composable () -> Unit = {
            FlowRowV(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "已选择${selectedDisabledMods.size}个",
                    color = MaterialColor.GRAY_700.color
                )
                Checkbox(selectAllDisabledMods, onCheckedChange = { onToggleSelectAll(it) })
                Text("全选")
                CircleIconButton(
                    icon = "\uF0E2",
                    tooltip = "启用选中的mod",
                    enabled = selectedDisabledMods.isNotEmpty(),
                    bgColor = MaterialColor.GREEN_900.color,
                ) {
                    onEnableSelected()
                }
            }
        }

        if (compactToolbar) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "可以重新启用已停用的mod",
                    color = MaterialColor.GRAY_700.color,
                    modifier = Modifier.fillMaxWidth()
                )
                actionRow()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "可以重新启用已停用的mod",
                    color = MaterialColor.GRAY_700.color,
                    modifier = Modifier.weight(1f)
                )
                actionRow()
            }
        }
    }

    if (disabledMods.isEmpty()) {
        Text("当前没有停用mod", color = MaterialColor.GRAY_700.color)
    } else {
        ModGrid(
            mods = uiMods,
            modifier = Modifier.fillMaxSize(),
            selectedKeys = selectedDisabledModKeys,
            emptyText = "当前没有停用mod",
            onModClick = { uiMod ->
                val selected = uiMod.key in selectedDisabledModKeys
                onToggleSelected(uiMod.mod, !selected)
            }
        )
    }
}

private data class RoleChange(
    val memberId: ObjectId,
    val newRole: Role
)

@Composable
private fun GithubReleaseAssetRow(
    asset: GithubReleaseAsset,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialColor.PURPLE_50.color else MaterialColor.GRAY_50.color
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = asset.name,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${asset.releaseTag} · ${asset.sizeText}",
                color = MaterialColor.GRAY_700.color,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Text("已选", color = MaterialColor.PURPLE_700.color)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualExtraModTextField(
    state: TextFieldState,
    enabled: Boolean,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth().height(48.dp),
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine
) {
    M3OutlinedTextField(
        state = state,
        enabled = enabled,
        lineLimits = lineLimits,
        label = { Text(label, color = Color.Gray) },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = modifier
    )
}

private fun extraModKey(mod: Mod): String = "${mod.platform}:${mod.projectId}:${mod.fileId}"

private fun buildManualExtraMod(
    platform: String,
    projectId: String,
    slug: String,
    fileId: String,
    hash: String,
    side: Mod.Side,
    downloadUrlsText: String
): Result<Mod> = runCatching {
    val normalizedPlatform = platform.trim().lowercase()
    if (normalizedPlatform !in listOf("mr", "cf")) {
        error("平台只能是Modrinth或CurseForge")
    }
    val trimmedHash = hash.trim()
    if (trimmedHash.isBlank()) error("SHA1不能为空")
    val downloadUrls = downloadUrlsText
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .toList()
    if (downloadUrls.isEmpty()) error("至少填写1个下载链接")
    val invalidUrl = downloadUrls.firstOrNull { !it.isValidExtraModDownloadUrl() }
    if (invalidUrl != null) error("下载链接无效: $invalidUrl")

    val resolvedProjectId = projectId.trim()
    val resolvedSlug = slug.trim()
    val resolvedFileId = fileId.trim()
    if (resolvedProjectId.isBlank()) error("projectId不能为空")
    if (resolvedSlug.isBlank()) error("slug不能为空")
    if (resolvedFileId.isBlank()) error("fileId不能为空")

    Mod(
        platform = normalizedPlatform,
        projectId = resolvedProjectId,
        slug = resolvedSlug,
        fileId = resolvedFileId,
        hash = trimmedHash,
        side = side,
        downloadUrls = downloadUrls
    )
}

private fun String.isValidExtraModDownloadUrl(): Boolean = runCatching {
    val uri = URI(this)
    val scheme = uri.scheme?.lowercase()
    (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

private data class ExtraModAddFilterResult(
    val acceptedMods: List<Mod>,
    val rejectedMessages: List<String>
)

private fun filterExtraModsForAdding(candidateMods: List<Mod>, existingMods: List<Mod>): ExtraModAddFilterResult {
    val existingKeys = existingMods.map(::extraModSlugIdentity)
        .filter { it.isNotBlank() }
        .toSet()
    val acceptedMods = mutableListOf<Mod>()
    val pendingKeys = mutableSetOf<String>()
    val rejectedMessages = mutableListOf<String>()

    candidateMods.forEach { mod ->
        val key = extraModSlugIdentity(mod)
        if (key.isBlank()) {
            acceptedMods += mod
            return@forEach
        }
        when {
            key in existingKeys -> rejectedMessages += "${mod.displaySlugOrProject}：主机或整合包中已存在同名Mod"
            !pendingKeys.add(key) -> rejectedMessages += "${mod.displaySlugOrProject}：本次选择中已有同名Mod"
            else -> acceptedMods += mod
        }
    }

    return ExtraModAddFilterResult(
        acceptedMods = acceptedMods,
        rejectedMessages = rejectedMessages.distinct()
    )
}

private fun extraModSlugIdentity(mod: Mod): String = mod.normalizedSlug.ifBlank {
    mod.normalizedProjectId.lowercase()
}
