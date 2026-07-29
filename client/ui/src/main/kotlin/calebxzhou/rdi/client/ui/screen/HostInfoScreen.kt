package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.*
import androidx.compose.material3.RadioButton as M3RadioButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import calebxzau.rdi.client.ui.AlertErr
import calebxzau.rdi.client.ui.BottomSnakebarM3
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ConfirmDialog
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.KeepAliveAnimatedTabHost
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.RThinTextField
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.TinyClickCopyText
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.TitleTabBar
import calebxzau.rdi.client.ui.TitleTabItem
import calebxzhou.rdi.client.auth.LocalCredentials
import calebxzhou.rdi.client.auth.updateLastPlayHost
import calebxzhou.rdi.client.modcatalog.ModCatalog
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.net.sse
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.service.GithubExtraModService
import calebxzhou.rdi.client.service.GithubRelease
import calebxzhou.rdi.client.service.GithubReleaseAsset
import calebxzhou.rdi.client.service.GithubRepoRef
import calebxzhou.rdi.client.service.StartPlayResult
import calebxzhou.rdi.client.service.startPlay
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.*
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.model.isAdmin
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.service.TaczGunpackValidator
import calebxzhou.rdi.model.Role
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.forms.*
import io.ktor.client.request.setBody
import io.ktor.http.*
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import org.bson.types.ObjectId
import java.io.File
import java.net.URI

internal const val TACZ_ROOT_DIR = "tacz"
private const val TACZ_MOD_SLUG = "timeless-and-classics-zero"
internal const val TACZ_FILE_MAX_BYTES: Long = 100L * 1024 * 1024
internal const val TACZ_MAX_ZIP_FILES = 10

/**
 * calebxzhou @ 2026-01-15 19:38
 */

