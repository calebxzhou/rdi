package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.loadResourceBitmap

/**
 * calebxzhou @ 2026-02-02 18:29
 */

@Composable
fun ImageCard(
    title: String,
    iconPath: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape: Shape = RoundedCornerShape(20.dp)
    val bitmap = remember(iconPath) {
        loadResourceBitmap(iconPath)
    }
    Box(modifier = Modifier.padding(6.dp)) {
        Surface(
            modifier = Modifier
                .width(70.dp)
                .then(
                    if (selected) {
                        Modifier.shadow(6.dp, shape, clip = false)
                    } else {
                        Modifier
                    }
                )
                .clickable { onClick() },
            shape = shape,
            color = MaterialColor.GRAY_200.color,
            border = if (selected) BorderStroke(2.dp, MaterialColor.PURPLE_500.color) else null,
            elevation = 0.dp
        ) {
            Column(modifier = Modifier.clip(shape)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(35.dp)
                        .clip(shape)
                        .background(Color(0xFF1E1E1E), shape)
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(title)
                }
            }
        }
    }
}
