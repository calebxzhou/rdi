package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.comp.WebPagePane

private const val MCMOD_PAGE_URL = "https://play.mcmod.cn/sv20188037.html"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McmodScreen(
    onBack: () -> Unit = {}
) {
    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            TitleRow("MC百科", onBack)
            ContentBody {
                WebPagePane(
                    url = MCMOD_PAGE_URL,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
