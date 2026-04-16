package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material3.ExperimentalMaterial3Api
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
import calebxzhou.rdi.client.model.toUiMod
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.net.sse
import calebxzhou.rdi.client.service.ClientTaskManager
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

/**
 * calebxzhou @ 2026-01-15 19:38
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var pendingExtraUiMods by remember { mutableStateOf<List<UiMod>>(emptyList()) }
    var rejectedExtraModFiles by remember { mutableStateOf<List<String>>(emptyList()) }
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
        pendingExtraUiMods = emptyList()
        rejectedExtraModFiles = emptyList()
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

    fun updatePendingExtraModSide(uiMod: UiMod, side: Mod.Side) {
        pendingExtraUiMods = pendingExtraUiMods.map { existing ->
            if (existing.key == uiMod.key) {
                existing.withSide(side)
            } else {
                existing
            }
        }
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
                                                if (!isDesktop) {
                                                    errorMessage = "当前平台暂不支持选择本地Mod文件"
                                                } else {
                                                    val hostMcVersion = modpackDetail?.mcVer
                                                    if (hostMcVersion == null) {
                                                        errorMessage = "无法获取当前整合包的MC版本"
                                                    } else {
                                                        scope.launch {
                                                            val files = selectHostExtraModFiles() ?: return@launch
                                                            resetAddExtraModDialog()
                                                            addExtraModLoading = true
                                                            addExtraModLoadingText = "正在匹配Mod..."
                                                            val matchResult = try {
                                                                matchHostExtraModFiles(files, hostMcVersion) { progress ->
                                                                    addExtraModLoadingText = progress
                                                                }
                                                            } catch (e: Exception) {
                                                                errorMessage = e.message ?: "匹配Mod失败"
                                                                addExtraModLoading = false
                                                                addExtraModLoadingText = ""
                                                                return@launch
                                                            }
                                                            rejectedExtraModFiles = matchResult.rejectedFiles
                                                            val matchedUiMods = matchResult.matchedMods.map { mod ->
                                                                if (
                                                                    mod.platform.equals("cf", ignoreCase = true) &&
                                                                    mod.side == Mod.Side.UNKNOWN
                                                                ) {
                                                                    mod.toUiMod().withSide(Mod.Side.SERVER)
                                                                } else {
                                                                    mod.toUiMod()
                                                                }
                                                            }
                                                            val dedupeResult = filterExtraModsForAdding(
                                                                candidateMods = matchedUiMods.map(UiMod::toMod),
                                                                existingMods = extraMods + baseVersionMods
                                                            )
                                                            pendingExtraUiMods = dedupeResult.acceptedMods.toUiMods()
                                                            rejectedExtraModFiles = matchResult.rejectedFiles + dedupeResult.rejectedMessages
                                                            addExtraModLoading = false
                                                            addExtraModLoadingText = ""
                                                            if (pendingExtraUiMods.isEmpty()) {
                                                                if (rejectedExtraModFiles.isEmpty()) {
                                                                    errorMessage = "没有在网上搜索到这些Mod的信息"
                                                                    return@launch
                                                                }
                                                                showAddExtraModDialog = true
                                                                return@launch
                                                            }
                                                            showAddExtraModDialog = true
                                                        }
                                                    }
                                                } 
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
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(20.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (pendingExtraUiMods.isNotEmpty()) {
                            "确认添加${pendingExtraUiMods.size}个附加Mod"
                        } else {
                            "附加Mod添加失败"
                        },
                        style = MaterialTheme.typography.h6
                    )
                    Text(
                        if (pendingExtraUiMods.isNotEmpty()) {
                            "※在整合包以外添加Mod可能会导致房间无法运行，请自行保证Mod兼容性"
                        } else {
                            "选中的Mod都无法添加，请查看下方错误信息。"
                        }
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ModGrid(
                            mods = pendingExtraUiMods,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            emptyText = "当前没有待添加的附加Mod",
                            onSideChange = if (addExtraModLoading) {
                                null
                            } else {
                                { uiMod, side -> updatePendingExtraModSide(uiMod, side) }
                            }
                        )
                        if (rejectedExtraModFiles.isNotEmpty()) {
                            val rejectedPreview = rejectedExtraModFiles.joinToString("\n") { "• $it" }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(255, 244, 244), RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "以下mod无法添加",
                                    color = MaterialColor.RED_900.color,
                                    fontSize = 14.sp
                                )
                                Text(
                                    rejectedPreview,
                                    color = MaterialColor.RED_900.color,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        addExtraModDialogError?.let {
                            Text(
                                text = it,
                                color = MaterialColor.RED_900.color,
                                modifier = Modifier.weight(1f)
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
                            enabled = !addExtraModLoading && pendingExtraUiMods.isNotEmpty(),
                            onClick = {
                                val dedupeResult = filterExtraModsForAdding(
                                    candidateMods = pendingExtraUiMods.map(UiMod::toMod),
                                    existingMods = extraMods + baseVersionMods
                                )
                                if (dedupeResult.acceptedMods.size != pendingExtraUiMods.size) {
                                    pendingExtraUiMods = dedupeResult.acceptedMods.toUiMods()
                                    rejectedExtraModFiles = rejectedExtraModFiles + dedupeResult.rejectedMessages
                                    addExtraModDialogError = "已移除同slug的重复Mod，请确认后再添加"
                                    return@TextButton
                                }
                                addExtraModLoading = true
                                addExtraModDialogError = null
                                scope.rdiRequestU(
                                    path = "host/$hostId/mods/extra",
                                    method = HttpMethod.Post,
                                    body = serdesJson.encodeToString(pendingExtraUiMods.map(UiMod::toMod)),
                                    onOk = {
                                        okMessage = "已提交附加Mod添加任务，请在邮件中查看进度"
                                        showAddExtraModDialog = false
                                        resetAddExtraModDialog()
                                    },
                                    onErr = {
                                        addExtraModDialogError = it.message ?: "添加附加Mod失败"
                                    },
                                    onDone = { addExtraModLoading = false }
                                )
                            }
                        ) {
                            Text(if (addExtraModLoading) "添加中..." else "添加")
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

private fun extraModKey(mod: Mod): String = "${mod.platform}:${mod.projectId}:${mod.fileId}"

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
