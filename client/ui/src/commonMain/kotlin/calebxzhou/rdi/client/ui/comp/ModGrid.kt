package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.model.UiMod
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.common.model.Mod

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
    var modSearch by remember { mutableStateOf("") }
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
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = modSearch,
            onValueChange = { modSearch = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
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
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(320.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 18.dp, bottom = 12.dp),
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
                    val clickableModifier = if (onModClick != null) {
                        Modifier.clickable { onModClick(mod) }
                    } else {
                        Modifier
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
                        ) {
                            card.ModCard(
                                modifier = Modifier.fillMaxWidth(),
                                currentSide = mod.side,
                                onSideChange = onSideChange?.let { callback ->
                                    { nextSide -> callback(mod, nextSide) }
                                }
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selected) selectedBackground else unselectedFallbackBackground,
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(12.dp)
                                .then(clickableModifier),
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
        }
    }
}
