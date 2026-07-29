package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import calebxzau.rdi.client.ui.DEFAULT_HOST_ICON
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.baseShapeRadius
import calebxzau.rdi.client.ui.baseRoundCornerShape
import calebxzhou.rdi.common.model.Host

/**
 * calebxzhou @ 2026-01-14 21:48
 */

@Composable
fun Host.BriefVo.HostCard(
    modifier: Modifier = Modifier,
    miniMode: Boolean = false,
    selected: Boolean = false,
    //onClickPlay: ((Host.BriefVo) -> Unit)? = null,
    onClick: ((Host.BriefVo) -> Unit)? = null
) {
    val isClickable = (miniMode || playable) && onClick != null
    val cardModifier = if (isClickable) {
        modifier
            .clip(baseRoundCornerShape)
            .clickable { onClick(this) }
    } else {
        modifier
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    if (miniMode) {
        val miniContentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        Surface(
            modifier = cardModifier
                .fillMaxWidth()
                .hoverable(interactionSource),
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
               /* if (isHovered && onClickPlay != null && playable) {
                    CircleIconButton(
                        icon = "\uF04B",
                        tooltip = "启动MC 玩这个房间",
                        size = 26,
                        contentPadding = PaddingValues(2.dp, 0.dp, 0.dp, 0.dp),
                        bgColor = MaterialTheme.colorScheme.primary,
                        showText = false
                    ) {
                        onClickPlay.invoke(this@HostCard)
                    }
                }*/
            }
        }
        return
    }

    Surface(
        modifier = cardModifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = baseRoundCornerShape,
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
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

           /* if (isHovered && onClickPlay != null && playable) {
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    CircleIconButton(
                        icon = "\uF04B",
                        tooltip = "启动MC 玩这个房间",
                        size = 26,
                        contentPadding = PaddingValues(2.dp, 0.dp, 0.dp, 0.dp),
                        bgColor = MaterialTheme.colorScheme.primary,
                        showText = false
                    ) {
                        onClickPlay.invoke(this@HostCard)
                    }
                }
            }*/
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
