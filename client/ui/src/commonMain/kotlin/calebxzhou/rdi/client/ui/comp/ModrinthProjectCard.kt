package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzhou.rdi.client.model.ModrinthProjectCardVo
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.asIconText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModrinthProjectCard(
    project: ModrinthProjectCardVo,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = onClick?.let { modifier.clickable(onClick = it) } ?: modifier
    Surface(
        modifier = clickableModifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        elevation = 2.dp
    ) {
        Column {
            ModrinthProjectBanner(project)
            Column(
                modifier = Modifier.padding(16.dp, 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    ModrinthProjectIcon(project)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = project.title,
                                style = MaterialTheme.typography.h6,
                                fontWeight = FontWeight.Bold,
                                color = MaterialColor.GRAY_900.color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            /*Text(
                                text = "by ${shader.author}",
                                style = MaterialTheme.typography.subtitle1,
                                color = MaterialColor.GRAY_700.color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )*/
                        }
                        Text(
                            text = project.description,
                            style = MaterialTheme.typography.subtitle1,
                            color = MaterialColor.GRAY_900.color,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 22.sp
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    project.categories.forEach { category ->
                        ModrinthProjectChip(category.label)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModrinthProjectStat("\uF019", project.downloadsText)
                    ModrinthProjectStat("\uDB80\uDED1", project.followsText)
                    Spacer(modifier = Modifier.weight(1f))
                    ModrinthProjectStat("\uE641", project.modifiedText)
                }
            }
        }
    }
}

@Composable
private fun ModrinthProjectBanner(project: ModrinthProjectCardVo) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2.25f)
            .background(MaterialColor.BLUE_GRAY_100.color)
    ) {
        project.bannerUrl?.takeIf(String::isNotBlank)?.let { url ->
            HttpImage(
                imgUrl = url,
                modifier = Modifier.fillMaxSize(),
                contentDescription = project.title,
                contentScale = ContentScale.Crop
            )
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialColor.BLUE_GRAY_200.color,
                            MaterialColor.AMBER_200.color,
                            MaterialColor.LIGHT_BLUE_200.color
                        )
                    )
                )
        )
    }
}

@Composable
private fun ModrinthProjectIcon(project: ModrinthProjectCardVo) {
    Surface(
        modifier = Modifier.size(64.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialColor.GRAY_200.color,
        elevation = 1.dp
    ) {
        project.iconUrl?.takeIf(String::isNotBlank)?.let { url ->
            HttpImage(
                imgUrl = url,
                modifier = Modifier.fillMaxSize(),
                contentDescription = project.title,
                contentScale = ContentScale.Crop
            )
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialColor.BLUE_GRAY_200.color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = project.title.firstOrNull()?.uppercaseChar()?.toString() ?: "M",
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold,
                color = MaterialColor.GRAY_800.color
            )
        }
    }
}

@Composable
private fun ModrinthProjectChip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialColor.GRAY_300.color)
    ) {
        Text(
            text = text,
            color = MaterialColor.GRAY_800.color,
            style = MaterialTheme.typography.body2,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ModrinthProjectStat(icon: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon.asIconText,
            color = MaterialColor.GRAY_900.color
        )
        Text(
            text = text,
            color = MaterialColor.GRAY_900.color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
