package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.service.PlayerService
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.PasswordField
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.MsaAccountInfo
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.model.Request
import calebxzhou.rdi.common.service.CryptoManager
import calebxzhou.rdi.common.util.validatePlayerName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.raphimc.minecraftauth.java.JavaAuthManager
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode

/**
 * calebxzhou @ 2026-02-08 16:59
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    useMsa: Boolean = true,
    onBack: () -> Unit,
    onRegisterSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var name by remember { mutableStateOf("") }
    var qq by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var pwd2 by remember { mutableStateOf("") }
    var msaDeviceCode by remember { mutableStateOf<MsaDeviceCode?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    var showPassword2 by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var okMessage by remember { mutableStateOf<String?>(null) }
    var msaInfo by remember { mutableStateOf<MsaAccountInfo?>(null) }
    var mcName by remember { mutableStateOf<String?>(null) }
    var authManager by remember { mutableStateOf<JavaAuthManager?>(null) }
    var showRegisterCodeDialog by remember { mutableStateOf(false) }
    var registerCode by remember { mutableStateOf("") }
    var registerMailTitle by remember { mutableStateOf("") }
    var showReceiptQueryDialog by remember { mutableStateOf(false) }
    fun validateRegisterInput(): Boolean {
        if (pwd != pwd2) {
            errorMessage = "两次输入的密码不一致"
            return false
        }
        if (name.isBlank() || qq.isBlank() || pwd.isBlank()) {
            errorMessage = "未填写完整"
            return false
        }
        name.validatePlayerName().getOrElse {
            errorMessage = it.message ?: "昵称格式不正确"
            return false
        }
        return true
    }
    LaunchedEffect(okMessage) {
        okMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            okMessage = null
        }
    }
    LaunchedEffect(Unit) {
        if (!useMsa) return@LaunchedEffect
        scope.launch(Dispatchers.IO) {
            val manager = PlayerService.microsoftLogin { code ->
                msaDeviceCode = code
                // Open verification URI
                openMsaVerificationUrl(code.directVerificationUri)
            }.getOrElse {
                errorMessage = "登录微软MC失败：${it.message}"
                return@launch
            }
            // Login completed, get profile info directly
            authManager = manager
            msaInfo = MsaAccountInfo(
                manager.minecraftProfile.upToDate.id,
                manager.minecraftProfile.upToDate.name,
                manager.minecraftToken.upToDate.token,
            ).also { name = it.name }

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
            val formWidthFraction = if (isPortrait) 0.92f else 0.4f
            val fieldShape = RoundedCornerShape(24.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(outerPadding),
                verticalArrangement = Arrangement.spacedBy(rootSpacing),
            ) {
                TitleRow2(
                    title = "注册",
                    onBack
                )
                Column(
                    modifier = Modifier.fillMaxWidth(formWidthFraction).align(Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(formSpacing)
                ) {
                    if (useMsa) {
                        // MS Account registration flow
                        if (msaInfo == null) {
                            Text("即将登录微软账号，点击复制浏览器中打开链接，请在10分钟内登录")
                            Text("不要切换到其他页面！", fontWeight = FontWeight.Bold)
                            Text("登录完成后稍等10秒，会自动读取账号信息以进行下一步")
                            msaDeviceCode?.let { msaDeviceCode ->
                                Text(
                                    text = msaDeviceCode.directVerificationUri,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                                    modifier = Modifier
                                        .clickable {
                                            copyToClipboard(msaDeviceCode.directVerificationUri)
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    "链接已复制到剪贴板",
                                                    duration = SnackbarDuration.Short
                                                )
                                            }
                                        }
                                )
                            }
                        }
                        msaInfo?.let { msaInfo ->
                            Text("登录成功！${msaInfo.name} · MSID ${msaInfo.uuid}")

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                shape = fieldShape,
                                label = { Text("昵称 支持中文") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = qq,
                                onValueChange = { qq = it },
                                shape = fieldShape,
                                label = { Text("QQ号") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            PasswordField(
                                value = pwd,
                                onValueChange = { pwd = it },
                                label = "密码",
                                shape = fieldShape,
                                showPassword = showPassword,
                                onToggleVisibility = { showPassword = !showPassword },
                                onEnter = {}
                            )
                            PasswordField(
                                value = pwd2,
                                onValueChange = { pwd2 = it },
                                label = "确认密码",
                                shape = fieldShape,
                                showPassword = showPassword2,
                                onToggleVisibility = { showPassword2 = !showPassword2 },
                                onEnter = {}
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        if (!validateRegisterInput()) {
                                            return@Button
                                        }
                                        submitting = true
                                        errorMessage = null
                                        scope.rdiRequestU(
                                            "player/register", body = RAccount.RegisterDto(name, qq, pwd, msaInfo).json,
                                            onDone = { submitting = false },
                                            onErr = { errorMessage = it.message ?: "注册失败" }) {
                                            okMessage = "注册成功，请登录"
                                            onRegisterSuccess?.invoke()
                                        }

                                    },
                                    enabled = !submitting
                                ) {
                                    Text(if (submitting) "注册中..." else "注册")
                                }
                            }
                        }
                    } else {
                        // Non-MS Account registration flow - generate registration code
                        Text("填写信息")

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            shape = fieldShape,
                            label = { Text("昵称 支持中文") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = qq,
                            onValueChange = { qq = it },
                            shape = fieldShape,
                            label = { Text("QQ号") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        PasswordField(
                            value = pwd,
                            onValueChange = { pwd = it },
                            label = "密码",
                            shape = fieldShape,
                            showPassword = showPassword,
                            onToggleVisibility = { showPassword = !showPassword },
                            onEnter = {}
                        )
                        PasswordField(
                            value = pwd2,
                            onValueChange = { pwd2 = it },
                            label = "确认密码",
                            shape = fieldShape,
                            showPassword = showPassword2,
                            onToggleVisibility = { showPassword2 = !showPassword2 },
                            onEnter = {}
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    if (!validateRegisterInput()) {
                                        return@Button
                                    }
                                    errorMessage = null

                                    // Generate encrypted registration code
                                    val dto = Request("register", RAccount.RegisterDto(name, qq, pwd, null))
                                    registerMailTitle = newOperationMailTitle()
                                    registerCode = CryptoManager.encrypt(dto.json)

                                    showRegisterCodeDialog = true
                                },
                                enabled = !submitting
                            ) {
                                Text("注册")
                            }
                        }
                    }
                    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        BottomSnakebarM3(snackbarHostState)

        if (!useMsa) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                CircleIconButton(
                    icon = "\uF002",
                    tooltip = "注册进度查询",
                    bgColor = MaterialColor.GREEN_900.color
                ) {
                    showReceiptQueryDialog = true
                }
            }
        }

        // Register Code Dialog
        if (showRegisterCodeDialog) {
            MailOperationGuideDialog(
                operationName = "注册",
                qq = qq,
                mailTitle = registerMailTitle,
                encryptedContent = registerCode,
                onDismiss = { showRegisterCodeDialog = false }
            )
        }

        ReceiptQueryDialogs(
            operationName = "注册",
            showQueryDialog = showReceiptQueryDialog,
            onShowQueryDialogChange = { showReceiptQueryDialog = it }
        )
    }
}
