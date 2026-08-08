package calebxzau.rdi.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.auth.AccountSessionStore
import calebxzau.rdi.client.ui.themeNow
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.PlayerService
import calebxzhou.rdi.client.service.SettingsService
import calebxzhou.rdi.client.service.playerInfoCache
import calebxzhou.rdi.client.ui.comp.PlayerModel
import calebxzhou.rdi.client.ui.comp.RPasswordField
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.MsaAccountInfo
import calebxzhou.rdi.common.model.RAccount
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.ErrorText
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.RTextField
import calebxzau.rdi.client.ui.RScrollableColumn
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.copyToClipboard
import calebxzau.rdi.client.ui.openMsaVerificationUrl
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode

@Composable
fun PlayerInfoScreen(onBack: () -> Unit) {
    val account by AccountSessionStore.account.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showChangeProfile by remember { mutableStateOf(false) }
    var startMsBind by remember { mutableStateOf(false) }
    var pendingBind by remember { mutableStateOf(false) }
    var msaInfo by remember { mutableStateOf<MsaAccountInfo?>(null) }
    var msaDeviceCode by remember { mutableStateOf<MsaDeviceCode?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun clearMsaState() {
        startMsBind = false
        msaInfo = null
        msaDeviceCode = null
    }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.MEDIUM) {
            TitleRow("个人信息", onBack) {}

            ContentBody {
                if (account._id == RAccount.DEFAULT._id) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("请先登录")
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(220.dp)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            PlayerModel(
                                skinUrl = account.cloth.skin,
                                capeUrl = account.cloth.cape,
                                modifier = Modifier.size(width = 220.dp, height = 330.dp),
                                backgroundColor = MaterialTheme.colorScheme.surface,
                                isSlim = account.cloth.isSlim,
                                maxRenderSide = 480,
                                noControl = true
                            )
                        }

                        RScrollableColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                        PlayerInfoRow(
                            label = "昵称",
                            value = account.name,
                            action = {
                                CircleIconButton(
                                    icon = "\uF040",
                                    tooltip = "修改个人信息",
                                    size = 24.dp,
                                    bgColor = MaterialTheme.colorScheme.secondaryContainer,
                                    iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    showText = false,
                                    onClick = { showChangeProfile = true }
                                )
                            }
                        )
                        PlayerInfoRow("QQ", account.qq)
                        PlayerInfoRow("RDID", account._id.toHexString(), selectable = true)
                        PlayerInfoRow(
                            label = "微软账号",
                            value = if (account.hasMsid) "已绑定" else "未绑定",
                            action = {
                                if (!account.hasMsid && !startMsBind) {
                                    CircleIconButton(
                                        icon = "\uE70F",
                                        label = "绑定微软MC账号",
                                        size = 28.dp,
                                        onClick = {
                                            startMsBind = true
                                            errorMessage = null
                                            scope.launch {
                                                val manager = withContext(Dispatchers.IO) {
                                                    PlayerService.microsoftLogin { code ->
                                                        scope.launch {
                                                            msaDeviceCode = code
                                                            openMsaVerificationUrl(code.directVerificationUri)
                                                        }
                                                    }
                                                }.getOrElse {
                                                    it.printStackTrace()
                                                    errorMessage = "登录微软MC失败：${it.message}"
                                                    startMsBind = false
                                                    return@launch
                                                }
                                                msaInfo = MsaAccountInfo(
                                                    manager.minecraftProfile.upToDate.id,
                                                    manager.minecraftProfile.upToDate.name,
                                                    manager.minecraftToken.upToDate.token
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        )

                        if (!account.hasMsid && startMsBind) {
                            Text("即将登录微软账号，点击复制浏览器中打开链接，请在10分钟内登录")
                            Text("不要切换到其他页面！", fontWeight = FontWeight.Bold)
                            Text("登录完成后稍等10秒，会自动读取账号信息以进行下一步")
                        }

                        msaDeviceCode?.let { deviceCode ->
                            Text(
                                text = deviceCode.directVerificationUri,
                                color = MaterialTheme.colorScheme.primary,
                                style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                                modifier = Modifier.clickable {
                                    copyToClipboard(deviceCode.directVerificationUri)
                                }
                            )
                        }

                        msaInfo?.let { info ->
                            Text("读取信息成功！昵称：${info.name}，MSID:${info.uuid}")
                            Text("绑定后将不能修改，如果确定账号信息正确，请点击确认。")
                            CircleIconButton(
                                icon = "\uDB82\uDE50",
                                label = "确认绑定",
                                bgColor = themeNow.primary,
                                enabled = !pendingBind
                            ) {
                                pendingBind = true
                                scope.rdiRequestU(
                                    path = "player/bind-ms",
                                    body = info.json,
                                    onDone = {
                                        clearMsaState()
                                        pendingBind = false
                                    },
                                    onErr = {
                                        it.printStackTrace()
                                        errorMessage = "绑定失败：${it.message}，请重试"
                                    },
                                    onOk = {
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                PlayerService.login(account._id.toHexString(), account.pwd)
                                            }
                                            result.onSuccess {
                                                snackbarHostState.showSnackbar("微软账号绑定成功")
                                            }.onFailure {
                                                it.printStackTrace()
                                                errorMessage = "重新登录失败：${it.message}"
                                            }
                                        }
                                    }
                                )
                            }
                        }

                            errorMessage?.let { ErrorText(it) }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    if (showChangeProfile) {
        ChangeProfileDialog(
            onDismiss = { showChangeProfile = false },
            onSuccess = {
                showChangeProfile = false
                scope.launch {
                    snackbarHostState.showSnackbar("修改成功")
                }
            }
        )
    }
}

@Composable
private fun PlayerInfoRow(
    label: String,
    value: String,
    selectable: Boolean = false,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(88.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectable) {
                SelectionContainer {
                    PlayerInfoValue(value)
                }
            } else {
                PlayerInfoValue(value)
            }
            action?.invoke()
        }
    }
}

@Composable
private fun PlayerInfoValue(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun ChangeProfileDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val account = loggedAccount
    var name by remember { mutableStateOf(account.name) }
    var pwd by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("修改信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "昵称",
                    enabled = !submitting
                )
                RPasswordField(
                    value = pwd,
                    onValueChange = { pwd = it },
                    label = "新密码 留空则不修改",
                    enabled = !submitting
                )
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    val validation = SettingsService.validateProfileChange(name, pwd, account.name)
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
                            val loginPwd = pwd.takeIf(String::isNotEmpty) ?: account.pwd
                            loggedAccount = PlayerService.login(account._id.toHexString(), loginPwd).getOrThrow()
                            playerInfoCache.put(loggedAccount.dto)
                        }.getOrElse {
                            it.printStackTrace()
                            errorMessage = "修改失败:${it.message}"
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
