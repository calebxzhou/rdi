package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.HeadButton
import calebxzhou.rdi.client.ui.comp.PlayerModel
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.util.periodOfDay

/**
 * calebxzhou @ 2026-02-25 17:51
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    onOpenMcVersions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMail: () -> Unit,
    onOpenHostLobby: () -> Unit,
    onOpenWardrobe: () -> Unit,
    onOpenWorldList: () -> Unit,
    onOpenModpackList: () -> Unit,
    onBack: () -> Unit
) {

    MainColumn {
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val compact = maxWidth < 980.dp || maxHeight > maxWidth
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RowV {
                    Text("${periodOfDay}好，")
                    HeadButton(loggedAccount._id)
                }
                Space24h()
                RowV {

                }
                RowV {
                    CircleIconButton(
                        "⏻",
                        "退出登录",
                        bgColor = MaterialColor.RED_900.color,
                        contentPadding = PaddingValues(start = 1.dp, bottom = 4.dp)
                    ) {
                        loggedAccount = RAccount.DEFAULT
                        onBack.invoke()
                    }
                    Space8w()
                    CircleIconButton(
                        "\uEB51",
                        "设置"
                    ) {
                        onOpenSettings()
                    }
                    Space8w()
                    ImageIconButton("grass_block", "版本管理", bgColor = Color.LightGray) {
                        onOpenMcVersions?.invoke()
                    }
                    Space8w()
                    CircleIconButton("\uEB1C", "信箱") {
                        onOpenMail.invoke()
                    }
                    Space8w()
                    CircleIconButton("\uDB85\uDC5C", "区块数据管理") {
                        onOpenWorldList?.invoke()
                    }
                    Space8w()
                    CircleIconButton(
                        "\uF04B",
                        "多人游玩",
                        bgColor = MaterialColor.GREEN_900.color,
                        contentPadding = PaddingValues(start = 2.dp)
                    ) {
                        onOpenHostLobby?.invoke()
                    }
                }


                PlayerPreviewCard(
                    modifier = Modifier
                        .size(320.dp),
                    onClick = onOpenWardrobe
                )


            }
        }


    }
}

@Composable
private fun PlayerPreviewCard(
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        PlayerModel(
            skinUrl = loggedAccount.cloth.skin,
            modifier = Modifier.fillMaxSize(),
            backgroundColor = Color.Transparent,
            autoRotate = true,
            animateWalk = true,
            showOuterLayer = true,
            isSlim = loggedAccount.cloth.isSlim,
            maxRenderSide = 1440
        )
    }
}
