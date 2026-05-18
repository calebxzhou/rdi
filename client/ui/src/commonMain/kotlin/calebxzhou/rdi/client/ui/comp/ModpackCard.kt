package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.ui.DEFAULT_MODPACK_ICON
import calebxzhou.rdi.client.ui.FlowRowV
import calebxzhou.rdi.client.ui.asIconText
import calebxzhou.rdi.client.ui.iconBitmap
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.util.toFriendlyDateTime
import kotlin.math.roundToInt

/**
 * calebxzhou @ 2026-01-13 16:14
 */
@Composable
fun Modpack.BriefVo.ModpackCard(
    modifier: Modifier = Modifier,
    miniMode: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val cardShape = if (miniMode) RoundedCornerShape(999.dp) else RoundedCornerShape(18.dp)
    val clickableModifier = if (onClick != null) {
        modifier
            .clip(cardShape)
            .clickable(onClick = onClick)
    } else {
        modifier
    }
    if (miniMode) {
        Surface(
            modifier = clickableModifier
                .fillMaxWidth()
                .height(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = cardShape,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val iconUrl = icon?.takeIf { it.isNotBlank() }
                Surface(
                    modifier = Modifier.size(20.dp),
                    shape = RoundedCornerShape(6.dp),
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
                    text = name.ifBlank { "未命名整合包" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        return
    }

    val updatedTimeText = (lastUpdatedTime.takeIf { it > 0L } ?: id.timestamp.toLong() * 1000L).toFriendlyDateTime()
    val briefText = info?.trim()?.takeIf(String::isNotBlank) ?: "暂无简介"

    Surface(
        modifier = clickableModifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = cardShape,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            val iconUrl = icon?.takeIf { it.isNotBlank() }
            Surface(
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(14.dp),
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

            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val stackedMeta = maxWidth < 520.dp

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (stackedMeta) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name.ifBlank { "未命名整合包" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        ModpackCardMeta(
                            mcVer = mcVer.simpleVer,
                            modloader = modloader,
                            playCount = playCount,
                            modCount = modCount,
                            updatedTimeText = updatedTimeText,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name.ifBlank { "未命名整合包" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            ModpackCardMeta(
                                mcVer = mcVer.simpleVer,
                                modloader = modloader,
                                playCount = playCount,
                                modCount = modCount,
                                updatedTimeText = updatedTimeText,
                                aligned = true
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (categories.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                categories.take(3).forEach { category ->
                                    ModpackCardChip(category.label)
                                }
                            }
                        }
                        Text(
                            text = briefText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModpackCardMeta(
    mcVer: String,
    modloader: ModLoader,
    playCount: Int,
    modCount: Int,
    updatedTimeText: String,
    modifier: Modifier = Modifier,
    aligned: Boolean = false
) {
    if (aligned) {
        Row(
            modifier = modifier.width(332.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModpackCardStat(
                icon = "\uDB80\uDE97",
                text = playCount.toCompactCountText(),
                modifier = Modifier.width(50.dp)
            )
            ModpackCardStat(
                icon = "\uDB81\uDC31",
                text = modCount.toString(),
                modifier = Modifier.width(54.dp)
            )
            Text(
                text = updatedTimeText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.width(98.dp)
            )
            Row(
                modifier = Modifier.width(74.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = iconBitmap("grass_block"),
                    contentDescription = "MC版本",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = mcVer,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier.width(24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                ModpackLoaderIcon(modloader)
            }
        }
        return
    }

    val statsText = buildString {
        append("\uDB80\uDE97  ")
        append(playCount.toCompactCountText())
        append(" · ")
        append("\uDB81\uDC31  ")
        append(modCount)
        append(" · ")
        append(updatedTimeText)
    }
    FlowRowV(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = statsText.asIconText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = iconBitmap("grass_block"),
                contentDescription = "MC版本",
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = mcVer,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        ModpackLoaderIcon(modloader)
    }
}

@Composable
private fun ModpackCardStat(
    icon: String,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon.asIconText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ModpackLoaderIcon(modloader: ModLoader) {
    modloader.cardIconName?.let { iconName ->
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Image(
                bitmap = iconBitmap(iconName),
                contentDescription = modloader.cardLabel,
                modifier = Modifier.padding(4.dp).size(14.dp)
            )
        }
    } ?: ModpackCardChip(modloader.cardLabel)
}

@Composable
private fun ModpackCardChip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

private val ModLoader.cardLabel: String
    get() = when (this) {
        ModLoader.forge -> "Forge"
        ModLoader.neoforge -> "NeoForge"
        ModLoader.cleanroom -> "Cleanroom"
    }

private val ModLoader.cardIconName: String?
    get() = when (this) {
        ModLoader.forge -> "forge"
        ModLoader.neoforge -> "neoforge"
        ModLoader.cleanroom ->  "cleanroom"
    }

private fun Int.toCompactCountText(): String {
    val safeValue = coerceAtLeast(0)
    return when {
        safeValue >= 100_000_000 -> compactUnitText(safeValue / 100_000_000.0, "亿")
        safeValue >= 10_000 -> compactUnitText(safeValue / 10_000.0, "万")
        else -> safeValue.toString()
    }
}

private fun compactUnitText(value: Double, suffix: String): String {
    val scaledTenths = (value * 10).roundToInt()
    val whole = scaledTenths / 10
    val decimal = scaledTenths % 10
    val formatted = if (value >= 100 || decimal == 0) {
        whole.toString()
    } else {
        "$whole.$decimal"
    }
    return formatted + suffix
}
