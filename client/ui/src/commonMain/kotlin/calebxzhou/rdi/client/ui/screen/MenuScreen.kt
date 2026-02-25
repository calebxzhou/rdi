package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.ImageIconButton
import calebxzhou.rdi.client.ui.MainColumn
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.RowV
import calebxzhou.rdi.client.ui.Space24w
import calebxzhou.rdi.client.ui.SpacerFullW
import calebxzhou.rdi.client.ui.TitleRow
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
    onOpenModpackList: () -> Unit,
    onBack: () -> Unit
) {
    val mcResourceColor = MaterialColor.PRIMARY_COLORS.values.first().color

    MainColumn {
        TitleRow("${periodOfDay}好，${loggedAccount.name}", onBack) {
            HeadButton(loggedAccount._id)
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxWidth < 980.dp || maxHeight > maxWidth

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {


                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    RowV {
                        CircleIconButton("\uF04B",bgColor = MaterialColor.GREEN_900.color, contentPadding = PaddingValues(start = 2.dp), size = 32) {
                            onOpenHostLobby?.invoke()
                        }
                        Space24w()
                        Text("地图大厅")
                    }
                    RowV {
                        CircleIconButton("\uEB51", size = 32, contentPadding = PaddingValues(start = 0.dp)) {
                            onOpenSettings?.invoke()
                        }
                        Space24w()
                        Text("设置")
                    }
                    RowV {
                        ImageIconButton("grass_block",bgColor = Color.LightGray, size = 32) {
                            onOpenMcVersions.invoke()
                        }
                        Space24w()
                        Text("MC·本地整合包")
                    }
                    RowV {
                        CircleIconButton("\uEB1C" , size = 32) {
                            onOpenMail.invoke()
                        }
                        Space24w()
                        Text("信箱")
                    }
                    RowV {
                        CircleIconButton("\uDB86\uDDD8", size = 32){
                            onOpenModpackList.invoke()
                        }
                        Space24w()
                        Text("大家的整合包·创建地图")
                    }

                }
                if (!compact) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        PlayerPreviewCard(
                            modifier = Modifier.fillMaxSize(),
                            onClick = onOpenWardrobe
                        )
                    }
                }
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
            maxRenderSide = 720
        )
    }
}

