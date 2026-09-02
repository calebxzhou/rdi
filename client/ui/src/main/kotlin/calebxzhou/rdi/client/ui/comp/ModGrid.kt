package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import calebxzhou.rdi.client.model.UiMod
import calebxzhou.rdi.client.service.analyzeModIconColors
import calebxzhou.rdi.client.service.sortModsByIconColor
import calebxzhou.rdi.common.model.Mod
import calebxzau.rdi.client.lgr

sealed interface ModGridDragEvent {
    data class Start(val mod: UiMod, val pointerInWindow: Offset) : ModGridDragEvent
    data class Move(val pointerInWindow: Offset) : ModGridDragEvent
    data class End(val pointerInWindow: Offset) : ModGridDragEvent
    data object Cancel : ModGridDragEvent
}

@Composable
fun ModGrid(
    mods: List<UiMod>,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    emptyText: String = "无mod",
    selectedKeys: Set<String> = emptySet(),
    draggedKey: String? = null,
    onModClick: ((UiMod) -> Unit)? = null,
    onSideChange: ((UiMod, Mod.Side) -> Unit)? = null,
    onDragEvent: ((ModGridDragEvent) -> Unit)? = null,
    initialIconOnly: Boolean = false,
) {
    val named = remember(mods) { mods.sortedBy { it.primaryName.lowercase() } }
    var colorSorted by remember(mods) { mutableStateOf<List<UiMod>?>(null) }
    var iconOnly by remember(initialIconOnly) { mutableStateOf(initialIconOnly) }
    val currentMods by rememberUpdatedState(mods)
    LaunchedEffect(mods, iconOnly) {
        colorSorted = null
        if (!iconOnly) return@LaunchedEffect
        analyzeModIconColors(named)
            .onFailure { lgr.warn(it) { "ModGrid图标颜色分析失败，将保持名称顺序" } }
            .onSuccess { colors ->
                if (currentMods === mods) colorSorted = sortModsByIconColor(named, colors)
            }
    }
    val itemsByKey = remember(mods) { mods.associate { it.key to ContentGridItem.ModItem(it) } }
    val items = remember(mods) { mods.map { itemsByKey.getValue(it.key) } }
    ContentGrid(
        items = items,
        modifier = modifier,
        gridState = gridState,
        emptyText = emptyText,
        onItemClick = onModClick?.let { callback ->
            { item -> callback((item as ContentGridItem.ModItem).mod) }
        },
        onModSideChange = onSideChange,
        onDragEvent = onDragEvent?.let { callback ->
            { event ->
                callback(
                    when (event) {
                        is ContentGridDragEvent.Start -> ModGridDragEvent.Start(
                            (event.item as ContentGridItem.ModItem).mod,
                            event.pointerInWindow,
                        )
                        is ContentGridDragEvent.Move -> ModGridDragEvent.Move(event.pointerInWindow)
                        is ContentGridDragEvent.End -> ModGridDragEvent.End(event.pointerInWindow)
                        ContentGridDragEvent.Cancel -> ModGridDragEvent.Cancel
                    }
                )
            }
        },
        draggedKey = draggedKey,
        initialIconOnly = initialIconOnly,
        showTypeFilters = false,
        sortItems = { _, iconOnly ->
            val ordered = if (iconOnly) colorSorted ?: named else named
            ordered.map { itemsByKey.getValue(it.key) }
        },
        legacySelectedKeys = selectedKeys,
        legacySelectedHighlight = true,
        onIconOnlyChange = { iconOnly = it },
    )
}
