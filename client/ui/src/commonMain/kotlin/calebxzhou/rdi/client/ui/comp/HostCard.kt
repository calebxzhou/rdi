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
import androidx.compose.material.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzhou.rdi.client.ui.DEFAULT_HOST_ICON
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.common.model.Host

/**
 * calebxzhou @ 2026-01-14 21:48
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Host.BriefVo.HostCard(
    modifier: Modifier = Modifier,
    miniMode: Boolean = false,
    selected: Boolean = false,
    onClickPlay: ((Host.BriefVo) -> Unit)? = null,
    onClick: ((Host.BriefVo) -> Unit)? = null
) {
    val isClickable = (miniMode || playable) && onClick != null
    val cardModifier = if (isClickable) {
        modifier.clickable { onClick(this) }
    } else {
        modifier
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    if (miniMode) {
        Surface(
            modifier = cardModifier
                .fillMaxWidth()
                .hoverable(interactionSource),
            color = if (selected) MaterialColor.BLUE_50.color else Color.White,
            shape = RoundedCornerShape(12.dp),
            border = if (selected) BorderStroke(2.dp, MaterialColor.BLUE_700.color) else null,
            elevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 350.dp)
                    .padding(horizontal = 5.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HostIcon(size = 28, corner = 8)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = name.ifBlank { "未命名房间" },
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$modpackName $packVer",
                        style = MaterialTheme.typography.caption,
                        color = MaterialColor.GRAY_500.color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isHovered && onClickPlay != null && playable) {
                    CircleIconButton(
                        icon = "\uF04B",
                        tooltip = "启动MC 玩这个房间",
                        size = 26,
                        contentPadding = PaddingValues(2.dp, 0.dp, 0.dp, 0.dp),
                        bgColor = MaterialColor.GREEN_900.color,
                        showText = false
                    ) {
                        onClickPlay.invoke(this@HostCard)
                    }
                }
            }
        }
        return
    }

    Surface(
        modifier = cardModifier.fillMaxWidth(),
        color = Color(0xFFF9F9FB),
        shape = RoundedCornerShape(16.dp),
        elevation = 1.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .background(Color.Transparent)
                .alpha(if (playable) 1f else 0.45f)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HostIcon(size = 64, corner = 12)
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$modpackName $packVer",
                        style = MaterialTheme.typography.body2,
                        color = MaterialColor.GRAY_500.color,
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
                        Text(" · ")
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

            if (isHovered && onClickPlay != null && playable) {
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    CircleIconButton(
                        icon = "\uF04B",
                        tooltip = "启动MC 玩这个房间",
                        size = 26,
                        contentPadding = PaddingValues(2.dp, 0.dp, 0.dp, 0.dp),
                        bgColor = MaterialColor.GREEN_900.color,
                        showText = false
                    ) {
                        onClickPlay.invoke(this@HostCard)
                    }
                }
            }
        }
    }
}

@Composable
private fun Host.BriefVo.HostIcon(
    size: Int,
    corner: Int
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(MaterialColor.GRAY_200.color, RoundedCornerShape(corner.dp)),
        contentAlignment = Alignment.Center
    ) {
        val iconUrl = iconUrl?.takeIf { it.isNotBlank() }
        if (iconUrl != null) {
            HttpImage(
                imgUrl = iconUrl,
                modifier = Modifier.size(size.dp),
                contentDescription = "Host Icon",
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                bitmap = DEFAULT_HOST_ICON,
                contentDescription = "Host Icon",
                modifier = Modifier.size(size.dp),
                contentScale = ContentScale.Crop
            )
        }
    }
}
