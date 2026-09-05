package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.service.ModpackLocalDir
import calebxzau.rdi.client.ui.DEFAULT_MODPACK_ICON
import calebxzau.rdi.client.ui.baseRoundCornerShape
import java.awt.Color

/**
 * calebxzhou @ 2026-01-27 21:24
 */

data class ModpackManageCardPresentation(
    val name: String,
    val versionLabel: String,
    val iconUrl: String?,
)

@Composable
fun ModpackManageCard(
    modifier: Modifier = Modifier.fillMaxWidth(),
    packdir: ModpackLocalDir,
    isRunning: Boolean = false,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    ModpackManageCard(
        modifier = modifier,
        presentation = ModpackManageCardPresentation(
            name = packdir.name,
            versionLabel = packdir.verName,
            iconUrl = packdir.iconUrl,
        ),
        isRunning = isRunning,
        selected = selected,
        onClick = onClick,
    )
}

@Composable
fun ModpackManageCard(
    modifier: Modifier = Modifier.fillMaxWidth(),
    presentation: ModpackManageCardPresentation,
    isRunning: Boolean = false,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clip(baseRoundCornerShape).clickable(onClick = onClick) else Modifier
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier.then(clickModifier),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = contentColor,
        shape = baseRoundCornerShape,
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModpackManageIcon(presentation.iconUrl)
            Text(
                text = buildString {
                    append(presentation.name.ifBlank { "未知整合包" })
                },
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(presentation.versionLabel,color = androidx.compose.ui.graphics.Color.Gray, style = MaterialTheme.typography.bodySmall,)
        }
    }
}

@Composable
private fun ModpackManageIcon(
    iconUrl: String?
) {
    val usableIconUrl = iconUrl?.takeIf { it.isNotBlank() }
    if (usableIconUrl != null) {
        HttpImage(
            imgUrl = usableIconUrl,
            modifier = Modifier.size(28.dp).clip(baseRoundCornerShape),
            contentDescription = "Modpack Icon",
            contentScale = ContentScale.Crop
        )
    } else {
        androidx.compose.foundation.Image(
            bitmap = DEFAULT_MODPACK_ICON,
            contentDescription = "Modpack Icon",
            modifier = Modifier.size(28.dp).clip(baseRoundCornerShape),
            contentScale = ContentScale.Crop
        )
    }
}
