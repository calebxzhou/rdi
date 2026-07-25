package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import calebxzhou.mykotutils.std.humanFileSize
import calebxzau.rdi.client.ui.CodeFontFamily
import calebxzau.rdi.client.ui.DEFAULT_MODPACK_ICON
import calebxzau.rdi.client.ui.baseRoundCornerShape
import calebxzau.rdi.client.ui.space8
import calebxzhou.rdi.common.model.World
import calebxzhou.rdi.common.util.toFriendlyDateTime

/**
 * calebxzhou @ 2026-02-02 18:30
 */
@Composable
fun World.Vo.WorldCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val sizeText = if (size > 0) size.humanFileSize else "?"
    val createdTimeText = (id.timestamp.toLong() * 1000L).toFriendlyDateTime()
    val clickableModifier = if (onClick != null) {
        modifier
            .clip(baseRoundCornerShape)
            .clickable(onClick = onClick)
    } else {
        modifier
    }

    Surface(
        modifier = clickableModifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = baseRoundCornerShape,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = space8
        ) {
            val iconUrl = modpackIconUrl?.takeIf { it.isNotBlank() }
            Surface(
                modifier = Modifier.size(54.dp),
                shape = baseRoundCornerShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                if (iconUrl != null) {
                    HttpImage(
                        imgUrl = iconUrl,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = name,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        bitmap = DEFAULT_MODPACK_ICON,
                        contentDescription = "Modpack Icon",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Text(
                text = name.ifBlank { "未命名存档" },
                modifier = Modifier.weight(1.4f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = modpackName.ifBlank { "未知整合包" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = sizeText,
                modifier = Modifier.weight(0.6f),
                fontFamily = CodeFontFamily,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = createdTimeText,
                modifier = Modifier.weight(1.1f),
                fontFamily = CodeFontFamily,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
