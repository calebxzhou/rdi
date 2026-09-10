package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.CursorPositionBox
import calebxzau.rdi.client.ui.comp.RCard
import calebxzau.rdi.client.ui.OffsetFirstItemUnderCursor
import calebxzau.rdi.client.ui.RDropdownMenuItem
import calebxzau.rdi.common.model.BaseWorld
import calebxzhou.rdi.client.ui.comp.HeadButton
import calebxzhou.rdi.common.util.objectId

@Composable
fun BaseWorldCard(
    world: BaseWorld,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    menuExpanded: Boolean = false,
    onClick: () -> Unit,
    onDismissMenu: () -> Unit = {},
    onDetails: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    deleteEnabled: Boolean = true,
) {
    val card = @Composable {
        RCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            enabled = enabled,
            colors = CardDefaults.cardColors(
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            ) {
                Text(world.name)
                HeadButton(world.ownerId.objectId, avatarSize = 16.dp)
            }
        }
    }
    if (selectionMode) {
        card()
    } else {
        CursorPositionBox(
            cursorContent = {
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = onDismissMenu,
                    offset = OffsetFirstItemUnderCursor,
                ) {
                    onDetails?.let { RDropdownMenuItem(text = "查看详情", icon = "\uF449", onClick = it) }
                    onRename?.let { RDropdownMenuItem(text = "修改名称", icon = "\uF4CE", onClick = it) }
                    onDelete?.let {
                        RDropdownMenuItem(
                            text = "删除",
                            icon = "\uEA81",
                            danger = true,
                            enabled = deleteEnabled,
                            onClick = it,
                        )
                    }
                }
            },
        ) {
            card()
        }
    }
}
