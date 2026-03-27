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
import calebxzhou.rdi.client.auth.LocalCredentials
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.server
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
    onOpenModpackLocalManage: () -> Unit,
    onOpenMcVersionManage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMail: () -> Unit,
    onOpenHostLobby: () -> Unit,
    onOpenHostInfo: (String) -> Unit,
    onOpenWardrobe: () -> Unit,
    onOpenWorldList: () -> Unit,
    onBack: () -> Unit
) {
    val lastPlayHost = remember { LocalCredentials.read().lastPlayHost }
    var onlinePlayerIds by remember { mutableStateOf<List<ObjectId>>(emptyList()) }

    LaunchedEffect(Unit) {
        runCatching {
            server.makeRequest<List<ObjectId>>("host/online-player-ids").data ?: emptyList()
        }.onSuccess { ids ->
            onlinePlayerIds = ids.distinct()
        }
    }

    MainColumn {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxWidth < 980.dp || maxHeight > maxWidth
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 28.dp else 72.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RowV {
                        Text("${periodOfDay}好，")
                        HeadButton(loggedAccount._id)
                    }
                    FlowRow(
                        modifier = Modifier.widthIn(max = 320.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("在线${onlinePlayerIds.size}人")
                        Space8w()
                        onlinePlayerIds.forEach {
                            HeadButton(it, showName = false, avatarSize = 12.dp)
                        }
                    }
                    PlayerPreviewCard(
                        modifier = Modifier.size(320.dp),
                        onClick = onOpenWardrobe
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.widthIn(min = if (compact) 180.dp else 220.dp)
                ) {
                    CircleIconButton(
                        "\uDB81\uDC25",
                        "退出",
                        bgColor = MaterialColor.RED_900.color,
                        contentPadding = PaddingValues()
                    ) {
                        loggedAccount = RAccount.DEFAULT
                        onBack.invoke()
                    }
                    CircleIconButton(
                        "\uEB51",
                        "设置"
                    ) {
                        onOpenSettings()
                    }
                    ImageIconButton("grass_block", "MC资源", bgColor = MaterialColor.GREEN_200.color) {
                        onOpenMcVersionManage()
                    }
                    ImageIconButton("chest", "整合包", bgColor = MaterialColor.AMBER_200.color) {
                        onOpenModpackLocalManage()
                    }
                    CircleIconButton("\uEB1C", "信箱") {
                        onOpenMail.invoke()
                    }
                    CircleIconButton("\uDB85\uDC5C", "存档") {
                        onOpenWorldList.invoke()
                    }
                    CircleIconButton(
                        "\uF04B",
                        "地图",
                        bgColor = MaterialColor.GREEN_900.color,
                        contentPadding = PaddingValues(start = 2.dp)
                    ) {
                        onOpenHostLobby.invoke()
                    }

                }
            }
            RowV(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                lastPlayHost?.let { host ->
                    CircleIconButton(
                        icon = "\uF04B",
                        tooltip = "继续游玩地图:${host.name}",
                        bgColor = MaterialColor.GREEN_900.color
                    ) {
                        onOpenHostInfo(host.id)
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
