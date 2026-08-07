package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import calebxzhou.mykotutils.std.javaExePath
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.RColumn
import calebxzau.rdi.client.ui.RRow
import calebxzau.rdi.client.ui.RSwitch
import calebxzau.rdi.client.ui.RTextField
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.Space8h
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.pickLocalDirectory
import calebxzau.rdi.client.ui.viewmodel.ModpackOptionAction
import calebxzau.rdi.client.ui.viewmodel.ModpackOptionViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ModpackOptionScreen(
    versionId: String,
    modpackName: String,
    versionName: String,
    onBack: () -> Unit,
    viewModel: ModpackOptionViewModel = koinViewModel(key = versionId) {
        parametersOf(versionId)
    },
) {
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }
    val globalJavaPath = remember {
        javaExePath.takeIf { Runtime.version().feature() == 25 } ?: ""
    }
    val packTitle = listOf(modpackName, versionName)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .ifBlank { versionId }

    LaunchedEffect(uiState.completedAction) {
        if (uiState.completedAction == ModpackOptionAction.NAVIGATE_BACK) {
            viewModel.clearCompletedAction()
            onBack()
        }
    }

    fun requestBack() {
        if (uiState.dirty) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    val draft = uiState.draft
    val loaded = !uiState.loading
    val controlsEnabled = loaded && !uiState.loadFailed && !uiState.saving

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            TitleRow("$packTitle·设置", ::requestBack) {
                CircleIconButton(
                    icon = "\uF021",
                    label = "恢复默认",
                    enabled = controlsEnabled,
                    onClick = viewModel::resetDefaults,
                )
                CircleIconButton(
                    icon = "\uF0C7",
                    label = "保存",
                    enabled = controlsEnabled,
                    onClick = { viewModel.save() },
                )
            }
            ContentBody {
                if (uiState.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    RColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RRow {
                            Text("指定java", style = MaterialTheme.typography.titleMedium)
                            RSwitch(
                                checked = draft.javaCustomEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.updateDraft(
                                        draft.copy(
                                            javaCustomEnabled = enabled,
                                            javaPath = if (enabled && draft.javaPath.isBlank()) {
                                                globalJavaPath
                                            } else {
                                                draft.javaPath
                                            },
                                        )
                                    )
                                }
                            )
                            if (draft.javaCustomEnabled) {
                                    RTextField(
                                        label = "java路径",
                                        value = draft.javaPath,
                                        modifier = Modifier.width(520.dp),
                                        onValueChange = {
                                            viewModel.updateDraft(draft.copy(javaPath = it))
                                        },
                                    )
                                    CircleIconButton(
                                        icon = "\uE8B7",
                                        label = "浏览",
                                        showText = false,
                                        onClick = {
                                            scope.launch {
                                                pickLocalDirectory("选择JDK目录")?.let {
                                                    viewModel.updateDraft(
                                                        draft.copy(javaPath = it.absolutePath)
                                                    )
                                                }
                                            }
                                        },
                                    )

                            }
                        }

                        RRow {
                            Text("内存限制", style = MaterialTheme.typography.titleMedium)
                            RSwitch(
                                checked = draft.memoryCustomEnabled,
                                onCheckedChange = {
                                    viewModel.updateDraft(draft.copy(memoryCustomEnabled = it))
                                },
                            )
                            if (draft.memoryCustomEnabled) {
                                RTextField(
                                    label = "最大内存MB",
                                    value = draft.maxMemoryText,
                                    modifier = Modifier.width(160.dp),
                                    onValueChange = {
                                        viewModel.updateDraft(draft.copy(maxMemoryText = it))
                                    },
                                )
                                Text(
                                    "总${uiState.totalMemoryMb}MB",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                            }
                        }
                        RRow {
                            Text("JDWP debugger", style = MaterialTheme.typography.titleMedium)
                            RSwitch(
                                checked = draft.jdwpEnabled,
                                onCheckedChange = {
                                    viewModel.updateDraft(draft.copy(jdwpEnabled = it))
                                },
                            )
                            if (draft.jdwpEnabled) {
                                RTextField(
                                    label = "param",
                                    value = draft.jdwpParam,
                                    onValueChange = {
                                        viewModel.updateDraft(draft.copy(jdwpParam = it))
                                    },
                                )
                            }
                        }
                        RRow {
                            Text("xtra jvm param", style = MaterialTheme.typography.titleMedium)
                            RTextField(
                                label = "1line1param",
                                value = draft.customJvmParams,
                                modifier = Modifier
                                    .width(620.dp)
                                    .height(140.dp),
                                singleLine = false,
                                onValueChange = {
                                    viewModel.updateDraft(draft.copy(customJvmParams = it))
                                },
                            )
                        }
                        RRow {
                            Text("禁用Forgeguard", style = MaterialTheme.typography.titleMedium)
                            RSwitch(
                                checked = draft.forgeguardDisabled,
                                onCheckedChange = {
                                    viewModel.updateDraft(draft.copy(forgeguardDisabled = it))
                                },
                            )
                        }
                        uiState.errorMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        Space8h()
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("未保存修改") },
            text = { Text("要保存修改后返回吗？") },
            confirmButton = {
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = {
                        showDiscardDialog = false
                        viewModel.save(returnAfterSave = true)
                    }) {
                        Text("保存并返回")
                    }
                    TextButton(onClick = {
                        showDiscardDialog = false
                        onBack()
                    }) {
                        Text("放弃修改")
                    }
                    TextButton(onClick = { showDiscardDialog = false }) {
                        Text("取消")
                    }
                }
            }
        )
    }
}
