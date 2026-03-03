package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.HeadButton
import calebxzhou.rdi.client.ui.comp.PlayerModel
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.util.periodOfDay
import org.bson.types.ObjectId

/**
 * calebxzhou @ 2026-02-25 17:51
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MenuScreen(
    onOpenMcVersions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMail: () -> Unit,
    onOpenHostLobby: () -> Unit,
    onOpenWardrobe: () -> Unit,
    onOpenWorldList: () -> Unit,
    onBack: () -> Unit
) {
    var onlinePlayerIds by remember { mutableStateOf<List<ObjectId>>(emptyList()) }

    LaunchedEffect(Unit) {
        runCatching {
            server.makeRequest<List<ObjectId>>("host/online-player-ids").data ?: emptyList()
        }.onSuccess { ids ->
            onlinePlayerIds = ids.distinct()
        }
    }

    MainColumn {
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val compact = maxWidth < 980.dp || maxHeight > maxWidth
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RowV {
                    Text("${periodOfDay}好，")
                    HeadButton(loggedAccount._id)
                }
                Space8h()
                FlowRow(
                    modifier = Modifier.widthIn(max = 460.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("在线${onlinePlayerIds.size}人")
                    Space8w()
                    onlinePlayerIds.forEach {
                        HeadButton(it, showName = false, avatarSize = 12.dp)
                    }
                }
                Space8h()
                RowV {
                    CircleIconButton(
                        "\uDB81\uDC25",
                        "退出登录",
                        bgColor = MaterialColor.RED_900.color,
                        contentPadding = PaddingValues(),
                        showText = false
                    ) {
                        loggedAccount = RAccount.DEFAULT
                        onBack.invoke()
                    }
                    Space8w()
                    CircleIconButton(
                        "\uEB51",
                        "设置",
                        showText = false
                    ) {
                        onOpenSettings()
                    }
                    Space8w()
                    ImageIconButton("grass_block", "版本管理", bgColor = Color.LightGray ) {
                        onOpenMcVersions?.invoke()
                    }
                    Space8w()
                    CircleIconButton("\uEB1C", "信箱", bgColor = Color.LightGray, iconColor = Color.Black,
                        showText = false) {
                        onOpenMail.invoke()
                    }
                    Space8w()
                    CircleIconButton("\uDB85\uDC5C", "区块数据管理",
                        showText = false) {
                        onOpenWorldList?.invoke()
                    }
                    Space8w()
                    CircleIconButton(
                        "\uF04B",
                        "多人游玩",
                        bgColor = MaterialColor.GREEN_900.color,
                        contentPadding = PaddingValues(start = 2.dp),
                        showText = false
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
            capeUrl = loggedAccount.cloth.cape,
            modifier = Modifier.fillMaxSize(),
            backgroundColor = Color.Transparent,
            autoRotate = true,
            animateWalk = true,
            showOuterLayer = true,
            isSlim = loggedAccount.cloth.isSlim,
            maxRenderSide = 128
        )
    }
}
