package calebxzhou.rdi.client.ui.comp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.shadow
import calebxzhou.rdi.client.model.firstLoader
import calebxzhou.rdi.client.model.firstLoaderVersion
import calebxzhou.rdi.client.model.metadata
import calebxzhou.rdi.client.service.GameService
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.isDesktop
import calebxzhou.rdi.client.ui.loadImageBitmap
import calebxzhou.rdi.common.model.McVersion

/**
 * calebxzhou @ 2026-01-29 18:44
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McVersionCard(
    mcver: McVersion,
    highlight: Boolean = false,
    onOpenFclDialog: (String, String) -> Unit,
    onOpenTaskList: ((String) -> Unit)? = null,
) {
    val iconBitmap = remember(mcver) {
        loadImageBitmap(mcver.icon)
    }
    val shape = RoundedCornerShape(16.dp)
    fun submitTask(task: calebxzhou.rdi.common.model.Task2) {
        val runId = ClientTaskManager.submit(task)
        onOpenTaskList?.invoke(runId)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (highlight) {
                    Modifier
                        .shadow(8.dp, shape, clip = false)
                        .border(2.dp, MaterialColor.PURPLE_500.color, shape)
                } else {
                    Modifier
                }
            ),
        color = Color(0xFFF9F9FB),
        shape = shape,
        elevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        MaterialColor.GRAY_200.color,
                        androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = "MC ${mcver.mcVer}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "MC ${mcver.mcVer}",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialColor.GRAY_900.color
                    )
                    Text(
                        text = mcver.loaderVersions.keys.joinToString(" / ") { it.name.lowercase() },
                        style = MaterialTheme.typography.body2,
                        color = MaterialColor.GRAY_700.color
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isDesktop) {
                        val enabled = mcver.enabled
                        CircleIconButton("\uF019", "下载全部", enabled = enabled) {
                            submitTask(GameService.downloadVersionTask2(mcver, mcver.firstLoader))
                        }
                        mcver.loaderVersions.forEach { (loader, _) ->
                            CircleIconButton(
                                "\uEEFF",
                                "安装最新${loader.name.lowercase()}",
                                bgColor = MaterialColor.TEAL_900.color,
                                enabled = enabled
                            ) {
                                submitTask(GameService.downloadLoaderTask2(mcver, loader))
                            }
                        }
                    } else {
                        CircleIconButton("\uF019", "使用FCL下载") {
                            val guideText = buildString {
                                appendLine("1.打开FCL启动器")
                                appendLine("2.点击左侧的\uDB80\uDD62按钮")
                                appendLine("3.在上方选择“游戏”")
                                appendLine("4.选择${mcver.mcVer}")
                                appendLine("5.点击${mcver.firstLoader.name}")
                                appendLine("6.点击版本${mcver.firstLoaderVersion.ver}")
                                appendLine("7.填入名称${mcver.firstLoaderVersion.dirName}，必须一模一样，填错会导致无法启动！填错会导致无法启动！填错会导致无法启动！")
                                append("8.点击名称栏右侧的\uDB80\uDDDA等待安装完成")
                            }
                            onOpenFclDialog.invoke(
                                guideText,
                                mcver.firstLoaderVersion.dirName
                            )
                        }
                    }
                }
            }
        }
    }
}
