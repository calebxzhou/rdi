package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.ui.DEFAULT_MODPACK_ICON
import calebxzhou.rdi.client.ui.FlowRowV
import calebxzhou.rdi.client.ui.MaterialColor
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
    val clickableModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }
    if (miniMode) {
        Surface(
            modifier = clickableModifier
                .fillMaxWidth()
                .height(28.dp),
            color = Color.White,
            shape = RoundedCornerShape(999.dp),
            elevation = 1.dp
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
                    color = MaterialColor.GRAY_200.color
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
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialColor.GRAY_900.color,
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
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        elevation = 2.dp
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
                color = MaterialColor.GRAY_200.color
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
                val stackedMeta = maxWidth < 340.dp

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
                                style = MaterialTheme.typography.subtitle1,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialColor.GRAY_900.color,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        ModpackCardMeta(
                            mcVer = mcVer.mcVer,
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
                                style = MaterialTheme.typography.subtitle1,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialColor.GRAY_900.color,
                                modifier = Modifier.weight(1f)
                            )
                            ModpackCardMeta(
                                mcVer = mcVer.mcVer,
                                modloader = modloader,
                                playCount = playCount,
                                modCount = modCount,
                                updatedTimeText = updatedTimeText
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
                            style = MaterialTheme.typography.body2,
                            color = MaterialColor.GRAY_600.color,
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
    modifier: Modifier = Modifier
) {
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
            style = MaterialTheme.typography.caption,
            color = MaterialColor.GRAY_500.color,
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
                style = MaterialTheme.typography.subtitle2,
                fontWeight = FontWeight.Medium,
                color = MaterialColor.GRAY_900.color
            )
        }
        modloader.cardIconName?.let { iconName ->
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = MaterialColor.BLUE_GRAY_100.color
            ) {
                Image(
                    bitmap = iconBitmap(iconName),
                    contentDescription = modloader.cardLabel,
                    modifier = Modifier.padding(4.dp).size(14.dp)
                )
            }
        } ?: ModpackCardChip(modloader.cardLabel)
    }
}

@Composable
private fun ModpackCardChip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialColor.GRAY_200.color
    ) {
        Text(
            text = text,
            color = MaterialColor.GRAY_800.color,
            style = MaterialTheme.typography.caption,
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