@Composable
fun HostInfoScreen(
    modCatalog: ModCatalog,
    hostId: ObjectId,
    onBack: () -> Unit = {},
    onOpenModpackInfo: (String) -> Unit,
    onOpenMcPlay: (McPlayArgs) -> Unit,
    onOpenMcVersions: (McVersion?) -> Unit,
    onOpenResourceMods: (McVersion, ModLoader) -> Unit,
    onOpenHostEdit: (Host.DetailVo) -> Unit,
    onOpenTaskList: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var okMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var hostDetail by remember { mutableStateOf<Host.DetailVo?>(null) }
    var modpackDetail by remember { mutableStateOf<Modpack.DetailVo?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteWorldWhenDeleteHost by remember { mutableStateOf(false) }
    var showUpdateConfirm by remember { mutableStateOf(false) }
    var roleChangeConfirm by remember { mutableStateOf<RoleChange?>(null) }
    var transferConfirm by remember { mutableStateOf<ObjectId?>(null) }
    var kickConfirm by remember { mutableStateOf<ObjectId?>(null) }
    var quitConfirm by remember { mutableStateOf(false) }
    var startPlayLoading by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable(hostId) { mutableStateOf(HostInfoTab.Info) }
    val consoleState = remember(hostId) { ConsoleState() }
    var logStreamSseJob by remember { mutableStateOf<Job?>(null) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteQq by remember { mutableStateOf("") }
    var installConfirmTask by remember { mutableStateOf<StartPlayResult.NeedInstall?>(null) }
    var showAddExtraModAdvancedDialog by remember { mutableStateOf(false) }
    var addExtraModLoading by remember { mutableStateOf(false) }
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
    var disabledMods by remember { mutableStateOf<List<Mod>>(emptyList()) }
    var removeExtraModsConfirm by remember { mutableStateOf<List<Mod>?>(null) }
    val mainTabs = remember {
        HostInfoTab.entries.map { TitleTabItem(it, it.icon, it.label) }
    }

    fun resetAddExtraModAdvancedDialog() {
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

    fun applyExtraMods(updatedMods: List<Mod>) {
        hostDetail = hostDetail?.copy(extraMods = updatedMods)
    }

    fun applyDisabledMods(updatedMods: List<Mod>) {
        disabledMods = updatedMods
        hostDetail = hostDetail?.copy(disabledMods = updatedMods)
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
        disabledMods = emptyList()
        reload()
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
    val baseVersionMods = modpackDetail?.versions
        ?.firstOrNull { it.name == host?.packVer }
        ?.mods
        ?.filterNot { versionMod -> disabledMods.any { sameMod(it, versionMod) } }
        .orEmpty()
    val hasTacz = (extraMods + baseVersionMods)
        .filterNot { mod -> disabledMods.any { sameMod(it, mod) } }
        .any { it.normalizedSlug == TACZ_MOD_SLUG }
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
                showAddExtraModAdvancedDialog = false
                resetAddExtraModAdvancedDialog()
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

    DisposableEffect(selectedTab, hostId) {
        if (selectedTab != HostInfoTab.Info) {
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
                    consoleState.append(payload)
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
        if (startPlayLoading) return
        startPlayLoading = true
        scope.launch {
            val args = try {
                host.startPlay()
            } catch (e: Exception) {
                errorMessage = e.message ?: "无法开始游玩"
                startPlayLoading = false
                return@launch
            }
            when (args) {
                is StartPlayResult.Ready -> {
                    LocalCredentials.read().updateLastPlayHost(
                        id = host._id.toHexString(),
                        name = host.name
                    )
                    onOpenMcPlay(args.args)
                }
                is StartPlayResult.NeedMod -> {
                    errorMessage = "房间缺少必要Mod：${args.modSlugs.joinToString("、")}。请先前往模组界面添加。"
                    startPlayLoading = false
                }
                is StartPlayResult.NeedInstall -> {
                    installConfirmTask = args
                    startPlayLoading = false
                }
                is StartPlayResult.Installing -> {
                    errorMessage = "整合包正在下载，请等待下载完成后再启动"
                    startPlayLoading = false
                    onOpenTaskList?.invoke(args.runId)
                }

                is StartPlayResult.NeedMc -> {
                    errorMessage = "请更新MC${args.ver.mcVer}版本资源"
                    startPlayLoading = false
                    onOpenMcVersions?.invoke(args.ver)
                }
            }
        }
    }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            TitleRow(title = host?.name ?: "房间详情", onBack = onBack) {
                host?.let { host ->
                    Column {
                        TinyClickCopyText("hid", host._id.toHexString())
                        TinyClickCopyText("mid", host.modpack.id.toHexString())
                        TinyClickCopyText("wid", host.worldId?.toHexString())
                    }
                    TitleTabBar(
                        items = mainTabs,
                        selected = selectedTab,
                        onSelect = { selectedTab = it }
                    )
                    CircleIconButton(
                        icon = "\uF04B",
                        tooltip = "开玩",
                        enabled = !startPlayLoading,
                    ) {
                        startPlay(host)
                    }
                    if (startPlayLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }

                    if (meAdmin) {
                        CircleIconButton(
                            icon = "\uF013",
                            tooltip = "设置",
                            showText = false,
                            bgColor = MaterialTheme.colorScheme.secondary
                        ) {
                            onOpenHostEdit(host)
                        }
                        if (modpackDetail != null) {

                            CircleIconButton(
                                icon = "\uDB80\uDFD5",
                                tooltip = "更新",
                                showText = false,
                            ) { showUpdateConfirm = true }
                        }
                    }
                    if (meOwner) {

                        CircleIconButton(
                            icon = "\uEA81",
                            tooltip = "删除",
                            showText = false,
                            bgColor = MaterialTheme.colorScheme.error
                        ) {
                            deleteWorldWhenDeleteHost = false
                            showDeleteConfirm = true
                        }
                    }
                }

            }

            ContentBody {
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
                        key(hostId) {
                            KeepAliveAnimatedTabHost(
                                selected = selectedTab,
                                order = HostInfoTab.entries::indexOf,
                                modifier = Modifier.fillMaxSize()
                            ) { activeTab ->
                                when (activeTab) {
                                HostInfoTab.Info -> HostOverviewPane(
                                    host = host,
                                    hostId = hostId,
                                    consoleState = consoleState,
                                    meAdmin = meAdmin,
                                    meOwner = meOwner,
                                    onOpenModpackInfo = onOpenModpackInfo,
                                    onInvite = { showInviteDialog = true },
                                    onTransfer = { transferConfirm = it },
                                    onChangeRole = { memberId, role ->
                                        roleChangeConfirm = RoleChange(memberId, role)
                                    },
                                    onKick = { kickConfirm = it },
                                    onQuit = { quitConfirm = true },
                                    onOk = { okMessage = it },
                                    onError = { errorMessage = it }
                                )

                                HostInfoTab.PrivateThings -> HostPrivateThingsPane(
                                    modCatalog = modCatalog,
                                    hostId = hostId,
                                    extraMods = extraMods,
                                    baseVersionMods = baseVersionMods,
                                    disabledMods = disabledMods,
                                    hasTacz = hasTacz,
                                    canManage = canManageExtraMods,
                                    addExtraModLoading = addExtraModLoading,
                                    addExtraModLoadingText = addExtraModLoadingText,
                                    onDisabledModsChanged = ::applyDisabledMods,
                                    onRemoveExtraMods = { removeExtraModsConfirm = it },
                                    onOpenResourceMods = {
                                        onOpenResourceMods(host.modpack.mcVer, host.modpack.modloader)
                                    },
                                    onAddExtraModAdvanced = {
                                        resetAddExtraModAdvancedDialog()
                                        showAddExtraModAdvancedDialog = true
                                    },
                                    onOk = { okMessage = it },
                                    onError = { errorMessage = it },
                                    onOpenTaskList = onOpenTaskList
                                )

                                HostInfoTab.Config -> HostConfigPane(
                                    hostId = hostId,
                                    canManage = canManageConfigFiles
                                )
                                }
                            }
                        }
                    }
                }

                }
            }
        }
        BottomSnakebarM3(snackbarHostState)
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
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
            message = "将更新房间当前的整合包《${modpackDetail!!.name}》到最新版本。所有修改过的配置都会丢失。",
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

    if (showAddExtraModAdvancedDialog) {
        Dialog(
            onDismissRequest = {
                if (!addExtraModLoading) {
                    resetAddExtraModAdvancedDialog()
                    showAddExtraModAdvancedDialog = false
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
                    Text("添加附加Mod（高级模式）", style = MaterialTheme.typography.titleSmall)
                    Text("仅供高级玩家使用。通常情况不建议使用此功能")
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
                                    RThinTextField(
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
                                    Text("【开发用界面】请使用“资源-模组”界面添加附加mod。")
                                }
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RThinTextField(
                                        state = extraModProjectIdState,
                                        enabled = !addExtraModLoading,
                                        label = "pj",
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    )
                                    RThinTextField(
                                        state = extraModSlugState,
                                        enabled = !addExtraModLoading,
                                        label = "slug",
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    )
                                    RThinTextField(
                                        state = extraModFileIdState,
                                        enabled = !addExtraModLoading,
                                        label = "file",
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    )
                                }
                            }
                        }
                        if (extraModPlatform != "github") {
                            item {
                                RThinTextField(
                                    state = extraModHashState,
                                    enabled = !addExtraModLoading,
                                    label = "hash"
                                )
                            }
                            item {
                                RThinTextField(
                                    state = extraModDownloadUrlsState,
                                    enabled = !addExtraModLoading,
                                    label = "dl url",
                                    lineLimits = TextFieldLineLimits.MultiLine(
                                        minHeightInLines = 2,
                                        maxHeightInLines = 4
                                    ),
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
                                resetAddExtraModAdvancedDialog()
                                showAddExtraModAdvancedDialog = false
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

    installConfirmTask?.let { install ->
        val currentHost = hostDetail
        ModpackDownloadMethodDialog(
            packName = currentHost?.modpack?.name ?: install.task.title,
            packVer = currentHost?.packVer.orEmpty(),
            onDismiss = { installConfirmTask = null },
            onDirectDownload = {
                installConfirmTask = null
                val runId = ClientTaskManager.submit(install.task, dedupeKey = install.dedupeKey)
                if (onOpenTaskList != null) {
                    onOpenTaskList(runId)
                } else {
                    okMessage = "已加入任务列表"
                }
            },
            onOpenTaskList = onOpenTaskList,
            onImportMessage = { okMessage = it },
            onImportError = { errorMessage = it }
        )
    }

    // options dialog removed

    removeExtraModsConfirm?.let { mods ->
        val targetProjectIds = mods.map { it.projectId }.distinct()
        val removeCount = targetProjectIds.size
        ConfirmDialog(
            title = "删除附加Mod",
            message = if (removeCount > 1) {
                "确定删除已选择的${removeCount}个附加Mod吗？"
            } else {
                "确定删除附加Mod《${mods.single().displaySlugOrProject}》吗？"
            },
            onConfirm = {
                scope.rdiRequest<List<Mod>>(
                    path = "host/$hostId/mods/extra",
                    method = HttpMethod.Delete,
                    body = serdesJson.encodeToString(targetProjectIds),
                    onOk = { response ->
                        applyExtraMods(response.data ?: emptyList())
                        okMessage = "已删除附加Mod"
                    },
                    onErr = { errorMessage = it.message ?: "删除附加Mod失败" }
                )
                removeExtraModsConfirm = null
            },
            onDismiss = { removeExtraModsConfirm = null }
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

internal fun Host.FileEntry.isTaczZipFileEntry(): Boolean =
    !directory && name.endsWith(".zip", ignoreCase = true)

private fun hostTaczChildPath(fileName: String): String {
    val normalizedName = fileName.replace('\\', '/').substringAfterLast('/').trim()
    require(normalizedName.isNotBlank()) { "文件名不能为空" }
    return "$TACZ_ROOT_DIR/$normalizedName"
}

internal fun createHostTaczUploadTask(
    hostId: ObjectId,
    files: List<File>,
    onUploaded: suspend () -> Unit
): Task2 {
    val uploadTasks = files.map { file ->
        Task2.Leaf("上传${file.name}") { ctx ->
            ctx.emit(Task2Progress("校验${file.name}", 0f))
            withContext(Dispatchers.IO) {
                TaczGunpackValidator.validate(file).getOrThrow()
            }
            ctx.ensureActive()
            ctx.emit(Task2Progress("上传${file.name}", 0.4f))
            uploadHostFile(
                hostId = hostId,
                targetPath = hostTaczChildPath(file.name),
                file = file
            )
            ctx.emit(Task2Progress("完成${file.name}", 1f))
        }
    }
    return Task2.Sequence(
        title = "上传TaCZ枪包${files.size}个",
        children = uploadTasks + Task2.Leaf("刷新TaCZ枪包列表") { ctx ->
            ctx.emit(Task2Progress("刷新TaCZ枪包列表", 0f))
            onUploaded()
            ctx.emit(Task2Progress("刷新完成", 1f))
        }
    )
}

private suspend fun uploadHostFile(
    hostId: ObjectId,
    targetPath: String,
    file: File
): Host.FileUploadVo {
    val multipartContent = MultiPartFormDataContent(
        formData {
            append("path", targetPath)
            append(
                key = "file",
                value = InputProvider { file.inputStream().asInput().buffered() },
                headers = Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                    append(HttpHeaders.ContentDisposition, "filename=\"${file.name.replace("\"", "")}\"")
                }
            )
        }
    )
    val response = server.makeRequest<Host.FileUploadVo>(
        path = "host/$hostId/files/file",
        method = HttpMethod.Post,
        params = mapOf("path" to targetPath)
    ) {
        setBody(multipartContent)
    }
    if (!response.ok) throw RequestError(response.msg)
    return response.data ?: throw RequestError("上传TaCZ枪包失败")
}
