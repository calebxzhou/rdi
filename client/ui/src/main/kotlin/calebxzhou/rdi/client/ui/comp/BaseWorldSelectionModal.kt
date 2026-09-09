package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import calebxzau.rdi.client.ui.AlertErr
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.themeNow
import calebxzau.rdi.common.model.BaseWorld
import java.util.UUID

@Composable
fun BaseWorldSelectionModal(
    worlds: List<BaseWorld>,
    loading: Boolean,
    errorMessage: String?,
    initialSelectedId: UUID?,
    submitting: Boolean,
    onRetry: () -> Unit,
    onClearError: () -> Unit,
    onConfirm: (UUID) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftId by remember(initialSelectedId) { mutableStateOf(initialSelectedId) }
    val selectedIsAvailable = draftId != null && worlds.any { it.id == draftId }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .zIndex(2f)
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.9f)
                .height(600.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
        ) {
            Column {
                TitleRow("选择地图模板", onDismiss) {
                    CircleIconButton(
                        icon = "\uDB82\uDE50",
                        label = "确定",
                        bgColor = themeNow.primary,
                        enabled = !submitting && selectedIsAvailable,
                    ) {
                        draftId?.let(onConfirm)
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when {
                        loading -> Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                        errorMessage != null -> Column(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            AlertErr(errorMessage, onClearError)
                            TextButton(onClick = onRetry, enabled = !submitting) { Text("重试") }
                        }
                        worlds.isEmpty() -> Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center,
                        ) { Text("没有可用的地图模板") }
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 220.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(worlds, key = { it.id }) { world ->
                                BaseWorldCard(
                                    world = world,
                                    selectionMode = true,
                                    selected = world.id == draftId,
                                    enabled = !submitting,
                                    onClick = { draftId = world.id },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
