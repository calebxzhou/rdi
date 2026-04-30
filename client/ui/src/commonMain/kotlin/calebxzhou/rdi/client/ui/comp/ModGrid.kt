package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.model.UiMod
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.RowV
import calebxzhou.rdi.client.ui.Space8w
import calebxzhou.rdi.common.model.Mod

/**
 * calebxzhou @ 2026-04-02 13:27
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var modSearch by remember { mutableStateOf("") }
    var showSearchBox by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val sortedMods = remember(mods) {
        mods.sortedWith(
            compareBy(
                String.CASE_INSENSITIVE_ORDER
            ) { mod ->
                mod.displayName
            }
        )
    }
    val filteredMods = remember(sortedMods, modSearch) {
        val query = modSearch.trim().lowercase()
        if (query.isBlank()) sortedMods
        else sortedMods.filter { mod ->
            mod.searchText.contains(query)
        }
    }
    val selectedBackground = Color(243, 236, 255)
    val unselectedFallbackBackground = Color(255, 255, 255, 235)
    Box(modifier = modifier.fillMaxWidth()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(320.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                top = if (showSearchBox) 56.dp else 8.dp,
                end = 18.dp,
                bottom = 12.dp
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (filteredMods.isEmpty()) {
                item(key = "mod-grid-empty") {
                    Text(
                        text = emptyText,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
            items(filteredMods, key = { it.key }) { mod ->
                val card = mod.card
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
                if (card != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected) selectedBackground else Color.Transparent,
                                RoundedCornerShape(18.dp)
                            )
                            .padding(2.dp)
                            .then(clickableModifier)
                            .then(contextMenuModifier)
                    ) {
                        card.ModCard(
                            modifier = Modifier.fillMaxWidth(),
                            currentSide = mod.side,
                            onSideChange = onSideChange?.let { callback ->
                                { nextSide -> callback(mod, nextSide) }
                            }
                        )
                        ModGridContextMenu(
                            expanded = showContextMenu,
                            onDismissRequest = { showContextMenu = false },
                            onCopyFileName = {
                                clipboardManager.setText(AnnotatedString(mod.mod.fileName))
                                showContextMenu = false
                            }
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected) selectedBackground else unselectedFallbackBackground,
                                RoundedCornerShape(16.dp)
                            )
                            .then(clickableModifier)
                            .then(contextMenuModifier)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = mod.displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialColor.GRAY_900.color
                            )
                            if (!mod.slug.equals(mod.displayName, ignoreCase = true)) {
                                Text(
                                    text = mod.slug,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialColor.BLUE_600.color
                                )
                            }
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
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(12.dp)
                .padding(bottom = 12.dp)
        ) {
            PlatformVerticalScrollbar(
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
            RowV(horizontalArrangement = Arrangement.End) {
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
                CircleIconButton(
                    icon = if (showSearchBox) "\uF00D" else "\uF002",
                    tooltip = if (showSearchBox) "隐藏搜索" else "显示搜索",
                    bgColor = MaterialColor.BLUE_900.color,
                    size = 32,
                    showText = false
                ) {
                    if (showSearchBox) {
                        showSearchBox = false
                        modSearch = ""
                    } else {
                        showSearchBox = true
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
        DropdownMenuItem(onClick = onCopyFileName) {
            Text("复制Mod文件名")
        }
    }
}
