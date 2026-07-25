package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzau.rdi.client.ui.decodeImageBitmap
import calebxzau.rdi.client.ui.wM
import calebxzhou.rdi.common.model.Mod

/**
 * calebxzhou @ 2026-01-13 21:28
 */
@Composable
fun Mod.CardVo.ModCard(
    modifier: Modifier = Modifier,
    currentSide: Mod.Side = side,
    onSideChange: ((Mod.Side) -> Unit)? = null
) {
    val (clientEnabled, serverEnabled) = sideToFlags(currentSide)
    val hasChineseName = !nameCn.isNullOrBlank()
    val primaryText = if (hasChineseName) nameCn!!.trim() else name.trim()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(12.dp, 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        ModCardIcon(
            iconData = iconData,
            iconUrls = iconUrls,
            modName = primaryText.ifBlank { name.trim() },
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = primaryText.ifBlank { name.trim() },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(8.wM)
                ToggleButton(
                    icon = "\uF108",
                    checked = clientEnabled,
                    onClick = onSideChange?.let {
                        {
                            it(flagsToSide(clientEnabled = !clientEnabled, serverEnabled = serverEnabled))
                        }
                    }
                )
                Spacer(6.wM)
                ToggleButton(
                    icon = "\uF233",
                    checked = serverEnabled,
                    onClick = onSideChange?.let {
                        {
                            it(flagsToSide(clientEnabled = clientEnabled, serverEnabled = !serverEnabled))
                        }
                    }
                )
            }

            if (hasChineseName) {
                val secondaryTrimmed = name.trim()
                if (secondaryTrimmed.isNotEmpty()) {
                    Text(
                        text = secondaryTrimmed,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontStyle = FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Intro
            Text(
                text = intro.ifBlank { "暂无简介" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.6.sp // 1.2 * 13sp
            )
        }
    }
}

@Composable
private fun ModCardIcon(
    iconData: ByteArray?,
    iconUrls: List<String>,
    modName: String,
    modifier: Modifier = Modifier
) {
    val localBitmap = remember(iconData) {
        iconData?.let { bytes ->
            runCatching { decodeImageBitmap(bytes) }.getOrNull()
        }
    }
    val iconUrl = iconUrls.firstOrNull { it.isNotBlank() }.orEmpty()
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            localBitmap != null -> {
                Image(
                    bitmap = localBitmap,
                    contentDescription = modName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            iconUrl.isNotBlank() -> {
                HttpImage(
                    imgUrl = iconUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = modName,
                    contentScale = ContentScale.Crop
                )
            }

            else -> {
                Text(
                    text = modName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "M",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }
    }
}

private fun sideToFlags(side: Mod.Side): Pair<Boolean, Boolean> = when (side) {
    Mod.Side.CLIENT -> true to false
    Mod.Side.SERVER -> false to true
    Mod.Side.BOTH -> true to true
    Mod.Side.UNKNOWN -> false to false
}

private fun flagsToSide(clientEnabled: Boolean, serverEnabled: Boolean): Mod.Side = when {
    clientEnabled && serverEnabled -> Mod.Side.BOTH
    clientEnabled -> Mod.Side.CLIENT
    serverEnabled -> Mod.Side.SERVER
    else -> Mod.Side.UNKNOWN
}
