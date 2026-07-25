package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.loadResourceBitmap

/**
 * calebxzhou @ 2026-03-31 13:59
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SponsorScreen(
    onBack: () -> Unit = {}
) {
    val sponsorBitmap = remember {
        loadResourceBitmap("assets/sponsor.jpg")
    }
    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.MEDIUM) {
            TitleRow("支持RDI", onBack)
            ContentBody {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val imageWidth = minOf(maxWidth - 32.dp, 720.dp)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("为了开发和维护RDI 用尽了业余时间 付出了很多精力")
                        Text("如果给一个小红包的话 我将不胜感激")
                        Text("赞助可以加速新功能的开发 快把想要的功能告诉我吧")
                        Spacer(modifier = Modifier.height(16.dp))
                        Image(
                            bitmap = sponsorBitmap,
                            contentDescription = "赞助二维码",
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = imageWidth)
                                .padding(horizontal = 16.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}
