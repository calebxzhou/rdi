package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import calebxzau.rdi.client.ui.AlertErr
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.RRow
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.ScrollableContentBody
import calebxzau.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.comp.HeadButton
import calebxzau.rdi.client.ui.viewmodel.ModpackUploaderManageEvent
import calebxzau.rdi.client.ui.viewmodel.ModpackUploaderManageViewModel
import calebxzau.rdi.client.ui.viewmodel.ModpackUploaderMode
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ModpackUploaderManageScreen(
    modpackId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ModpackUploaderManageViewModel = koinViewModel(key = modpackId) {
        parametersOf(modpackId)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showInviteDialog by remember { mutableStateOf(false) }
    var playerNameOrQq by remember { mutableStateOf("") }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is ModpackUploaderManageEvent.Saved) onSaved()
        }
    }
    state.errorMessage?.let { AlertErr(it, viewModel::clearError) }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.MEDIUM) {
            TitleRow("版本上传权限", onBack) {
                CircleIconButton(
                    icon = "\uF00C",
                    tooltip = "保存",
                    enabled = state.dirty && !state.loading && !state.saving,
                    onClick = viewModel::save,
                )
            }
            when {
                state.loading -> ContentBody {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> ScrollableContentBody {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RRow {

                            PolicyRadioRow(
                                selected = state.mode == ModpackUploaderMode.EVERYONE,
                                title = "所有人",
                            ) { viewModel.setMode(ModpackUploaderMode.EVERYONE) }
                            PolicyRadioRow(
                                selected = state.mode == ModpackUploaderMode.AUTHOR_ONLY,
                                title = "仅自己",
                            ) { viewModel.setMode(ModpackUploaderMode.AUTHOR_ONLY) }
                            PolicyRadioRow(
                                selected = state.mode == ModpackUploaderMode.SELECTED_PLAYERS,
                                title = "指定人",
                            ) { viewModel.setMode(ModpackUploaderMode.SELECTED_PLAYERS) }
                            if (state.mode == ModpackUploaderMode.SELECTED_PLAYERS) {

                                    CircleIconButton(
                                        icon = "\uF067",
                                        label = "添加",
                                        enabled = !state.resolving && !state.saving,
                                    ) {
                                        playerNameOrQq = ""
                                        showInviteDialog = true
                                    }

                                val known = state.uploaders.associateBy { it.id }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    state.selectedIds.forEach { id ->
                                        val account = known[id]
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            if (account == null) {
                                                Column(Modifier.weight(1f)) {
                                                    Text("未知玩家", color = MaterialTheme.colorScheme.onSurface)
                                                    Text(id.toHexString(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            } else {
                                                HeadButton(
                                                    uid = account.id,
                                                    showName = true,
                                                    onClick = null,
                                                )
                                            }
                                            CircleIconButton(
                                                icon = "\uF014",
                                                tooltip = "移除",
                                                bgColor = MaterialTheme.colorScheme.error,
                                            ) { viewModel.remove(id) }
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            }
        }
    }

    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.resolving) showInviteDialog = false },
            title = { Text("添加指定玩家") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = playerNameOrQq,
                        onValueChange = { playerNameOrQq = it },
                        label = { Text("名/QQ") },
                        supportingText = { Text("请输入对方昵称或QQ") },
                        singleLine = true,
                        enabled = !state.resolving,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }, enabled = !state.resolving) { Text("取消") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resolveAndAdd(playerNameOrQq)
                        if (playerNameOrQq.isNotBlank()) showInviteDialog = false
                    },
                    enabled = playerNameOrQq.isNotBlank() && !state.resolving,
                ) { Text(if (state.resolving) "查找中…" else "添加") }
            },
        )
    }
}

@Composable
private fun PolicyRadioRow(
    selected: Boolean,
    title: String,
    onClick: () -> Unit,
) {
    RadioButton(selected = selected, onClick = onClick)
    Text(title, style = MaterialTheme.typography.titleMedium)


}
