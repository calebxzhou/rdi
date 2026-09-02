package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ConfirmDialog
import calebxzau.rdi.client.ui.FlowRowV
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.ScrollableContentBody
import calebxzau.rdi.client.ui.RowV
import calebxzau.rdi.client.ui.SimpleTooltip
import calebxzau.rdi.client.ui.Space8h
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.AlertErr
import calebxzau.rdi.client.ui.AlertOk
import calebxzau.rdi.client.ui.themeNow
import calebxzau.rdi.client.ui.viewmodel.HostCreateEvent
import calebxzau.rdi.client.ui.viewmodel.HostCreateViewModel
import calebxzau.rdi.client.ui.viewmodel.HostCreateViewModelArgs
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.GameRuleModal
import calebxzhou.rdi.client.ui.comp.ImageCard
import kotlinx.coroutines.flow.collect
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.random.Random

/**
 * calebxzhou @ 2026-02-28 22:46
 */

@Composable
fun HostNewCreateScreen(
    arg: HostCreate,
    onBack: () -> Unit,
) {
    val defaultHostName = remember(arg.hostId) {
        "${loggedAccount.name}的世界${Random.nextInt(1000)}"
    }
    val routeKey = arg.hostId?.trim()?.ifBlank { "create" }
        ?: "create:${arg.kind}:${arg.sourceId}:${arg.legacyVersionName}:${arg.legacyMcVersion}"
    val viewModel = koinViewModel<HostCreateViewModel>(key = "host-create:$routeKey") {
        parametersOf(
            HostCreateViewModelArgs(
                hostId = arg.hostId,
                defaultHostName = defaultHostName,
                kind = HostKind.fromRouteValue(arg.kind),
                sourceId = arg.sourceId,
                legacyVersionName = arg.legacyVersionName,
                displayName = arg.displayName,
                legacyMcVersion = arg.legacyMcVersion,
            )
        )
    }
    val state by viewModel.uiState.collectAsState()
    var showRules by remember { mutableStateOf(false) }
    var returnSuccessMessage by remember { mutableStateOf<String?>(null) }
    var staySuccessMessage by remember { mutableStateOf<String?>(null) }
    var showUpdateConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var resetName by remember { mutableStateOf("") }
    var showCustomLevelTypeDialog by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HostCreateEvent.LegacyCreateSubmitted -> {
                    returnSuccessMessage = event.message
                }
                is HostCreateEvent.EditSaved -> {
                    returnSuccessMessage = event.message
                }
                is HostCreateEvent.HostPackUpdateSubmitted -> staySuccessMessage = event.message
                is HostCreateEvent.WorldReset -> staySuccessMessage = event.message
            }
        }
    }

    if (showCustomLevelTypeDialog) {
        AlertDialog(
            onDismissRequest = { showCustomLevelTypeDialog = false },
            title = { Text("自定义地形") },
            text = {
                OutlinedTextField(
                    value = state.customLevelTypeText,
                    onValueChange = viewModel::updateCustomLevelTypeText,
                    singleLine = true,
                    isError = state.customLevelTypeError != null,
                    label = { Text("level type") },
                    supportingText = { state.customLevelTypeError?.let { Text(it) } }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (viewModel.applyCustomLevelType()) {
                            showCustomLevelTypeDialog = false
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelCustomLevelType()
                        showCustomLevelTypeDialog = false
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showResetConfirm) {
        val currentHost = state.editHost
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("重置存档") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("这会删除房间存档且不可恢复。下次启动时将生成新存档。请输入房间名称确认。")
                    OutlinedTextField(
                        value = resetName,
                        onValueChange = { resetName = it },
                        label = { Text(currentHost?.name ?: "") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = currentHost != null && resetName == currentHost.name,
                    onClick = {
                        showResetConfirm = false
                        resetName = ""
                        viewModel.resetWorld()
                    },
                ) { Text("重置") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("取消") } },
        )
    }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.MEDIUM) {
            TitleRow(state.title, onBack) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                if (state.editHost != null) {
                    if (state.editHost?.version == 2) {
                        CircleIconButton(
                            icon = "\uDB85\uDC5E",
                            label = "重置存档",
                            bgColor = MaterialTheme.colorScheme.error,
                            showText = true,
                        ) {
                            resetName = ""
                            showResetConfirm = true
                        }
                    }
                    CircleIconButton(
                        icon = "\uDB80\uDFD5",
                        label = "更新整合包",
                        showText = true,
                    ) {
                        showUpdateConfirm = true
                    }
                }
                CircleIconButton("\uDB82\uDE50","提交", bgColor = themeNow.primary) {
                    viewModel.submit()
                }
            }
            ScrollableContentBody {
                Text("${state.selectedPackTitle} v${state.selectedVersionName}")
                Space8h()

            /*if (false) { // Host2 creation is disabled for this release.
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("整合包：${state.selectedPackTitle}", fontWeight = FontWeight.Bold)
                            if (state.isLegacyCreate && state.selectedVersionName.isNotBlank()) {
                                Text("版本：${state.selectedVersionName}", color = themeNow.onSurfaceVariant)
                            }

                            OutlinedTextField(
                                label = { Text("房间名称") },
                                value = state.hostName,
                                onValueChange = viewModel::updateHostName,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                label = { Text("简介") },
                                value = state.intro,
                                onValueChange = viewModel::updateIntro,
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            RowV {
                                Checkbox(state.whitelist, viewModel::updateWhitelist)
                                SimpleTooltip("仅限受邀玩家游玩") {
                                    Text("不允许陌生人游玩此房间")
                                }
                            }
                        }
                    } else */

                        Column(modifier = Modifier.fillMaxWidth()) {
                        if (state.isLegacyCreate) {
                            Space8h()
                        }
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val compactTopLayout = maxWidth < 1280.dp
                            val basicSettingSectionModifier = if (compactTopLayout) {
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier.widthIn(min = 320.dp, max = 460.dp)
                            }
                            val gameplaySettingSectionModifier = if (compactTopLayout) {
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier.widthIn(min = 520.dp, max = 980.dp)
                            }
                            FlowRow(
                                modifier = basicSettingSectionModifier,
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                maxItemsInEachRow = if (compactTopLayout) 1 else 3
                            ) {
                                Column(
                                    modifier = basicSettingSectionModifier,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FlowRowV(
                                        modifier = basicSettingSectionModifier,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedTextField(
                                            label = { Text("房间名称") },
                                            value = state.hostName,
                                            onValueChange = viewModel::updateHostName,
                                            singleLine = true
                                        )
                                        RowV {

                                            Checkbox(state.whitelist, viewModel::updateWhitelist)
                                            SimpleTooltip("仅限受邀玩家游玩") {
                                                Text("不允许陌生人游玩此房间")
                                            }
                                        }
                                        RowV {

                                            Checkbox(state.allowCheats, viewModel::updateAllowCheats)
                                            Text("允许作弊")
                                        }
                                        Space8w()
                                        Button(
                                            onClick = { showRules = true },
                                            enabled = !state.loading,
                                        ) {
                                            Text("修改游戏规则(${state.gameRules.size})")
                                        }


                                    }
                                }

                                Column(
                                    modifier = gameplaySettingSectionModifier,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                        val compactGameplayLayout = maxWidth < 960.dp
                                        val optionSectionModifier = if (compactGameplayLayout) {
                                            Modifier.fillMaxWidth()
                                        } else {
                                            Modifier.widthIn(min = 220.dp, max = 340.dp)
                                        }
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            maxItemsInEachRow = if (compactGameplayLayout) 1 else 3
                                        ) {
                                            Column(modifier = optionSectionModifier) {
                                                Text("难度", fontWeight = FontWeight.Bold)
                                                Space8h()
                                                Row(
                                                    modifier = Modifier
                                                        .horizontalScroll(rememberScrollState())
                                                        .padding(horizontal = 2.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    ImageCard(
                                                        title = "和平",
                                                        iconPath = "assets/icons/difficulty_peaceful.avif",
                                                        selected = state.difficulty == 0,
                                                        onClick = { viewModel.updateDifficulty(0) }
                                                    )
                                                    ImageCard(
                                                        title = "简单",
                                                        iconPath = "assets/icons/difficulty_easy.avif",
                                                        selected = state.difficulty == 1,
                                                        onClick = { viewModel.updateDifficulty(1) }
                                                    )
                                                    ImageCard(
                                                        title = "普通",
                                                        iconPath = "assets/icons/difficulty_normal.avif",
                                                        selected = state.difficulty == 2,
                                                        onClick = { viewModel.updateDifficulty(2) }
                                                    )
                                                    ImageCard(
                                                        title = "困难",
                                                        iconPath = "assets/icons/difficulty_hard.avif",
                                                        selected = state.difficulty == 3,
                                                        onClick = { viewModel.updateDifficulty(3) }
                                                    )
                                                }
                                            }
                                            Column(modifier = optionSectionModifier) {
                                                Text("模式", fontWeight = FontWeight.Bold)
                                                Space8h()
                                                Row(
                                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    ImageCard(
                                                        title = "生存",
                                                        iconPath = "assets/icons/gamemode_survival.avif",
                                                        selected = state.gameMode == 0,
                                                        onClick = { viewModel.updateGameMode(0) }
                                                    )
                                                    ImageCard(
                                                        title = "创造",
                                                        iconPath = "assets/icons/gamemode_creative.avif",
                                                        selected = state.gameMode == 1,
                                                        onClick = { viewModel.updateGameMode(1) }
                                                    )
                                                }
                                            }
                                            Column(modifier = optionSectionModifier) {
                                                Text("地形", fontWeight = FontWeight.Bold)
                                                Space8h()
                                                Row(
                                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    ImageCard(
                                                        title = "普通",
                                                        iconPath = "assets/icons/worldtype_normal.avif",
                                                        selected = state.levelChoice == 0,
                                                        onClick = { viewModel.selectLevelChoice(0) }
                                                    )
                                                    ImageCard(
                                                        title = "超平坦",
                                                        iconPath = "assets/icons/worldtype_flat.avif",
                                                        selected = state.levelChoice == 1,
                                                        onClick = { viewModel.selectLevelChoice(1) }
                                                    )
                                                    ImageCard(
                                                        title = "空岛",
                                                        iconPath = "assets/icons/worldtype_skyblock.avif",
                                                        selected = state.levelChoice == 2,
                                                        onClick = { viewModel.selectLevelChoice(2) }
                                                    )
                                                    ImageCard(
                                                        title = "自定义",
                                                        iconPath = "assets/icons/worldtype_normal.avif",
                                                        selected = state.levelChoice == 3,
                                                        onClick = {
                                                            viewModel.beginCustomLevelType()
                                                            showCustomLevelTypeDialog = true
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

            }
            Text(
                "注意，一个月没人玩房间就自动删了",
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 12.dp, bottom = 8.dp),
            )
        }
    }

    if (showRules) {
        GameRuleModal(
            show = true,
            initialOverrideRules = state.gameRules,
            onSave = viewModel::updateGameRules,
            onClose = { showRules = false },
        )
    }

    if (showUpdateConfirm) {
        val currentHost = state.editHost
        ConfirmDialog(
            title = "确认更新",
            message = "将更新房间当前的整合包《${currentHost?.modpack?.name.orEmpty()}》到最新版本。所有修改过的配置都会丢失。",
            onConfirm = {
                showUpdateConfirm = false
                viewModel.requestHostPackUpdate()
            },
            onDismiss = { showUpdateConfirm = false }
        )
    }

    when {
        state.errorMessage != null -> AlertErr(state.errorMessage!!, viewModel::clearErrorMessage)
        state.statusMessage != null -> AlertErr(state.statusMessage!!, viewModel::clearStatusMessage)
        returnSuccessMessage != null -> AlertOk(returnSuccessMessage!!) {
            returnSuccessMessage = null
            onBack()
        }
        staySuccessMessage != null -> AlertOk(staySuccessMessage!!) {
            staySuccessMessage = null
        }
    }
}
