package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.sp
import calebxzhou.rdi.client.model.RemoteModCardVo
import calebxzhou.rdi.client.model.RemoteModSource
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.asIconText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemoteModCard(
    mod: RemoteModCardVo,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = onClick?.let { modifier.clickable(onClick = it) } ?: modifier
    Surface(
        modifier = clickableModifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        elevation = 1.dp,
        border = BorderStroke(1.dp, MaterialColor.GRAY_200.color)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                RemoteModIcon(mod)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mod.title,
                            style = MaterialTheme.typography.h6,
                            fontWeight = FontWeight.Bold,
                            color = MaterialColor.GRAY_900.color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = mod.source.label,
                            style = MaterialTheme.typography.caption,
                            color = MaterialColor.GRAY_700.color,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "by ${mod.author}",
                        style = MaterialTheme.typography.body2,
                        color = MaterialColor.GRAY_700.color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mod.summary,
                        style = MaterialTheme.typography.subtitle1,
                        color = MaterialColor.GRAY_900.color,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 21.sp
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                mod.clientSide?.let { RemoteModChip(it.toSideLabel("客户端")) }
                mod.serverSide?.let { RemoteModChip(it.toSideLabel("服务端")) }
                mod.loaders.take(3).forEach { RemoteModChip(it.toLoaderLabel()) }
                mod.categories.take(3).forEach { RemoteModChip(it.label) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RemoteModStat("\uF019", mod.downloadsText)
                mod.followsText?.let { RemoteModStat("\uDB80\uDED1", it) }
                Spacer(modifier = Modifier.weight(1f))
                mod.modifiedText?.let { RemoteModStat("\uE641", it) }
            }
        }
    }
}

@Composable
private fun RemoteModIcon(mod: RemoteModCardVo) {
    Surface(
        modifier = Modifier.size(72.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialColor.GRAY_200.color,
        elevation = 1.dp
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
                .background(MaterialColor.BLUE_GRAY_100.color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = mod.title.firstOrNull()?.uppercaseChar()?.toString() ?: "M",
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold,
                color = MaterialColor.GRAY_800.color
            )
        }
    }
}

@Composable
private fun RemoteModChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialColor.GRAY_300.color)
    ) {
        Text(
            text = text,
            color = MaterialColor.GRAY_800.color,
            style = MaterialTheme.typography.body2,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RemoteModStat(icon: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon.asIconText, color = MaterialColor.GRAY_900.color)
        Text(
            text = text,
            color = MaterialColor.GRAY_900.color,
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

private fun String.toLoaderLabel(): String =
    replaceFirstChar { it.uppercase() }

private fun String.toSideLabel(name: String): String =
    when (lowercase()) {
        "required" -> "$name 必需"
        "optional" -> "$name 可选"
        "unsupported" -> "不支持$name"
        else -> "$name:$this"
    }
