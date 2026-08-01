package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzau.rdi.client.ui.CursorPositionBox
import calebxzau.rdi.client.ui.DEFAULT_HOST_ICON
import calebxzau.rdi.client.ui.OffsetFirstItemUnderCursor
import calebxzau.rdi.client.ui.RDropdownMenuItem
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.baseShapeRadius
import calebxzau.rdi.client.ui.baseRoundCornerShape
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.isDav
import calebxzhou.rdi.model.Role

/**
 * calebxzhou @ 2026-01-14 21:48
 */

@Composable
fun Host.BriefVo.HostCard(
    modifier: Modifier = Modifier,
    miniMode: Boolean = false,
    selected: Boolean = false,
    onClick: ((Host.BriefVo) -> Unit)? = null,
    onDirectClick: ((Host.BriefVo) -> Unit)? = null,
    onPlay: ((Host.BriefVo) -> Unit)? = null,
    onOpenMembers: ((Host.BriefVo) -> Unit)? = null,
    onOpenMods: ((Host.BriefVo) -> Unit)? = null,
    onOpenFiles: ((Host.BriefVo) -> Unit)? = null,
    onOpenBackend: ((Host.BriefVo) -> Unit)? = null,
    onOpenSettings: ((Host.BriefVo) -> Unit)? = null,
    onDelete: ((Host.BriefVo) -> Unit)? = null,
    playEnabled: Boolean = true,
    playLoading: Boolean = false,
    allowUnplayableClick: Boolean = false
) {
    val isClickable = (miniMode || playable || allowUnplayableClick) &&
        (onClick != null || onDirectClick != null)
    var menuExpanded by remember { mutableStateOf(false) }
    val cardModifier = if (isClickable) {
        modifier
            .clip(baseRoundCornerShape)
            .clickable {
                when {
                    miniMode -> onClick?.invoke(this)
                    onDirectClick != null -> onDirectClick(this)
                    else -> menuExpanded = true
                }
            }
    } else {
        modifier
    }
    val isDav = loggedAccount.isDav
    val isOwner = ownerId == loggedAccount._id || role == Role.OWNER || isDav
    val canManage = role == Role.OWNER || role == Role.ADMIN || isDav
    val canUseMemberFeatures = isMember || isOwner || isDav

    if (miniMode) {
        val miniContentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        Surface(
            modifier = cardModifier.fillMaxWidth(),
            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = miniContentColor,
            shape = baseRoundCornerShape,
            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            tonalElevation = if (selected) 2.dp else 0.dp
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 350.dp)
                    .padding(horizontal = 5.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HostIcon(size = 28)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = name.ifBlank { "未命名房间" },
                        color = miniContentColor,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$modpackName $packVer",
                        style = MaterialTheme.typography.labelMedium,
                        color = miniContentColor.copy(alpha = 0.76f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        return
    }

    CursorPositionBox(
        cursorContent = {
            if (onDirectClick == null) {
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    offset = OffsetFirstItemUnderCursor
                ) {
                if (onPlay != null) {
                    RDropdownMenuItem(
                        text = if (playLoading) "启动中..." else "开玩",
                        icon = "\uF04B",
                        enabled = playable && playEnabled && !playLoading,
                        onClick = {
                            menuExpanded = false
                            onPlay(this@HostCard)
                        }
                    )
                }
                if (onClick != null) {
                    RDropdownMenuItem(
                        text = "详情",
                        icon = "\uF05A",
                        onClick = {
                            menuExpanded = false
                            onClick(this@HostCard)
                        }
                    )
                }
                if (canUseMemberFeatures && onOpenMembers != null) {
                    RDropdownMenuItem(
                        text = "成员",
                        icon = "\uF0C0",
                        onClick = {
                            menuExpanded = false
                            onOpenMembers(this@HostCard)
                        }
                    )
                }
                if (canUseMemberFeatures && onOpenMods != null) {
                    RDropdownMenuItem(
                        text = "模组",
                        icon = "\uDB85\uDCD3",
                        onClick = {
                            menuExpanded = false
                            onOpenMods(this@HostCard)
                        }
                    )
                }
                if (canManage && onOpenFiles != null) {
                    RDropdownMenuItem(
                        text = "文件",
                        icon = "\uF07C",
                        onClick = {
                            menuExpanded = false
                            onOpenFiles(this@HostCard)
                        }
                    )
                }
                if (canUseMemberFeatures && onOpenBackend != null) {
                    RDropdownMenuItem(
                        text = "后台",
                        icon = "\uDB80\uDD8D",
                        onClick = {
                            menuExpanded = false
                            onOpenBackend(this@HostCard)
                        }
                    )
                }
                if (canManage && onOpenSettings != null) {
                    RDropdownMenuItem(
                        text = "设置",
                        icon = "\uEAF8",
                        onClick = {
                            menuExpanded = false
                            onOpenSettings(this@HostCard)
                        }
                    )
                }
                if (isOwner && onDelete != null) {
                    HorizontalDivider()
                    RDropdownMenuItem(
                        text = "删除",
                        icon = "\uEA81",
                        danger = true,
                        onClick = {
                            menuExpanded = false
                            onDelete(this@HostCard)
                        }
                    )
                }
                }
            }
        }
    ) {
        Surface(
            modifier = cardModifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = baseRoundCornerShape,
            tonalElevation = 1.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .alpha(if (playable) 1f else 0.45f)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HostIcon(size = 64)
                    Space8w()
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$modpackName $packVer",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HeadButton(
                                ownerId,
                                avatarSize = 18.dp,
                                nameFontSize = 14.sp,
                                showName = true
                            )
                            Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            onlinePlayerIds.forEach {
                                HeadButton(
                                    it,
                                    avatarSize = 18.dp,
                                    showName = false
                                )
                            }
                        }
                    }
                }

                if (playLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.TopEnd).size(22.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun Host.BriefVo.HostIcon(
    size: Int,
    corner: Int = baseShapeRadius
) {
    val iconShape = RoundedCornerShape(corner.dp)
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(iconShape)
            .background(MaterialTheme.colorScheme.surfaceVariant, iconShape),
        contentAlignment = Alignment.Center
    ) {
        val iconUrl = iconUrl?.takeIf { it.isNotBlank() }
        if (iconUrl != null) {
            HttpImage(
                imgUrl = iconUrl,
                modifier = Modifier.fillMaxSize(),
                contentDescription = "Host Icon",
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                bitmap = DEFAULT_HOST_ICON,
                contentDescription = "Host Icon",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
