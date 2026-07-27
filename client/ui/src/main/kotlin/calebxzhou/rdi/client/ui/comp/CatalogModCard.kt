package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.asIconText
import calebxzau.rdi.client.ui.baseRoundCornerShape
import calebxzau.rdi.client.lgr
import calebxzhou.rdi.client.modcatalog.CatalogMod
import calebxzhou.rdi.client.service.loadLocalIcon
import calebxzhou.rdi.client.ui.MaterialColor

@Composable
fun CatalogModCard(
    mod: CatalogMod,
    modifier: Modifier = Modifier.fillMaxWidth(),
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val iconSize = if (compact) 48.dp else 76.dp
    val padding = if (compact) 8.dp else 10.dp
    val gap = if (compact) 8.dp else 10.dp
    val localIcon by produceState(
        initialValue = CatalogLocalIconLookup(),
        key1 = mod.identity.stableKey,
        key2 = mod.sources
    ) {
        value = mod.loadLocalIcon().fold(
            onSuccess = { CatalogLocalIconLookup(complete = true, iconData = it) },
            onFailure = { cause ->
                lgr.warn(cause) { "查找本地Mod图标失败: ${mod.identity.stableKey}" }
                CatalogLocalIconLookup(complete = true)
            }
        )
    }
    val icon = rememberLocalFirstImage(
        iconData = localIcon.iconData,
        iconUrls = mod.iconUrls.takeIf { localIcon.complete }.orEmpty()
    )
    Surface(
        modifier = modifier,
        shape = baseRoundCornerShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(padding),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(iconSize),
                shape = baseRoundCornerShape,
                color = MaterialColor.GRAY_200.color
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = mod.nameCn ?: mod.name,
                        contentScale = ContentScale.Crop
                    )
                } else Box(
                    modifier = Modifier.fillMaxSize().background(MaterialColor.BLUE_GRAY_100.color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (mod.nameCn ?: mod.name).firstOrNull()?.uppercaseChar()?.toString() ?: "M",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)
            ) {
                Text(
                    text = mod.nameCn ?: mod.name,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (mod.nameCn != null) {
                    Text(
                        text = mod.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = mod.summary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "\uF019 ${mod.downloadCount.compactCount()}".asIconText,
                        color = MaterialColor.GRAY_700.color,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private data class CatalogLocalIconLookup(
    val complete: Boolean = false,
    val iconData: ByteArray? = null
)

internal fun Long.compactCount(): String = when {
    this >= 100_000_000 -> "${this / 100_000_000}亿"
    this >= 10_000 -> "${this / 10_000}万"
    else -> toString()
}
