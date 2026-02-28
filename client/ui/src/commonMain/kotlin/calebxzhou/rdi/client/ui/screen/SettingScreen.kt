package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.PlayerService
import calebxzhou.rdi.client.service.playerInfoCache
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.HeadButton
import calebxzhou.rdi.client.ui.comp.PasswordField
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.MsaAccountInfo
import calebxzhou.rdi.common.model.RAccount
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

    // Desktop-only settings state
    var useMirror by remember { mutableStateOf(false) }
    var maxMemoryText by remember { mutableStateOf("") }
    var jre21Path by remember { mutableStateOf("") }
    var jre8Path by remember { mutableStateOf("") }
    var carrier by remember { mutableStateOf(0) }
    var proxyEnabled by remember { mutableStateOf(false) }
    var proxySystem by remember { mutableStateOf(false) }
    var proxyHost by remember { mutableStateOf("127.0.0.1") }
    var proxyPortText by remember { mutableStateOf("10808") }
    var proxyUsr by remember { mutableStateOf("") }
    var proxyPwd by remember { mutableStateOf("") }
    var totalMemoryMb by remember { mutableStateOf(0) }

    // Load config on desktop
    if (isDesktop) {
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val config = calebxzhou.rdi.client.AppConfig.load()
                    useMirror = config.useMirror
                    maxMemoryText = if (config.maxMemory <= 0) "" else config.maxMemory.toString()
                    jre21Path = config.jre21Path.orEmpty()
                    jre8Path = config.jre8Path.orEmpty()
                    carrier = config.carrier
                    proxyEnabled = config.proxyConfig?.enabled ?: false
                    proxySystem = config.proxyConfig?.systemProxy ?: false
                    proxyHost = config.proxyConfig?.host ?: "127.0.0.1"
                    proxyPortText = (config.proxyConfig?.port ?: 10808).toString()
                    proxyUsr = config.proxyConfig?.usr.orEmpty()
                    proxyPwd = config.proxyConfig?.pwd.orEmpty()
                    totalMemoryMb = calebxzhou.rdi.client.service.SettingsService.getTotalPhysicalMemoryMb()
                }
            }
        }
    }

    MainBox {
        MainColumn {
            TitleRow("设置", onBack) {
                if (isDesktop) {
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
                            val jre21 = jre21Path.trim().takeIf { it.isNotEmpty() }
                            val jre8 = jre8Path.trim().takeIf { it.isNotEmpty() }
                            val java21Ok = withContext(Dispatchers.IO) {
                                jre21?.let { svc.validateJavaPath(it, 21) } ?: Result.success(Unit)
                            }
                            val java8Ok = withContext(Dispatchers.IO) {
                                jre8?.let { svc.validateJavaPath(it, 8) } ?: Result.success(Unit)
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
                            // Save settings
                            svc.saveSettings(
                                useMirror = useMirror,
                                maxMemoryText = maxMemoryText,
                                jre21Path = jre21Path,
                                jre8Path = jre8Path,
                                carrier = carrier,
                                proxyEnabled = proxyEnabled,
                                proxySystem = proxySystem,
                                proxyHost = proxyHost,
                                proxyPortText = proxyPortText,
                                proxyUsr = proxyUsr,
                                proxyPwd = proxyPwd
                            ).onSuccess {
                                errorMessage = null
                                scaffoldState.snackbarHostState.showSnackbar("设置已保存")
                            }.onFailure {
                                errorMessage = "保存失败: ${it.message}"
                            }
                            saving = false
                        }
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
                                            jre21Path = jre21Path,
                                            onJre21Change = { jre21Path = it },
                                            jre8Path = jre8Path,
                                            onJre8Change = { jre8Path = it }
                                        )
                                    }
                                }
                                SettingCategory.Network -> {
                                    NetworkSettings(
                                        useMirror = useMirror,
                                        onUseMirrorChange = { useMirror = it },
                                        carrier = carrier,
                                        onCarrierChange = { carrier = it },
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
                                        onProxyPwdChange = { proxyPwd = it }
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
    Network("\uEF09", "网络");

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
    fun clearMsaState(){
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
        if (loggedAccount.hasMsid) {

            Text("已绑定微软MC账号，邀请你的朋友一起玩RDI。")
            Space8h()

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("已邀请：${invitedPlayers.size}/5人")
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                invitedPlayers.forEach { player ->
                    HeadButton(player.id)
                }
                if (invitedPlayers.size < 5) {
                    CircleIconButton("\uF234", "邀请") {
                        showInviteDialog = true
                    }
                }
            }
        } else {
            if (!startMsBind){
                RowV {
                    Text("绑定微软MC账号，可获得更丰富的RDI体验 👉")
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
        }
        msaInfo?.let { info ->
            Text("读取信息成功！昵称：${info.name} MSID: ${info.uuid}")
            RowV {
                Text("绑定后将不能修改，如果确定账号信息正确，")
                Space8w()
                CircleIconButton("\uDB82\uDE50", "ok", bgColor = MaterialColor.GREEN_900.color, enabled = !pendingBind) {
                    scope.rdiRequestU("player/bind-ms", body = info.json, onDone = {
                        clearMsaState()
                        pendingBind=false
                    }, onErr = {
                        errMsg = "绑定失败：${it.message}，请重试"
                    }) {
                        loggedAccount = loggedAccount.copy(msid = info.uuid)
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

@Composable
private fun JavaSettings(
    totalMemoryMb: Int,
    maxMemoryText: String,
    onMaxMemoryChange: (String) -> Unit,
    jre21Path: String,
    onJre21Change: (String) -> Unit,
    jre8Path: String,
    onJre8Change: (String) -> Unit
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
        OutlinedTextField(
            label = { Text("Java21主程序路径（可选 留空自带）") },
            value = jre21Path,
            onValueChange = onJre21Change,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Space8h()
        OutlinedTextField(
            label = { Text("Java8主程序路径（可选 留空自带）") },
            value = jre8Path,
            onValueChange = onJre8Change,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


@Composable
private fun NetworkSettings(
    useMirror: Boolean,
    onUseMirrorChange: (Boolean) -> Unit,
    carrier: Int,
    onCarrierChange: (Int) -> Unit,
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
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = useMirror, onCheckedChange = onUseMirrorChange)
            Text("使用镜像源（下载更快）")
        }
        Space8h()
        CarrierSelector(
            selected = carrier,
            onSelect = onCarrierChange
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

@Composable
private fun CarrierSelector(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val carriers = listOf("电信", "移动", "联通", "教育网", "广电")

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "运营商节点"
        )

        // ← This is the key change
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),  // space between options
            verticalArrangement = Arrangement.spacedBy(8.dp)      // space between rows when wrapped
        ) {
            carriers.forEachIndexed { index, name ->
                // Each radio + label is now one self-contained item
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onSelect(index) }   // whole row is clickable (better UX)
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = selected == index,
                        onClick = { onSelect(index) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = name)
                }
            }
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
                    val validation = calebxzhou.rdi.client.service.SettingsService.validateProfileChange(name, pwd, account.name)
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
