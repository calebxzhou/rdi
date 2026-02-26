package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.auth.LocalCredentials
import calebxzhou.rdi.client.service.PlayerService
import calebxzhou.rdi.client.ui.BottomSnakebar
import calebxzhou.rdi.client.ui.MainBox
import calebxzhou.rdi.client.ui.checkCanCreateSymlink
import calebxzhou.rdi.client.ui.createDesktopShortcut
import calebxzhou.rdi.client.ui.isDesktop
import calebxzhou.rdi.client.ui.runDesktopUpdateFlow
import calebxzhou.rdi.client.ui.comp.PasswordField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.window.Dialog
import calebxzhou.rdi.client.Const
import calebxzhou.rdi.client.ui.Space8w
import calebxzhou.rdi.common.DEBUG


/**
 * calebxzhou @ 2026-01-14 16:45
 */
@Composable
fun LoginScreen(
    onLoginSuccess: (() -> Unit)? = null,
    onOpenRegister: ((Boolean) -> Unit)? = null
) {
    var showPassword by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val creds = remember { LocalCredentials.read() }
    val storedAccounts = remember(creds.loginInfos) {
        creds.loginInfos.entries.sortedByDescending { it.value.lastLoggedTime }
    }
    var qq by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var showAccounts by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var updateStatus by remember { mutableStateOf("正在检查更新...") }
    var updateDetail by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    var okMessage by remember { mutableStateOf<String?>(null) }
    var symlinkError by remember { mutableStateOf<String?>(null) }
    var updateCheckComplete by remember { mutableStateOf(false) }
    var showMsAccountDialog by remember { mutableStateOf(false) }

    fun attemptLogin() {
        if (qq.isBlank() || pwd.isBlank()) {
            loginError = "未填写完整"
            return
        }
        if (submitting) return
        submitting = true
        loginError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                PlayerService.login(qq, pwd)
            }
            submitting = false
            result.onFailure {
                loginError = it.message ?: "登录失败"
            }.onSuccess {
                onLoginSuccess?.invoke()
            }
        }
    }

    LaunchedEffect(storedAccounts) {
        if (qq.isBlank() && pwd.isBlank()) {
            creds.lastLogged?.let {
                qq = it.qq
                pwd = it.pwd
            }
        }
    }
    LaunchedEffect(Unit) {
        // Desktop-only: symlink check
        if (isDesktop && !checkCanCreateSymlink()) {
            symlinkError = """RDI需要权限为Mod及资源文件创建软连接。
请点击上方【创建桌面快捷方式】按钮，从桌面图标运行RDI。"""
        }
        runDesktopUpdateFlow(
            onStatus = { updateStatus = it },
            onDetail = { updateDetail = it },
            onRestart = {
                if (isDesktop) {
                    updateStatus = "更新完成，客户端将在 5 秒关闭..."
                    for (i in 5 downTo 1) {
                        updateDetail = "${i}秒"
                        delay(1000)
                    }
                    kotlin.system.exitProcess(0)
                }
            }
        )
        updateCheckComplete = true
    }
    LaunchedEffect(okMessage) {
        okMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            okMessage = null
        }
    }

    MainBox {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            val isPortrait = maxHeight > maxWidth
            val outerPadding = if (isPortrait) {
                PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            } else {
                PaddingValues(24.dp)
            }
            val rootSpacing = if (isPortrait) 12.dp else 16.dp
            val formSpacing = if (isPortrait) 10.dp else 12.dp
            val formWidthFraction = if (isPortrait) 0.9f else 0.4f

            Column(
                modifier = Modifier.fillMaxSize().padding(outerPadding),
                verticalArrangement = Arrangement.spacedBy(rootSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("登录", style = MaterialTheme.typography.h5, color = Color.Black)

                Column(
                    modifier = Modifier.fillMaxWidth(formWidthFraction),
                    verticalArrangement = Arrangement.spacedBy(formSpacing)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = qq,
                            onValueChange = { qq = it },
                            label = { Text("RDID/QQ号") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                    attemptLogin()
                                    true
                                } else {
                                    false
                                }
                            },
                            trailingIcon = {
                                TextButton(onClick = { showAccounts = true }) {
                                    Text("▼")
                                }
                            },
                        )
                        DropdownMenu(
                            expanded = showAccounts,
                            onDismissRequest = { showAccounts = false }
                        ) {
                            storedAccounts.forEach { entry ->
                                val info = entry.value
                                DropdownMenuItem(onClick = {
                                    qq = info.qq
                                    pwd = info.pwd
                                    showAccounts = false
                                }) {
                                    Text("${info.name} (${info.qq})")
                                }
                            }
                            if (storedAccounts.isEmpty()) {
                                DropdownMenuItem(onClick = { showAccounts = false }) {
                                    Text("暂无历史账号")
                                }
                            }
                        }
                    }
                    PasswordField(
                        value = pwd,
                        onValueChange = { pwd = it },
                        showPassword = showPassword,
                        onToggleVisibility = { showPassword = !showPassword },
                        onEnter = { attemptLogin() }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Desktop-only: shortcut creation button
                        if (isDesktop) {
                            TextButton(onClick = {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        createDesktopShortcut()
                                    }
                                    if (result.isSuccess) {
                                        okMessage = "已创建桌面快捷方式"
                                    } else {
                                        loginError = result.exceptionOrNull()?.message ?: "创建快捷方式失败"
                                    }
                                }
                            }) {
                                Text("创建桌面快捷方式", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Spacer(Modifier)
                        }
                        if(DEBUG){
                            Text("debug mode")
                            Space8w()
                        }
                        TextButton(onClick = { showMsAccountDialog = true }) {
                            Text("注册")
                        }
                    }

                    // Desktop-only: symlink error message
                    if (isDesktop) {
                        symlinkError?.let { message ->
                            Text(message, color = MaterialTheme.colors.error)
                        }
                    }

                    loginError?.let { message ->
                        Text(message, color = MaterialTheme.colors.error)
                    }

                    Row(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.width(20.dp).height(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (submitting) {
                                CircularProgressIndicator(modifier = Modifier.width(16.dp), strokeWidth = 2.dp)
                            }
                        }
                        Button(
                            onClick = {
                                attemptLogin()
                            },
                            enabled = !submitting && updateCheckComplete
                        ) {
                            Text(if (submitting) "登录中..." else "登录")
                        }
                    }

                }
                if (isDesktop) {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = updateStatus,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface
                    )
                    if (updateDetail.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = updateDetail,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface
                        )
                    }

                }
                Text("版本 "+Const.VERSION_NUMBER,style = MaterialTheme.typography.caption,)
            }
        }

        BottomSnakebar(snackbarHostState)

        // MS Account Dialog
        if (showMsAccountDialog) {
            Dialog(onDismissRequest = { showMsAccountDialog = false }) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colors.surface,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "你有微软MC账号吗？",
                            style = MaterialTheme.typography.h6
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    showMsAccountDialog = false
                                    onOpenRegister?.invoke(true)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("有")
                            }
                            OutlinedButton(
                                onClick = {
                                    showMsAccountDialog = false
                                    onOpenRegister?.invoke(false)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("没有")
                            }
                        }
                    }
                }
            }
        }
    }
}
