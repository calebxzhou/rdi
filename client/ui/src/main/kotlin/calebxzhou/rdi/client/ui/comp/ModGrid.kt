package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.model.UiMod
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.SimpleTooltip
import calebxzau.rdi.client.ui.Space8w
import calebxzhou.rdi.common.model.Mod
import java.util.Locale

/**
 * calebxzhou @ 2026-04-02 13:27
 */
@Composable
fun ModGrid(
    mods: List<UiMod>,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    emptyText: String = "没有找到mod",
    selectedKeys: Set<String> = emptySet(),
    onModClick: ((UiMod) -> Unit)? = null,
    onSideChange: ((UiMod, Mod.Side) -> Unit)? = null
) {
    var modSearch by rememberSaveable { mutableStateOf("") }
    var showSearchBox by rememberSaveable { mutableStateOf(false) }
    var iconOnly by rememberSaveable { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val sortedMods = remember(mods) {
        mods.sortedWith(
            compareBy(
                String.CASE_INSENSITIVE_ORDER
            ) { mod ->
                mod.primaryName
            }
        )
    }
    val filteredMods = remember(sortedMods, modSearch) {
        val query = modSearch.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) sortedMods
        else sortedMods.filter { mod ->
            mod.searchText.contains(query)
        }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(if (iconOnly) 64.dp else 320.dp),
            horizontalArrangement = Arrangement.spacedBy(if (iconOnly) 4.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (iconOnly) 8.dp else 12.dp),
            contentPadding = PaddingValues(
                top = if (showSearchBox) 56.dp else 8.dp,
                end = 18.dp,
                bottom = 12.dp
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (filteredMods.isEmpty()) {
                item(key = "mod-grid-empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = emptyText,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
            items(filteredMods, key = { it.key }) { mod ->
                val selected = mod.key in selectedKeys
                var showContextMenu by remember(mod.key) { mutableStateOf(false) }
                val clickableModifier = if (onModClick != null) {
                    Modifier.clickable { onModClick(mod) }
                } else {
                    Modifier
                }
                val contextMenuModifier = Modifier.pointerInput(mod.key) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.buttons.isSecondaryPressed) {
                                showContextMenu = true
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            RoundedCornerShape(18.dp)
                        )
                        .padding(2.dp)
                        .then(clickableModifier)
                        .then(contextMenuModifier)
                ) {
                    if (iconOnly) {
                        SimpleTooltip(mod.primaryName) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                UiModIcon(
                                    mod = mod,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                        }
                    } else {
                        UiModCard(
                            mod = mod,
                            modifier = Modifier.fillMaxWidth(),
                            onSideChange = onSideChange?.let { callback ->
                                { nextSide -> callback(mod, nextSide) }
                            }
                        )
                    }
                    ModGridContextMenu(
                        expanded = showContextMenu,
                        onDismissRequest = { showContextMenu = false },
                        onCopyFileName = {
                            clipboardManager.setText(AnnotatedString(mod.mod.fileName))
                            showContextMenu = false
                        }
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(12.dp)
                .padding(bottom = 12.dp)
        ) {
            RVerticalScrollbar(
                gridState = gridState,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                if (showSearchBox) {
                    OutlinedTextField(
                        value = modSearch,
                        onValueChange = { modSearch = it },
                        modifier = Modifier.width(220.dp),
                        label = { Text("搜索mod") },
                        singleLine = true,
                        maxLines = 1,
                        placeholder = { Text("搜索mod..") },
                        trailingIcon = {
                            if (modSearch.isNotBlank()) {
                                IconButton(onClick = { modSearch = "" }) {
                                    Text("✕")
                                }
                            }
                        }
                    )
                    Space8w()
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    CircleIconButton(
                        icon = if (showSearchBox) "\uF00D" else "\uF002",
                        tooltip = if (showSearchBox) "隐藏搜索" else "显示搜索",
                        bgColor = MaterialTheme.colorScheme.primary,
                        size = 32.dp,
                        showText = false
                    ) {
                        if (showSearchBox) {
                            showSearchBox = false
                            modSearch = ""
                        } else {
                            showSearchBox = true
                        }
                    }
                    CircleIconButton(
                        icon = if (iconOnly) "\uF03A" else "\uF00A",
                        tooltip = if (iconOnly) "卡片模式" else "仅图标模式",
                        bgColor = MaterialTheme.colorScheme.primary,
                        size = 32.dp,
                        showText = false
                    ) {
                        iconOnly = !iconOnly
                    }
                }
            }
        }
    }
}

@Composable
private fun ModGridContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onCopyFileName: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = { Text("复制Mod文件名") },
            onClick = onCopyFileName
        )
    }
}
