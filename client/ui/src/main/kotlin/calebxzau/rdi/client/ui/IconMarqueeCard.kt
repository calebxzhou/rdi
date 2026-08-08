package calebxzau.rdi.client.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.lgr
import calebxzhou.rdi.client.service.HttpImageState
import calebxzhou.rdi.client.ui.comp.PlayerHead
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive

private const val MARQUEE_SPEED_DP_PER_SECOND = 24f
private const val MARQUEE_CARD_ASPECT_RATIO = 1.5f

sealed interface MarqueeIcon {
    val contentDescription: String?

    data class Resource(
        val path: String,
        override val contentDescription: String? = null
    ) : MarqueeIcon

    data class Url(
        val url: String,
        override val contentDescription: String? = null
    ) : MarqueeIcon

    data class SkinHead(
        val image: ImageBitmap?,
        override val contentDescription: String? = null
    ) : MarqueeIcon
}

@Composable
fun IconMarqueeCard(
    title: String,
    subtitle: String,
    icons: List<MarqueeIcon>,
    backgroundBrush: Brush= Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.primary
        )
    ),
    modifier: Modifier = Modifier
) {

    Surface(
        modifier = modifier
            .aspectRatio(MARQUEE_CARD_ASPECT_RATIO)
            .clip(baseRoundCornerShape),
        shape = baseRoundCornerShape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                IconMarqueeTracks(
                    icons = icons,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = subtitle,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = title,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun IconMarqueeTracks(
    icons: List<MarqueeIcon>,
    modifier: Modifier
) {
    val lanes = remember(icons) { splitMarqueeIcons(icons) }
    val density = LocalDensity.current
    var viewportWidthPx by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .onSizeChanged { size -> viewportWidthPx = size.width }
            .clipToBounds()
    ) {
        if (lanes.isNotEmpty() && viewportWidthPx > 0) {
            val viewportWidthDp = with(density) { viewportWidthPx.toDp() }
            val iconSize = marqueeIconSize(viewportWidthDp)
            val itemGap = (iconSize.value * 0.16f).coerceIn(12f, 32f).dp
            val visibleItemCount = calculateMarqueeItemCount(
                viewportWidthDp = viewportWidthDp.value,
                iconSizeDp = iconSize.value,
                gapDp = itemGap.value
            )
            val trackItemCount = calculateMarqueeTrackItemCount(
                visibleItemCount = visibleItemCount,
                lanes = lanes
            )
            val laneItems = remember(lanes, trackItemCount) {
                lanes.map { cycleMarqueeIcons(it, trackItemCount) }
            }
            val laneTracks = remember(laneItems, trackItemCount) {
                laneItems.map { buildMarqueeTrackIcons(it, trackItemCount) }
            }
            val trackWidthDp = (iconSize.value + itemGap.value) * trackItemCount
            val trackWidthPx = with(density) { trackWidthDp.dp.toPx() }
            val offset = remember(trackWidthPx) { Animatable(-trackWidthPx) }

            LaunchedEffect(trackWidthPx, trackWidthDp) {
                offset.snapTo(-trackWidthPx)
                val durationMillis = marqueeDurationMillis(trackWidthDp)
                while (isActive) {
                    offset.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = durationMillis,
                            easing = LinearEasing
                        )
                    )
                    offset.snapTo(-trackWidthPx)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = marqueeTopPadding(iconSize)),
                verticalArrangement = Arrangement.spacedBy(itemGap)
            ) {
                laneTracks.forEach { lane ->
                    MarqueeLane(
                        icons = lane,
                        offsetPx = offset.value,
                        iconSize = iconSize,
                        itemGap = itemGap,
                        trackWidthDp = trackWidthDp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(iconSize)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarqueeLane(
    icons: List<MarqueeIcon>,
    offsetPx: Float,
    iconSize: Dp,
    itemGap: Dp,
    trackWidthDp: Float,
    modifier: Modifier
) {
    Box(modifier = modifier.clipToBounds()) {
        Row(
            modifier = Modifier
                .requiredWidth((trackWidthDp * 3f).dp)
                .offset { IntOffset(offsetPx.roundToInt(), 0) },
            horizontalArrangement = Arrangement.spacedBy(itemGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icons.forEach { icon ->
                MarqueeIconImage(
                    icon = icon,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
private fun MarqueeIconImage(
    icon: MarqueeIcon,
    modifier: Modifier
) {
    when (icon) {
        is MarqueeIcon.Resource -> {
            val bitmap = remember(icon.path) {
                loadImageBitmap(icon.path)
                    .onFailure { error ->
                        lgr.warn(error) { "本地图标加载失败: ${icon.path}" }
                    }
                    .getOrNull()
            }
            MarqueeIconBitmap(
                bitmap = bitmap,
                contentDescription = icon.contentDescription,
                modifier = modifier
            )
        }

        is MarqueeIcon.Url -> {
            val state by produceState(
                initialValue = HttpImageState.peek(icon.url) ?: HttpImageState.loading(),
                key1 = icon.url
            ) {
                value = HttpImageState.peek(icon.url) ?: HttpImageState.fetch(icon.url)
            }
            MarqueeIconBitmap(
                bitmap = state.bitmap,
                contentDescription = icon.contentDescription,
                isLoading = state.isLoading,
                modifier = modifier
            )
        }

        is MarqueeIcon.SkinHead -> MarqueeIconSkinHead(
            image = icon.image,
            modifier = modifier
        )
    }
}

@Composable
private fun MarqueeIconSkinHead(
    image: ImageBitmap?,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(percent = 24)
    Surface(
        modifier = modifier.clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        PlayerHead(
            skinImage = image,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun MarqueeIconBitmap(
    bitmap: ImageBitmap?,
    contentDescription: String?,
    modifier: Modifier,
    isLoading: Boolean = false
) {
    val shape = RoundedCornerShape(percent = 24)
    Surface(
        modifier = modifier.clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }

            else -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}

internal fun splitMarqueeIcons(icons: List<MarqueeIcon>): List<List<MarqueeIcon>> {
    if (icons.isEmpty()) return emptyList()

    val firstLane = ArrayList<MarqueeIcon>()
    val secondLane = ArrayList<MarqueeIcon>()
    icons.forEachIndexed { index, icon ->
        if (index % 2 == 0) {
            firstLane += icon
        } else {
            secondLane += icon
        }
    }
    return listOf(firstLane, secondLane).filter(List<MarqueeIcon>::isNotEmpty)
}

internal fun cycleMarqueeIcons(
    icons: List<MarqueeIcon>,
    count: Int
): List<MarqueeIcon> {
    if (icons.isEmpty() || count <= 0) return emptyList()
    return List(count) { index -> icons[index % icons.size] }
}

internal fun buildMarqueeTrackIcons(
    icons: List<MarqueeIcon>,
    trackItemCount: Int
): List<MarqueeIcon> {
    val canonicalTrack = cycleMarqueeIcons(icons, trackItemCount)
    return canonicalTrack + canonicalTrack + canonicalTrack
}

internal fun calculateMarqueeItemCount(
    viewportWidthDp: Float,
    iconSizeDp: Float,
    gapDp: Float
): Int {
    val itemPitchDp = iconSizeDp + gapDp
    return (ceil(viewportWidthDp / itemPitchDp).toInt() + 1).coerceAtLeast(1)
}

internal fun calculateMarqueeTrackItemCount(
    visibleItemCount: Int,
    lanes: List<List<MarqueeIcon>>
): Int {
    return maxOf(visibleItemCount, lanes.maxOfOrNull { it.size } ?: 0)
}

internal fun marqueeDurationMillis(trackWidthDp: Float): Int {
    return (trackWidthDp / MARQUEE_SPEED_DP_PER_SECOND * 1_000f)
        .roundToInt()
        .coerceAtLeast(1)
}

private fun marqueeIconSize(maxWidth: Dp): Dp {
    if (!maxWidth.value.isFinite()) return 48.dp
    return (maxWidth.value / 6.25f).coerceIn(32f, 208f).dp
}

private fun marqueeTopPadding(iconSize: Dp): Dp {
    return (iconSize.value * 0.28f).coerceIn(24f, 56f).dp
}
