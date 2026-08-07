package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.model.RemoteModCardVo
import calebxzhou.rdi.client.model.RemoteModSource
import calebxzau.rdi.client.ui.themeNow
import calebxzau.rdi.client.ui.asIconText
import calebxzhou.rdi.client.ui.loadResourceBitmap
import calebxzau.rdi.client.ui.baseRoundCornerShape


@Composable
fun RemoteModCard(
    mod: RemoteModCardVo,
    modifier: Modifier = Modifier.fillMaxWidth(),
    compact: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val iconSize = if (compact) 48.dp else 76.dp
    val contentPadding = if (compact) 8.dp else 10.dp
    val itemGap = if (compact) 8.dp else 10.dp
    Surface(
        modifier = modifier,
        shape = baseRoundCornerShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, themeNow.outlineVariant),
        onClick = onClick ?: {}
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(itemGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(itemGap),
                verticalAlignment = Alignment.Top
            ) {
                RemoteModIcon(mod, iconSize)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mod.title,
                            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        RemoteModSourceIcon(mod.source)
                    }
                    Text(
                        text = mod.summary,
                        style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RemoteModStat("\uF019", mod.downloadsText, compact)
                        mod.followsText?.let { RemoteModStat("\uDB80\uDED1", it, compact) }
                        Spacer(modifier = Modifier.weight(1f))
                        // mod.modifiedText?.let { RemoteModStat("\uE641", it) }
                    }
                }
            }


        }
    }
}

@Composable
private fun RemoteModIcon(mod: RemoteModCardVo, size: androidx.compose.ui.unit.Dp = 76.dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = baseRoundCornerShape,
        color = themeNow.surfaceContainerHighest,
        shadowElevation = 1.dp
    ) {
        mod.iconUrl?.takeIf(String::isNotBlank)?.let { url ->
            HttpImage(
                imgUrl = url,
                modifier = Modifier.fillMaxSize(),
                contentDescription = mod.title,
                contentScale = ContentScale.Crop
            )
        } ?: androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(themeNow.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = mod.title.firstOrNull()?.uppercaseChar()?.toString() ?: "M",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = themeNow.onSurface
            )
        }
    }
}

@Composable
fun RemoteModSourceIcon(
    source: RemoteModSource,
    modifier: Modifier = Modifier.size(16.dp)
) {
    when (source) {
        RemoteModSource.MODRINTH -> Image(
            imageVector = modrinthImageVector,
            contentDescription = source.label,
            modifier = modifier
        )

        RemoteModSource.CURSEFORGE -> {
            val bitmap = remember { loadResourceBitmap("assets/icons/curseforge.png") }
            Image(
                bitmap = bitmap,
                contentDescription = source.label,
                modifier = modifier,
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun RemoteModIconChip(
    icon: @Composable () -> Unit,
    text: String? = null
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, themeNow.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (text == null) 8.dp else 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(18.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            text?.let {
                Text(
                    text = it,
                    color = themeNow.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RemoteModStat(icon: String, text: String, compact: Boolean = false) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon.asIconText,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = text,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val RemoteModSource.label: String
    get() = when (this) {
        RemoteModSource.MODRINTH -> "Modrinth"
        RemoteModSource.CURSEFORGE -> "CurseForge"
    }

private val modrinthImageVector: ImageVector by lazy {
    val paths = listOf(
        "m29 424.4 188.2-112.95-17.15-45.48 53.75-55.21 67.93-14.64 19.67 24.21-31.32 31.72-27.3 8.6-19.52 20.05 9.56 26.6 19.4 20.6 27.36-7.28 19.47-21.38 42.51-13.47 12.67 28.5-43.87 53.78-73.5 23.27-32.97-36.7L55.06 467.94C46.1 456.41 35.67 440.08 29 424.4Zm543.03-230.25-149.5 40.32c8.24 21.92 10.95 34.8 13.23 49l149.23-40.26c-2.38-15.94-6.65-32.17-12.96-49.06Z",
        "M51.28 316.13c10.59 125 115.54 223.3 243.27 223.3 96.51 0 180.02-56.12 219.63-137.46l48.61 16.83c-46.78 101.34-149.35 171.75-268.24 171.75C138.6 590.55 10.71 469.38 0 316.13h51.28ZM.78 265.24C15.86 116.36 141.73 0 294.56 0c162.97 0 295.28 132.31 295.28 295.28 0 26.14-3.4 51.49-9.8 75.63l-48.48-16.78a244.28 244.28 0 0 0 7.15-58.85c0-134.75-109.4-244.15-244.15-244.15-124.58 0-227.49 93.5-242.32 214.11H.8Z",
        "M293.77 153.17c-78.49.07-142.2 63.83-142.2 142.34 0 78.56 63.79 142.34 142.35 142.34 3.98 0 7.93-.16 11.83-.49l14.22 49.76a194.65 194.65 0 0 1-26.05 1.74c-106.72 0-193.36-86.64-193.36-193.35 0-106.72 86.64-193.35 193.36-193.35 2.64 0 5.28.05 7.9.16l-8.05 50.85Zm58.2-42.13c78.39 24.67 135.3 97.98 135.3 184.47 0 80.07-48.77 148.83-118.2 178.18l-14.17-49.55c48.08-22.85 81.36-71.89 81.36-128.63 0-60.99-38.44-113.07-92.39-133.32l8.1-51.15Z"
    )
    ImageVector.Builder(
        name = "Modrinth",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 593f,
        viewportHeight = 593f
    ).apply {
        paths.forEach { pathData ->
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color(0xFF00AF5C))
            )
        }
    }.build()
}
