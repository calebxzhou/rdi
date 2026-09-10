package calebxzau.rdi.client.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.RRow
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.ScrollableContentBody
import calebxzau.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.auth.AccountSessionStore
import calebxzhou.rdi.client.ui.comp.ImageCard
import calebxzau.rdi.client.ui.viewmodel.BaseWorldLevelTypeChoice
import calebxzau.rdi.client.ui.viewmodel.BaseWorldUploadViewModel

@Composable
fun BaseWorldUploadScreen(
    onBack: () -> Unit,
    onUploadSubmitted: (String) -> Unit,
    viewModel: BaseWorldUploadViewModel = viewModel { BaseWorldUploadViewModel() },
) {
    val account by AccountSessionStore.account.collectAsStateWithLifecycle()
    val ownerId = remember(account._id) { account._id.toHexString() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCustomLevelTypeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(ownerId) {
        viewModel.setOwner(ownerId)
    }

    if (showCustomLevelTypeDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.cancelCustomLevelType()
                showCustomLevelTypeDialog = false
            },
            title = { Text("自定义地形") },
            text = {
                OutlinedTextField(
                    value = state.customLevelTypeText,
                    onValueChange = viewModel::updateCustomLevelTypeText,
                    singleLine = true,
                    isError = state.customLevelTypeError != null,
                    label = { Text("地形ID") },
                    supportingText = { state.customLevelTypeError?.let { Text(it) } },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (viewModel.applyCustomLevelType()) {
                            showCustomLevelTypeDialog = false
                        }
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelCustomLevelType()
                        showCustomLevelTypeDialog = false
                    },
                ) { Text("取消") }
            },
        )
    }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.SMALL) {
            TitleRow(
                title = "创建地图模板",
                onBack = onBack,
            ) {
                CircleIconButton(
                    icon = "\uF058",
                    label = "创建地图模板",
                    enabled = state.canSubmit,
                    onClick = {
                        viewModel.submit(ownerId)?.let(onUploadSubmitted)
                    },
                )
            }
            ScrollableContentBody(
                state = rememberScrollState(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircleIconButton(
                    icon = "\uF07C",
                    label = if (state.selectingDirectory) "正在选择..." else "选择已有地图文件夹",
                    enabled = !state.selectingDirectory && !state.submitting,
                    onClick = viewModel::selectDirectory,
                )
                state.directory?.let { directory ->
                    Text(
                        "已选择：${directory.absolutePath}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.directory == null) {
                    Text(
                        "未选择地图文件夹，将在房间首次启动时生成地图。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RRow {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::updateName,
                        label = { Text("名称") },
                        singleLine = true,
                        enabled = !state.submitting,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("地形类型", fontWeight = FontWeight.Bold)
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        ImageCard(
                            title = "普通",
                            iconPath = "assets/icons/worldtype_normal.avif",
                            selected = state.levelTypeChoice == BaseWorldLevelTypeChoice.Normal,
                            onClick = {
                                if (!state.submitting) viewModel.selectLevelType(BaseWorldLevelTypeChoice.Normal)
                            },
                        )
                        ImageCard(
                            title = "平坦",
                            iconPath = "assets/icons/worldtype_flat.avif",
                            selected = state.levelTypeChoice == BaseWorldLevelTypeChoice.Flat,
                            onClick = {
                                if (!state.submitting) viewModel.selectLevelType(BaseWorldLevelTypeChoice.Flat)
                            },
                        )
                        ImageCard(
                            title = "空岛",
                            iconPath = "assets/icons/worldtype_skyblock.avif",
                            selected = state.levelTypeChoice == BaseWorldLevelTypeChoice.Skyblock,
                            onClick = {
                                if (!state.submitting) viewModel.selectLevelType(BaseWorldLevelTypeChoice.Skyblock)
                            },
                        )
                        ImageCard(
                            title = "自定义",
                            iconPath = "assets/icons/worldtype_normal.avif",
                            selected = state.levelTypeChoice == BaseWorldLevelTypeChoice.Custom,
                            onClick = {
                                if (!state.submitting) {
                                    viewModel.beginCustomLevelType()
                                    showCustomLevelTypeDialog = true
                                }
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = state.generatorSettings,
                    onValueChange = viewModel::updateGeneratorSettings,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("生成器json 可选") },
                    minLines = 4,
                    enabled = !state.submitting,
                )
                state.errorMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                if (state.submitting) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
