package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.service.PlayerService
import calebxzhou.rdi.client.ui.AlertErr
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.MainBox
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.TitleRow2
import calebxzhou.rdi.client.ui.comp.PasswordField
import calebxzhou.rdi.client.ui.openMsaVerificationUrl
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.MsaAccountInfo
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.model.Request
import calebxzhou.rdi.common.service.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode

private enum class ResetPasswordMode {
    MSA, QQ_MAIL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPasswordScreen(
    onBack: () -> Unit,
    onResetSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(ResetPasswordMode.MSA) }
    var qq by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var pwd2 by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showPassword2 by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var msaDeviceCode by remember { mutableStateOf<MsaDeviceCode?>(null) }
    var showMailDialog by remember { mutableStateOf(false) }
    var mailTitle by remember { mutableStateOf("") }
    var mailContent by remember { mutableStateOf("") }
    var showReceiptQueryDialog by remember { mutableStateOf(false) }
    var showResetDoneDialog by remember { mutableStateOf(false) }

    fun validateNewPassword(): Boolean {
        if (pwd.isBlank() || pwd2.isBlank()) {
            errorMessage = "未填写完整"
            return false
        }
        if (pwd != pwd2) {
            errorMessage = "两次输入的密码不一致"
            return false
        }
        if (pwd.length !in 6..16) {
            errorMessage = "密码长度须在6~16个字符"
            return false
        }
        return true
    }

    fun resetByMsa() {
        if (!validateNewPassword() || submitting) return
        submitting = true
        errorMessage = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val manager = PlayerService.microsoftLogin { code ->
                        msaDeviceCode = code
                        openMsaVerificationUrl(code.directVerificationUri)
                    }.getOrThrow()
                    val msaInfo = MsaAccountInfo(
                        manager.minecraftProfile.upToDate.id,
                        manager.minecraftProfile.upToDate.name,
                        manager.minecraftToken.upToDate.token
                    )
                    PlayerService.resetPasswordByMsa(msaInfo, pwd).getOrThrow()
                }
            }
            submitting = false
            result.onFailure {
                errorMessage = it.message ?: "重置密码失败"
            }.onSuccess {
                showResetDoneDialog = true
            }
        }
    }

    fun generateQqMailOperation() {
        if (!validateNewPassword()) return
        val targetQq = qq.trim()
        if (targetQq.length !in 5..10 || !targetQq.all(Char::isDigit)) {
            errorMessage = "QQ号格式不正确"
            return
        }
        errorMessage = null
        mailTitle = newOperationMailTitle()
        mailContent = CryptoManager.encrypt(
            Request("resetPwd", RAccount.ResetPasswordByQqMailDto(targetQq, pwd)).json
        )
        showMailDialog = true
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
            val formWidthFraction = if (isPortrait) 0.92f else 0.42f
            val fieldShape = RoundedCornerShape(if (isPortrait) 28.dp else 36.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(outerPadding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TitleRow2("重置密码", onBack = onBack)
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth(formWidthFraction)
                        .widthIn(max = 760.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "选择验证方式",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TabRow(
                        selectedTabIndex = if (mode == ResetPasswordMode.MSA) 0 else 1,
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(
                                    tabPositions[if (mode == ResetPasswordMode.MSA) 0 else 1]
                                ),
                                color = Color.Black
                            )
                        }
                    ) {
                        Tab(
                            selected = mode == ResetPasswordMode.MSA,
                            onClick = {
                                mode = ResetPasswordMode.MSA
                                errorMessage = null
                            },
                            selectedContentColor = Color.Black,
                            unselectedContentColor = Color.Black,
                            text = { Text("微软账号") }
                        )
                        Tab(
                            selected = mode == ResetPasswordMode.QQ_MAIL,
                            onClick = {
                                mode = ResetPasswordMode.QQ_MAIL
                                errorMessage = null
                            },
                            selectedContentColor = Color.Black,
                            unselectedContentColor = Color.Black,
                            text = { Text("QQ邮箱") }
                        )
                    }

                    if (mode == ResetPasswordMode.QQ_MAIL) {
                        OutlinedTextField(
                            value = qq,
                            onValueChange = {
                                qq = it
                                errorMessage = null
                            },
                            label = { Text("QQ号") },
                            shape = fieldShape,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    PasswordField(
                        value = pwd,
                        onValueChange = {
                            pwd = it
                            errorMessage = null
                        },
                        label = "新密码",
                        shape = fieldShape,
                        showPassword = showPassword,
                        onToggleVisibility = { showPassword = !showPassword },
                        onEnter = {
                            if (mode == ResetPasswordMode.MSA) resetByMsa() else generateQqMailOperation()
                        }
                    )
                    PasswordField(
                        value = pwd2,
                        onValueChange = {
                            pwd2 = it
                            errorMessage = null
                        },
                        label = "确认新密码",
                        shape = fieldShape,
                        showPassword = showPassword2,
                        onToggleVisibility = { showPassword2 = !showPassword2 },
                        onEnter = {
                            if (mode == ResetPasswordMode.MSA) resetByMsa() else generateQqMailOperation()
                        }
                    )

                    if (msaDeviceCode != null && mode == ResetPasswordMode.MSA) {
                        Text("已打开微软验证页面，请在浏览器完成登录")
                    }

                    errorMessage?.let { AlertErr(it) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (mode == ResetPasswordMode.QQ_MAIL) {
                            OutlinedButton(
                                onClick = {
                                    errorMessage = null
                                    showReceiptQueryDialog = true
                                },
                                enabled = !submitting,
                                shape = fieldShape
                            ) {
                                Text("查询进度")
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        CircleIconButton(
                            icon = if (mode == ResetPasswordMode.MSA) "\uE70F" else "\uF0E0",
                            tooltip = if (submitting) "处理中..." else "重置密码",
                            enabled = !submitting,
                            bgColor = MaterialColor.BLUE_200.color,
                            iconColor = Color.Black
                        ) {
                            if (mode == ResetPasswordMode.MSA) resetByMsa() else generateQqMailOperation()
                        }
                    }
                }
            }
        }

        if (showMailDialog) {
            MailOperationGuideDialog(
                operationName = "重置密码",
                qq = qq.trim(),
                mailTitle = mailTitle,
                encryptedContent = mailContent,
                onDismiss = { showMailDialog = false }
            )
        }
        ReceiptQueryDialogs(
            operationName = "重置密码",
            showQueryDialog = showReceiptQueryDialog,
            onShowQueryDialogChange = { showReceiptQueryDialog = it },
            onResultClosed = { result ->
                if (result.contains("密码重置完成")) {
                    showResetDoneDialog = true
                }
            }
        )
        if (showResetDoneDialog) {
            AlertDialog(
                onDismissRequest = {
                    showResetDoneDialog = false
                    onResetSuccess()
                },
                title = { Text("密码重置完成") },
                text = { Text("请使用新密码登录") },
                containerColor = MaterialTheme.colorScheme.surface,
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResetDoneDialog = false
                            onResetSuccess()
                        }
                    ) {
                        Text("去登录")
                    }
                }
            )
        }
    }
}
