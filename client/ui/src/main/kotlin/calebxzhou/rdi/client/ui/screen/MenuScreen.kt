package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ImageIconButton
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.RowV
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.RDIClient
import calebxzhou.rdi.client.auth.LocalCredentials
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.HeadButton
import calebxzhou.rdi.client.ui.comp.PlayerModel
import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.util.periodOfDay
import org.bson.types.ObjectId

/**
 * calebxzhou @ 2026-02-25 17:51
 */

@Composable
fun MenuScreen(
    onOpenResources: () -> Unit,
    onOpenMcmod: () -> Unit,
    onOpenSponsor: () -> Unit,
    onOpenHostLobby: () -> Unit,
    onOpenHost2Lobby: () -> Unit,
    onOpenHostInfo: (String) -> Unit,
    onOpenWardrobe: () -> Unit
) {
    val lastPlayHost = remember { LocalCredentials.read().lastPlayHost }
    var onlinePlayerIds by remember { mutableStateOf<List<ObjectId>>(emptyList()) }
    var showOldMainWarning by remember { mutableStateOf(RDIClient.OLD_MAIN) }

    LaunchedEffect(Unit) {
        runCatching {
            server.makeRequest<List<ObjectId>>("host/online-player-ids").data ?: emptyList()
        }.onSuccess { ids ->
            onlinePlayerIds = ids.distinct()
        }
    }

    if (showOldMainWarning) {
        AlertDialog(
            onDismissRequest = { showOldMainWarning = false },
            title = { Text("旧版入口即将停止支持") },
            text = {
                Text(
                    "你正在使用旧版入口启动rdi\n" +
                        "此入口将在2026.8.10停止支持\n" +
                        "请打开rdi安装文件夹，从\"start.exe\"启动rdi 然后删掉桌面图标重新创建"
                )
            },
            confirmButton = {
                CircleIconButton(
                    icon = "\uF00C",
                    tooltip = "知道了"
                ) {
                    showOldMainWarning = false
                }
            }
        )
    }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.MEDIUM) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(72.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MenuAccountSummary(onlinePlayerIds = onlinePlayerIds)
                        PlayerPreviewCard(
                            modifier = Modifier.size(320.dp),
                            onClick = onOpenWardrobe
                        )
                    }

                    MenuActionButtons(
                        modifier = Modifier.widthIn(min = 220.dp),
                        onOpenResources = onOpenResources,
                        onOpenHostLobby = onOpenHostLobby,
                        onOpenHost2Lobby = onOpenHost2Lobby,
                        onOpenMcmod = onOpenMcmod,
                        onOpenSponsor = onOpenSponsor
                    )
                }

                RowV(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 24.dp, bottom = 24.dp)
                ) {
                    Column {
                        lastPlayHost?.let { host ->
                            CircleIconButton(
                                icon = "\uF04B",
                                tooltip = "继续游玩房间:${host.name}",
                                bgColor = MaterialColor.GREEN_900.color
                            ) {
                                onOpenHostInfo(host.id)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuAccountSummary(
    onlinePlayerIds: List<ObjectId>,
    modifier: Modifier = Modifier
) {


    Column(
        modifier = modifier,
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
    }

}

@Composable
private fun MenuActionButtons(
    modifier: Modifier = Modifier,
    onOpenResources: () -> Unit,
    onOpenHostLobby: () -> Unit,
    onOpenHost2Lobby: () -> Unit,
    onOpenMcmod: () -> Unit,
    onOpenSponsor: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        ImageIconButton("grass_block", "资源") {
            onOpenResources()
        }
        CircleIconButton(
            "\uF04B",
            "游玩",
            contentPadding = PaddingValues(start = 2.dp)
        ) {
            onOpenHostLobby()
        }
        if(DEBUG){

            CircleIconButton(
                icon = "\uF1B3",
                tooltip = "新版房间"
            ) {
                onOpenHost2Lobby()
            }
        }
        ImageIconButton(
            icon = "mcmod",
            tooltip = "百科"
        ) {
            onOpenMcmod()
        }
        CircleIconButton(
            icon = "\uF004",
            tooltip = "支持"
        ) {
            onOpenSponsor()
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
            maxRenderSide = 480,
            noControl = true
        )
    }
}
