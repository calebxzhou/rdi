package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzhou.mykotutils.std.secondsToHumanDateTime
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.net.sse
import calebxzhou.rdi.client.service.StartPlayResult
import calebxzhou.rdi.client.service.startPlay
import calebxzhou.rdi.client.ui.McPlayArgs
import calebxzhou.rdi.client.ui.BottomSnakebar
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.ConfirmDialog
import calebxzhou.rdi.client.ui.ErrorText
import calebxzhou.rdi.client.ui.MainBox
import calebxzhou.rdi.client.ui.MainColumn
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.SimpleTooltip
import calebxzhou.rdi.client.ui.Space8h
import calebxzhou.rdi.client.ui.Space8w
import calebxzhou.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.asIconText
import calebxzhou.rdi.client.ui.comp.Console
import calebxzhou.rdi.client.ui.comp.ConsoleState
import calebxzhou.rdi.client.ui.comp.HeadButton
import calebxzhou.rdi.client.ui.comp.ModpackCard
import calebxzhou.rdi.common.extension.isAdmin
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.Task
import calebxzhou.rdi.common.model.isDav
import calebxzhou.rdi.model.Role
import io.ktor.client.plugins.sse.SSEBufferPolicy
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.bson.types.ObjectId

/**
 * calebxzhou @ 2026-01-15 19:38
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostInfoScreen(
    hostId: ObjectId,
    onBack: () -> Unit = {},
    onOpenModpackInfo: ((String) -> Unit)? = null,
    onOpenMcPlay: ((McPlayArgs) -> Unit)? = null,
    onOpenMcVersions: ((McVersion?) -> Unit)? = null,
    onOpenHostEdit: ((Host.DetailVo) -> Unit)? = null,
    onOpenTask: ((Task) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var okMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var hostDetail by remember { mutableStateOf<Host.DetailVo?>(null) }
    var modpackDetail by remember { mutableStateOf<Modpack.DetailVo?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
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
    var installConfirmTask by remember { mutableStateOf<Task?>(null) }

    fun reload() {
        loading = true
        errorMessage = null
        scope.rdiRequest<Host.DetailVo>(
            path = "host/$hostId/detail",
            onOk = { response ->
                val detail = response.data
                if (detail == null) {
                    errorMessage = "无法加载地图信息"
                    loading = false
                    return@rdiRequest
                }
                hostDetail = detail
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
                errorMessage = "加载地图信息失败: ${it.message}"
            },
            onDone = { loading = false }
        )
    }

    LaunchedEffect(hostId) {
        reload()
    }
    LaunchedEffect(okMessage) {
        okMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            okMessage = null
        }
    }

    val host = hostDetail
    val meAdmin = host?.let { it.isAdmin(loggedAccount) || loggedAccount.isDav  }?: false
    val meOwner = host?.let { it.ownerId == loggedAccount._id || loggedAccount.isDav } ?: false

    DisposableEffect(selectedTab, hostId) {
        if (selectedTab != 1) {
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

    MainBox {
        MainColumn {
            TitleRow(title = host?.name ?: "地图详情", onBack = onBack) {
                errorMessage?.let { ErrorText(it) }
                Space8w()
                host?.let { host ->
                    SimpleTooltip("地图的创建者") {
                        HeadButton(host.ownerId)
                    }
                    Space8w()
                    host.onlinePlayerIds.forEach {
                        HeadButton(it, showName = false)
                    }
                    Space8w()
                    CircleIconButton(
                        icon = "\uF04B",
                        tooltip = "开始游玩",
                        bgColor = MaterialColor.GREEN_900.color
                    ) {
                        scope.launch {
                            val args = try {
                                host.startPlay()
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "无法开始游玩"
                                return@launch
                            }
                            when (args) {
                                is StartPlayResult.Ready -> {
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
                    Space8w()
                    if (meAdmin) {
                    CircleIconButton(
                        icon = "\uF013",
                        tooltip = "设置"
                    ) {
                        if (onOpenHostEdit != null) {
                            onOpenHostEdit(host)
                        } else {
                            errorMessage = "暂不支持编辑地图"
                        }
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
                            tooltip = "删除地图",
                            bgColor = MaterialColor.RED_900.color
                        ) { showDeleteConfirm = true }
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
                        Text(text = errorMessage ?: "无法加载地图信息", color = MaterialColor.RED_900.color)
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val compactLayout = maxWidth < 760.dp
                            if (compactLayout) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("\uEB29 整合包 ${host.modpack.name} ${host.packVer}".asIconText)
                                        Row {
                                            Text("\uF05F ${host.intro}".asIconText)
                                        }
                                        Row {
                                            Text("\uE384 ${host._id.timestamp.secondsToHumanDateTime}".asIconText)
                                        }
                                    }
                                    modpackDetail?.let {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            host.modpack.ModpackCard(
                                                modifier = Modifier.width(300.dp),
                                                onClick = { onOpenModpackInfo?.invoke(host.modpack.id.toHexString()) }
                                            )
                                        }
                                    } ?: Text(
                                        text = "找不到整合包",
                                        color = MaterialColor.RED_900.color
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("\uEB29 整合包 ${host.modpack.name} ${host.packVer}".asIconText)
                                        Row {
                                            Text("\uF05F ${host.intro}".asIconText)
                                        }
                                        Row {
                                            Text("\uE384 ${host._id.timestamp.secondsToHumanDateTime}".asIconText)
                                        }
                                    }
                                    modpackDetail?.let {
                                        host.modpack.ModpackCard(
                                            modifier = Modifier.width(300.dp),
                                            onClick = { onOpenModpackInfo?.invoke(host.modpack.id.toHexString()) }
                                        )
                                    } ?: Text(
                                        text = "找不到整合包",
                                        color = MaterialColor.RED_900.color
                                    )
                                }
                            }
                        }

                        val tabs = listOf("\uEF69 成员(${host.members.size}/10)", "\uDB80\uDD8D 后台", "\uE5FC 配置", "\uE615 信息")
                        TabRow(selectedTabIndex = selectedTab, backgroundColor = Color.White) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    text = { Text(title.asIconText) }
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
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (meOwner && member.role != Role.OWNER) {
                                                    TextButton(onClick = { transferConfirm = member.id }) {
                                                        Text("转让")
                                                    }
                                                }
                                                if (meOwner && member.role != Role.OWNER) {
                                                    val newRole = if (member.role == Role.ADMIN) Role.MEMBER else Role.ADMIN
                                                    TextButton(onClick = {
                                                        roleChangeConfirm = RoleChange(member.id, newRole)
                                                    }) {
                                                        Text(if (member.role == Role.ADMIN) "取消管理员" else "设为管理员")
                                                    }
                                                }
                                                if (meAdmin && member.role != Role.OWNER) {
                                                    TextButton(onClick = { kickConfirm = member.id }) {
                                                        Text("踢出", color = MaterialColor.RED_900.color)
                                                    }
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
                            1 -> {
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
                                            bgColor = MaterialColor.GREEN_900.color
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
                                            bgColor = MaterialColor.BLUE_800.color
                                        ) {
                                            restartConfirm = true
                                        }
                                        CircleIconButton(
                                            icon = "\uF04D",
                                            tooltip = "停止",
                                            bgColor = MaterialColor.RED_700.color
                                        ) {
                                            stopConfirm = true
                                        }
                                        CircleIconButton(
                                            icon = "\uF05E",
                                            tooltip = "强制停止",
                                            bgColor = MaterialColor.RED_900.color
                                        ) {
                                            forceStopConfirm = true
                                        }
                                    }
                                }
                            }
                            2 -> {
                                Text("配置文件功能开发中", color = MaterialColor.GRAY_700.color)
                            }
                            else -> {
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
                        }
                    }
                }

            }
        }
        BottomSnakebar(snackbarHostState)
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "确认删除",
            message = "确认删除地图吗？\n（仅删除成员列表。\n区块数据不会被删除，可导出或重复利用）",
            onConfirm = {
                scope.rdiRequestU(
                    path = "host/$hostId",
                    method = HttpMethod.Delete,
                    onOk = {
                        okMessage = "已删除"
                        onBack()
                    },
                    onErr = { errorMessage = it.message ?: "删除失败" }
                )
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    if (showUpdateConfirm && modpackDetail != null) {
        ConfirmDialog(
            title = "确认更新",
            message = "将更新地图当前的整合包《${modpackDetail!!.name}》到最新版本。",
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

    installConfirmTask?.let { task ->
        ConfirmDialog(
            title = "未下载整合包",
            message = "未下载此地图的整合包，是否立即下载？",
            onConfirm = {
                installConfirmTask = null
                if (onOpenTask != null) {
                    onOpenTask(task)
                } else {
                    errorMessage = "暂不支持在此页面下载"
                }
            },
            onDismiss = { installConfirmTask = null }
        )
    }

    // options dialog removed

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
            message = "确定将地图所有权转让给该成员吗？",
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
            message = "确定重启该地图吗？",
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
            message = "确定停止该地图吗？",
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
            message = "确定强制停止该地图吗？",
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
            title = "退出地图",
            message = "确定退出该地图吗？",
            onConfirm = {
                scope.rdiRequestU(
                    path = "host/$hostId/quit",
                    method = HttpMethod.Put,
                    onOk = {
                        okMessage = "已退出地图"
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
