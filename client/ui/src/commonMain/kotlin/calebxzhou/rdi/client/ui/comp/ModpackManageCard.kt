package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calebxzhou.mykotutils.std.millisToHumanDateTime
import calebxzhou.rdi.client.service.ModpackLocalDir
import calebxzhou.rdi.client.ui.DEFAULT_MODPACK_ICON
import calebxzhou.rdi.client.ui.MaterialColor

/**
 * calebxzhou @ 2026-01-27 21:24
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModpackManageCard(
    packdir: ModpackLocalDir,
    isRunning: Boolean = false,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {

    val cardShape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    val glowModifier = if (isRunning) {
        Modifier
            .background(MaterialColor.ORANGE_900.color, cardShape)
            .padding(2.dp)
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(glowModifier)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .let { base ->
                    if (onClick != null) {
                        base.clickable(onClick = onClick)
                    } else {
                        base
                    }
                },
            color = Color(0xFFF9F9FB),
            shape = cardShape,
            border = if (selected) BorderStroke(2.dp, MaterialColor.PURPLE_500.color) else null,
            elevation = 1.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialColor.GRAY_200.color, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val iconUrl = packdir.vo.icon?.takeIf { it.isNotBlank() }
                    if (iconUrl != null) {
                        HttpImage(
                            imgUrl = iconUrl,
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = "Modpack Icon"
                        )
                    } else {
                        androidx.compose.foundation.Image(
                            bitmap = DEFAULT_MODPACK_ICON,
                            contentDescription = "Modpack Icon",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
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
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = packdir.verName,
                            style = MaterialTheme.typography.body2,
                            color = MaterialColor.GRAY_700.color
                        )
                    }
                    Text(
                        text = packdir.createTime.millisToHumanDateTime,
                        style = MaterialTheme.typography.body2,
                        color = MaterialColor.GRAY_700.color
                    )
                }
            }
        }
    }
}
