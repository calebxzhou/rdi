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
    onOpenResources: () -> Unit,
    onOpenSponsor: () -> Unit,
    onOpenTaskList: () -> Unit,
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
            val portrait = maxHeight > maxWidth
            val compact = maxWidth < 980.dp || portrait
            if (portrait) {
                val buttonAreaWidth = 128.dp
                val playerCardSize = minOf(
                    maxWidth - buttonAreaWidth - 32.dp,
                    maxHeight * 0.42f,
                    320.dp
                ).coerceAtLeast(200.dp)

                MenuAccountSummary(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp),
                    onlinePlayerIds = onlinePlayerIds
                )

                PlayerPreviewCard(
                    modifier = Modifier
                        .size(playerCardSize)
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .offset(y = 56.dp),
                    onClick = onOpenWardrobe
                )

                MenuActionButtons(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp, top = 136.dp, bottom = 96.dp)
                            .widthIn(min = 92.dp, max = buttonAreaWidth),
                        compact = true,
                        showBottomActionsInline = true,
                        onOpenResources = onOpenResources,
                        onOpenSponsor = onOpenSponsor,
                        onOpenTaskList = onOpenTaskList,
                        onOpenSettings = onOpenSettings,
                        onOpenMail = onOpenMail,
                        onOpenHostLobby = onOpenHostLobby,
                        onOpenWorldList = onOpenWorldList,
                        onBack = onBack
                    )
            } else {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 28.dp else 72.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MenuAccountSummary(
                            onlinePlayerIds = onlinePlayerIds
                        )
                        PlayerPreviewCard(
                            modifier = Modifier.size(320.dp),
                            onClick = onOpenWardrobe
                        )
                    }

                    MenuActionButtons(
                        modifier = Modifier.widthIn(min = if (compact) 180.dp else 220.dp),
                        compact = compact,
                        showBottomActionsInline = false,
                        onOpenResources = onOpenResources,
                        onOpenSponsor = onOpenSponsor,
                        onOpenTaskList = onOpenTaskList,
                        onOpenSettings = onOpenSettings,
                        onOpenMail = onOpenMail,
                        onOpenHostLobby = onOpenHostLobby,
                        onOpenWorldList = onOpenWorldList,
                        onBack = onBack
                    )
                }
            }
            if (!portrait) {
                RowV(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                ) {
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
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 24.dp)
                ) {
                    CircleIconButton(
                        icon = "\uF004",
                        tooltip = "支持RDI",
                        bgColor = MaterialColor.PINK_700.color
                    ) {
                        onOpenSponsor()
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(end = 24.dp, bottom = 24.dp)
                ) {
                    CircleIconButton(
                        icon = "\uDB80\uDDDA",
                        tooltip = "任务列表",
                        bgColor = MaterialColor.BLUE_800.color
                    ) {
                        onOpenTaskList()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuActionButtons(
    modifier: Modifier = Modifier,
    compact: Boolean,
    showBottomActionsInline: Boolean,
    onOpenResources: () -> Unit,
    onOpenSponsor: () -> Unit,
    onOpenTaskList: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMail: () -> Unit,
    onOpenHostLobby: () -> Unit,
    onOpenWorldList: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
        modifier = modifier
    ) {
        CircleIconButton(
            "\uDB81\uDC25",
            "退出",
            bgColor = MaterialColor.RED_900.color,
            contentPadding = PaddingValues()
        ) {
            loggedAccount = RAccount.DEFAULT
            onBack()
        }
        CircleIconButton(
            "\uEB51",
            "设置"
        ) {
            onOpenSettings()
        }
        ImageIconButton("grass_block", "资源", bgColor = MaterialColor.GREEN_200.color) {
            onOpenResources()
        }
        CircleIconButton(
            "\uF04B",
            "游玩",
            bgColor = MaterialColor.GREEN_900.color,
            contentPadding = PaddingValues(start = 2.dp)
        ) {
            onOpenHostLobby()
        }
        if (showBottomActionsInline) {
            CircleIconButton(
                "\uDB80\uDDDA",
                "任务",
                bgColor = MaterialColor.BLUE_800.color
            ) {
                onOpenTaskList()
            }
            CircleIconButton(
                "\uF004",
                "支持",
                bgColor = MaterialColor.PINK_700.color
            ) {
                onOpenSponsor()
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
            maxRenderSide = 128,
            noControl = true
        )
    }
}
