package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import calebxzhou.rdi.client.Const
import calebxzhou.rdi.client.CodeFontFamily
import calebxzhou.rdi.client.auth.LocalCredentials
import calebxzhou.rdi.client.net.BACKUP_NODE
import calebxzhou.rdi.client.service.PlayerService
import calebxzhou.rdi.client.service.tryEnableBackupNodeForPeakHours
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.common.DEBUG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * calebxzhou @ 2026-01-14 16:45
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val qqState = rememberTextFieldState()
    val pwdState = rememberTextFieldState()
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
    val backgroundImage = remember { loadResourceBitmap("assets/bg1.webp") }

    fun attemptLogin() {
        val qq = qqState.text.toString()
        val pwd = pwdState.text.toString()
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
        if (qqState.text.isBlank() && pwdState.text.isBlank()) {
            creds.lastLogged?.let {
                qqState.setTextAndPlaceCursorAtEnd(it.qq)
                pwdState.setTextAndPlaceCursorAtEnd(it.pwd)
            }
        }
    }
    LaunchedEffect(Unit) {
        // Desktop-only: symlink check
        if (isDesktop && !checkCanCreateSymlink()) {
            symlinkError = """RDI需要权限为Mod及资源文件创建软连接。
请点击上方【创建桌面快捷方式】按钮，从桌面图标运行RDI。"""
        }
        tryEnableBackupNodeForPeakHours()
        if(Const.NO_UPDATE){
            updateCheckComplete=true
            updateDetail = "自动更新已关闭"
            return@LaunchedEffect
        }
        runDesktopUpdateFlow(
            onStatus = { updateStatus = it },
            onDetail = { updateDetail = it },
            onRestart = {
                if (isDesktop) {
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
            modifier = Modifier.fillMaxSize()
        ) {
            Image(
                bitmap = backgroundImage,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
            )
            val isPortrait = maxHeight > maxWidth
            val outerPadding = if (isPortrait) PaddingValues(18.dp) else PaddingValues(18.dp)
            val rootSpacing = if (isPortrait) 12.dp else 16.dp
            val formSpacing = if (isPortrait) 10.dp else 12.dp
            val panelWidthFraction = if (isPortrait) 0.92f else 0.3f
            val panelHeightFraction = if (isPortrait) 0.82f else 0.6f
            val formWidthFraction = if (isPortrait) 1f else 0.82f

            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(panelWidthFraction)
                    .fillMaxHeight(panelHeightFraction),
                shape = RoundedCornerShape(if (isPortrait) 28.dp else 36.dp),
                color = Color.White.copy(alpha = 0.96f),
                elevation = 18.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(outerPadding),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "RDi",
                            style = MaterialTheme.typography.h5,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(formWidthFraction),
                            verticalArrangement = Arrangement.spacedBy(formSpacing)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    state = qqState,
                                    label = if (qqState.text.isBlank()) {
                                        { Text("RDID/QQ号") }
                                    } else {
                                        null
                                    },
                                    lineLimits = TextFieldLineLimits.SingleLine,
                                    textStyle = MaterialTheme.typography.body2,
                                    contentPadding = PaddingValues(horizontal = 8.dp,0.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .onKeyEvent { event ->
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
                                            qqState.setTextAndPlaceCursorAtEnd(info.qq)
                                            pwdState.setTextAndPlaceCursorAtEnd(info.pwd)
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
                            OutlinedSecureTextField(
                                state = pwdState,
                                label = if (pwdState.text.isBlank()) {
                                    { Text("密码") }
                                } else {
                                    null
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp,0.dp),
                                textStyle = MaterialTheme.typography.body2,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .onKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                            attemptLogin()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                textObfuscationMode = if (showPassword) {
                                    TextObfuscationMode.Visible
                                } else {
                                    TextObfuscationMode.RevealLastTyped
                                },
                                trailingIcon = {
                                    Text(
                                        text = "\uDB80\uDE08".asIconText,
                                        style = MaterialTheme.typography.h6.copy(
                                            fontFamily = CodeFontFamily,
                                            fontSize = 20.sp
                                        ),
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .clickable { showPassword = !showPassword }
                                    )
                                }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircleIconButton(
                                    "\uDB80\uDF42",
                                    if (submitting) "登录中.." else "登录",
                                    enabled = !submitting && updateCheckComplete,
                                    bgColor = if(BACKUP_NODE) MaterialColor.YELLOW_200.color else MaterialColor.BLUE_200.color, iconColor = Color.Black
                                )  {
                                    attemptLogin()
                                }
                                Space8w()
                                CircleIconButton("\uEBCD","注册", bgColor = MaterialColor.PINK_200.color, iconColor = Color.Black){
                                    showMsAccountDialog = true
                                }
                            }
                            if (isDesktop) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircleIconButton("\uDB83\uDCFD","创建桌面图标", bgColor = MaterialColor.TEAL_200.color, iconColor = Color.Black){
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
                                    }
                                }
                            }

                            if (isDesktop) {
                                symlinkError?.let { message ->
                                    AlertWarn(message)
                                }
                            }

                            loginError?.let { message ->
                                AlertErr(message)
                            }
                            Box(
                                modifier = Modifier.width(20.dp).height(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (submitting) {
                                    CircularProgressIndicator(modifier = Modifier.width(16.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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
                        Text(
                            "v"+Const.VERSION_NUMBER + if(DEBUG)"debug" else "",
                            style = MaterialTheme.typography.caption,
                        )
                        if(!isDesktop) {
                            Text(
                                "为了正常下包 请确保RDI有文件+通知权限",
                                style = MaterialTheme.typography.caption,
                            )
                        }
                    }
                }
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
                            "有微软MC正版号吗？",
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
