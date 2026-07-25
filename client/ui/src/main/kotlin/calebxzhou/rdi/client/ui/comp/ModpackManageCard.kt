package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calebxzhou.mykotutils.std.millisToHumanDateTime
import calebxzhou.rdi.client.service.ModpackLocalDir
import calebxzau.rdi.client.ui.DEFAULT_MODPACK_ICON

/**
 * calebxzhou @ 2026-01-27 21:24
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModpackManageCard(
    modifier: Modifier = Modifier.fillMaxWidth(),
    packdir: ModpackLocalDir,
    isRunning: Boolean = false,
    selected: Boolean = false,
    miniMode: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    if (miniMode) {
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        val clickModifier = if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier
        val miniContentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        Surface(
            modifier = modifier.then(clickModifier),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = miniContentColor,
            shape = shape,
            border = when {
                selected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                isRunning -> BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
                else -> null
            },
            tonalElevation = if (selected) 2.dp else 0.dp
        ) {
            Row(
                modifier = Modifier.widthIn(max = 350.dp).padding(horizontal = 5.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModpackManageIcon(packdir, 28)
                    Text(
                        text = "${packdir.vo.name.ifBlank { "未知整合包" }} ${packdir.verName}",
                        color = miniContentColor,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        return
    }

    val cardShape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    val clickModifier = if (onClick != null) Modifier.clip(cardShape).clickable(onClick = onClick) else Modifier
    val glowModifier = if (isRunning) {
        Modifier
            .background(MaterialTheme.colorScheme.tertiary, cardShape)
            .padding(2.dp)
    } else {
        Modifier
    }
    Box(
        modifier = modifier.then(glowModifier)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(clickModifier),
            color = MaterialTheme.colorScheme.surface,
            shape = cardShape,
            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ModpackManageIcon(packdir, 64)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = packdir.vo.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = packdir.verName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = packdir.createTime.millisToHumanDateTime,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ModpackManageIcon(
    packdir: ModpackLocalDir,
    size: Int
) {
    val iconUrl = packdir.vo.icon?.takeIf { it.isNotBlank() }
    if (iconUrl != null) {
        HttpImage(
            imgUrl = iconUrl,
            modifier = Modifier.size(size.dp),
            contentDescription = "Modpack Icon",
            contentScale = ContentScale.Crop
        )
    } else {
        androidx.compose.foundation.Image(
            bitmap = DEFAULT_MODPACK_ICON,
            contentDescription = "Modpack Icon",
            modifier = Modifier.size(size.dp),
            contentScale = ContentScale.Crop
        )
    }
}
