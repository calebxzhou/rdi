package calebxzau.rdi.client.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import calebxzau.rdi.client.ui.AlertErr
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.RRow
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.comp.RCard
import calebxzau.rdi.client.ui.viewmodel.ModpackVersionBaseWorldManageEvent
import calebxzau.rdi.client.ui.viewmodel.ModpackVersionBaseWorldManageViewModel
import calebxzhou.rdi.client.ui.comp.BaseWorldCard
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ModpackVersionBaseWorldManageScreen(
    modpackId: String,
    verName: String,
    onBack: () -> Unit,
    onSaved: () -> Unit = onBack,
    viewModel: ModpackVersionBaseWorldManageViewModel = koinViewModel(key = "$modpackId:$verName") {
        parametersOf(modpackId, verName)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ModpackVersionBaseWorldManageEvent.Saved -> onSaved()
            }
        }
    }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            TitleRow(title = "初始地图模板", onBack = onBack) {
                Text(
                    state.selectedWorld?.name?.let { "已选择 $it" } ?: if (state.selectedWorldUnavailable) {
                        "已选择的地图模板暂不可用"
                    } else {
                        "在下方选择一个指定地图模板"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                CircleIconButton(
                    icon = "\uF00C",
                    label = if (state.saving) "保存中..." else "保存",
                    enabled = state.canSave,
                    onClick = viewModel::save,
                )
            }
            ContentBody {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.selectedWorldUnavailable) {
                        Text(
                            "当前选择的地图模板不可用。再次点击下方的失效模板卡片取消选择，或选择其他地图模板。",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    RRow{
                        Text("建议使用")
                        Switch(
                            checked = state.draftRequired,
                            onCheckedChange = viewModel::setRequired,
                            enabled = !state.loading && !state.saving && state.draftId != null,
                        )
                        Text("强制使用")
                    }

                    when {
                        state.loading -> Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }

                        state.errorMessage != null && !state.dataLoaded -> AlertErr(
                            msg = state.errorMessage!!,
                            onClose = {
                                viewModel.clearError()
                                viewModel.reload()
                            },
                        )

                        state.worlds.isEmpty() && !state.selectedWorldUnavailable -> Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center,
                        ) { Text("暂无可用地图模板") }

                        else -> {
                            state.errorMessage?.let { AlertErr(it, viewModel::clearError) }
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 220.dp),
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (state.selectedWorldUnavailable) {
                                    val unavailableId = state.draftId
                                    if (unavailableId != null) {
                                        item(key = "unavailable-$unavailableId") {
                                            RCard(
                                                modifier = Modifier.fillMaxWidth(),
                                                onClick = { viewModel.select(unavailableId) },
                                                enabled = !state.saving,
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                                ),
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    Text(
                                                        "已失效的地图模板",
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                    )
                                                    Text(
                                                        unavailableId.toString(),
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        style = MaterialTheme.typography.bodySmall,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                items(state.worlds, key = { it.id }) { world ->
                                    BaseWorldCard(
                                        world = world,
                                        selectionMode = true,
                                        selected = world.id == state.draftId,
                                        enabled = !state.saving,
                                        onClick = { viewModel.select(world.id) },
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
