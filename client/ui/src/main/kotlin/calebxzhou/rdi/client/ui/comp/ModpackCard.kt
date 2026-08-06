package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzau.rdi.client.ui.DEFAULT_MODPACK_ICON
import calebxzau.rdi.client.ui.RRow
import calebxzau.rdi.client.ui.baseRoundCornerShape
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.util.toFriendlyDateTime
import kotlin.math.roundToInt

/**
 * calebxzhou @ 2026-01-13 16:14
 */

private const val MODPACK_CARD_META_INLINE_ID = "modpack-card-meta"

@Composable
fun Modpack.BriefVo.ModpackCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        modifier
            .clip(baseRoundCornerShape)
            .clickable(onClick = onClick)
    } else {
        modifier
    }

    val updatedTimeText = ((lastUpdatedTime.takeIf { it > 0L } ?: (id.timestamp.toLong() * 1000L))).toFriendlyDateTime()
    val briefText = info?.trim()?.takeIf(String::isNotBlank) ?: "暂无简介"

    Surface(
        modifier = clickableModifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = baseRoundCornerShape,
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

            val introText = buildAnnotatedString {
                appendInlineContent(MODPACK_CARD_META_INLINE_ID, " ")
                append(briefText)
            }
            val inlineContent = mapOf(
                MODPACK_CARD_META_INLINE_ID to InlineTextContent(
                    placeholder = Placeholder(
                        width = 120.sp,
                        height = 16.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                    )
                ) {
                    ModpackCardMeta(
                        playCount = playCount,
                        updatedTimeText = updatedTimeText
                    )
                }
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text(
                    text = name.ifBlank { "未命名整合包" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = introText,
                    inlineContent = inlineContent,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ModpackCardMeta(
    playCount: Int,
    updatedTimeText: String
) {
    RRow {
        listOf(
            "\uDB80\uDE97",
            playCount.toCompactCountText(),
            updatedTimeText
        ).forEach {
            Text(
                text =  it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
