package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.ui.CircleIconButton
import calebxzhou.rdi.client.ui.ImageIconButton
import calebxzhou.rdi.client.ui.MainColumn
import calebxzhou.rdi.client.ui.Space8h
import calebxzhou.rdi.client.ui.Space8w
import calebxzhou.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.isDesktop
import calebxzhou.rdi.client.ui.comp.ModpackCard
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.Task

/**
 * calebxzhou @ 2026-01-13 18:27
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModpackListScreen(
    onBack: (() -> Unit) = {},
    onOpenUpload: (() -> Unit) = {},
    onOpenTask: ((Task, Boolean, (() -> Unit)?) -> Unit) = { _, _, _ -> },
    onOpenMcVersions: (() -> Unit) = {},
    onOpenInfo: ((String) -> Unit) = {}
) {
    var modpacks by remember { mutableStateOf<List<Modpack.BriefVo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var onlyMine by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        errorMessage = null
        rdiRequest<List<Modpack.BriefVo>>(
            path = "modpack",
            onOk = { response -> modpacks = response.data ?: emptyList() },
            onErr = { errorMessage = "加载整合包失败: ${it.message}" },
            onDone = { loading = false }
        )
    }

    val visibleModpacks = if (onlyMine) {
        modpacks.filter { it.authorId == loggedAccount._id }
    } else {
        modpacks
    }

    MainColumn {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compactActions = maxWidth < 760.dp

            TitleRow("上传过的整合包", onBack) {
                Checkbox(
                    checked = onlyMine,
                    onCheckedChange = { onlyMine = it }
                )
                Text("我的包")
                Space8w()
                val allow = loggedAccount.hasMsid
                CircleIconButton(
                    "\uDB80\uDFD5",
                    tooltip = if (allow) "传包" else "绑定微软MC账号上传整合包",
                    enabled = allow,
                    showText = true
                ) {
                    onOpenUpload.invoke()
                }
            }
        }
        Space8h()

        if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        errorMessage?.let {
            Text(it, color = MaterialTheme.colors.error)
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(280.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(visibleModpacks, key = { it.id.toHexString() }) { modpack ->
                modpack.ModpackCard(onClick = { onOpenInfo(modpack.id.toHexString()) })
            }
        }
    }
}
