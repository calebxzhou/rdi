package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.ModpackLocalDir
import calebxzhou.rdi.client.service.ModpackService
import calebxzhou.rdi.client.service.ModpackService.startInstall
import calebxzhou.rdi.client.service.getLocalPackDirs
import calebxzhou.rdi.client.service.GameService
import calebxzhou.rdi.client.ui.MaterialColor
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.McVersionCard
import calebxzhou.rdi.client.ui.comp.ModpackManageCard
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.Task
import calebxzhou.rdi.common.model.TaskProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * calebxzhou @ 2026-01-29 18:43
 * Full McVersionScreen — used on both Desktop and Android.
 * Desktop-only features (file import/export, open folder) are gated behind isDesktop.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun McVersionScreen(
    onBack: () -> Unit,
    requiredMcVer: McVersion? = null,
    onOpenTask: ((Task) -> Unit)? = null,
    onOpenPlay: ((McPlayArgs) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var localDirs by remember { mutableStateOf<List<ModpackLocalDir>>(emptyList()) }
    var selectedPack by remember { mutableStateOf<ModpackLocalDir?>(null) }
    var packActionMessage by remember { mutableStateOf<String?>(null) }
    var fclDialogText by remember { mutableStateOf<String?>(null) }
    var fclDialogDirName by remember { mutableStateOf<String?>(null) }

    fun reload() {
        loading = true
        errorMessage = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ModpackService.getLocalPackDirs() }.getOrNull()
            }
            if (result == null) {
                errorMessage = "加载本地整合包失败"
            } else {
                localDirs = result
                selectedPack = selectedPack?.let { selected ->
                    localDirs.firstOrNull { it.versionId == selected.versionId }
                }
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    MainBox {
        MainColumn {
            TitleRow("版本管理", onBack) {
                if (isDesktop) {
                    Text("若下载不成功，可尝试从网盘下载，然后手动导入。")
                    CircleIconButton("\uDB85\uDC03", "从网盘下载", showText = true) {
                        openUrl("https://www.123865.com/s/iWSWvd-Zrtdd")
                    }
                    Space8w()
                    CircleIconButton("\uEE38", "导入MC版本", showText = true) {
                        val files = selectRdiPackFiles() ?: return@CircleIconButton
                        val task = if (files.size == 1) {
                            buildImportPackTask(files.first())
                        } else {
                            Task.Sequence(
                                name = "导入MC版本",
                                subTasks = files.map { buildImportPackTask(it) }
                            )
                        }
                        onOpenTask?.invoke(task)
                    }
                }
            }
            Space8h()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                if (requiredMcVer != null) {
                    Text(
                        text = "请先下载所需版本：${requiredMcVer.mcVer}",
                        color = MaterialTheme.colors.error
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(min = 0.dp, max = 800.dp)
                ) {
                    items(McVersion.entries) { mcver ->
                        McVersionCard(
                            mcver = mcver,
                            highlight = requiredMcVer == mcver,
                            onOpenTask = onOpenTask,
                            onOpenFclDialog = { text, dirName ->
                                fclDialogText = text
                                fclDialogDirName = dirName
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                val selected = selectedPack
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val compactActions = maxWidth < 760.dp
                    val size = 32
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("已安装整合包", style = MaterialTheme.typography.subtitle1)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            packActionMessage?.let {
                                Text(it, color = MaterialTheme.colors.primary)
                            }
                            selected?.let {
                                Text("已选择 ${it.vo.name} ${it.verName}")
                            } ?: Text("选择一个整合包...")
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (compactActions) Arrangement.Start else Arrangement.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isDesktop) {
                                CircleIconButton(
                                    "\uDB82\uDD5D",
                                    "导入RDI整合包",
                                    bgColor = MaterialColor.GREEN_900.color,
                                    size = size,
                                    enabled = true
                                ) {
                                    scope.launch {
                                        val task = withContext(Dispatchers.IO) {
                                            runCatching {
                                                packActionMessage = "开始导入..."
                                                importRdiModpack { msg -> packActionMessage = msg }
                                            }
                                        }.getOrElse {
                                            errorMessage = it.message ?: "导入失败"
                                            packActionMessage = null
                                            return@launch
                                        }
                                        if (onOpenTask != null) {
                                            onOpenTask(task)
                                            reload()
                                            packActionMessage = "导入完成"
                                        } else {
                                            errorMessage = "暂不支持在此页面下载"
                                            packActionMessage = null
                                        }
                                    }
                                }
                            }
                            CircleIconButton(
                                "\uEA81",
                                "删除",
                                size = size,
                                bgColor = MaterialColor.RED_900.color,
                                longPressDelay = 5000L,
                                enabled = selected != null
                            ) {
                                val packdir = selected ?: return@CircleIconButton
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { packdir.dir.deleteRecursivelyNoSymlink() }
                                    }
                                    if (result.isFailure) {
                                        errorMessage = "删除失败: ${result.exceptionOrNull()?.message}"
                                    }
                                    reload()
                                }
                            }
                            CircleIconButton(
                                "\uDB81\uDC53",
                                "重装",
                                size = size,
                                bgColor = MaterialColor.PINK_900.color,
                                enabled = selected != null
                            ) {
                                val packdir = selected ?: return@CircleIconButton
                                scope.launch {
                                    val version = server.makeRequest<Modpack.Version>(
                                        "modpack/${packdir.vo.id}/version/${packdir.verName}"
                                    ).data
                                    if (version == null) {
                                        errorMessage = "未找到对应版本信息，可能已被删除"
                                        return@launch
                                    }
                                    val task =
                                        version.startInstall(packdir.vo.mcVer, packdir.vo.modloader, packdir.vo.name)
                                    if (onOpenTask != null) {
                                        onOpenTask(task)
                                    } else {
                                        errorMessage = "暂不支持在此页面下载"
                                    }
                                }
                            }
                            if (isDesktop) {
                                CircleIconButton(
                                    "\uEAED",
                                    "打开文件夹",
                                    size = size,
                                    bgColor = MaterialColor.YELLOW_900.color,
                                    enabled = selected != null
                                ) {
                                    val packdir = selected ?: return@CircleIconButton
                                    val dir = packdir.dir
                                    if (!dir.exists()) {
                                        errorMessage = "目录不存在: ${dir.absolutePath}"
                                    } else {
                                        runCatching {
                                            openFolder(dir.absolutePath)
                                        }.onFailure {
                                            errorMessage = "无法打开目录: ${it.message}"
                                        }
                                    }
                                }
                            }
                            CircleIconButton("\uEB9B", "测试运行", size = size, enabled = selected != null) {
                                selected?.let { packdir ->
                                    val playArgs = McPlayArgs(
                                        title = "测试运行 - ${packdir.vo.name} ${packdir.verName}",
                                        mcVer = packdir.vo.mcVer,
                                        versionId = packdir.versionId,
                                        "${server.hqUrl}\n${server.ip}:${server.gamePort}\ntest\n25565"
                                    )
                                    onOpenPlay?.invoke(playArgs)
                                }
                            }
                            if (isDesktop) {
                                CircleIconButton(
                                    "\uEF11",
                                    "导出日志",
                                    size = size,
                                    bgColor = MaterialColor.GRAY_900.color,
                                    enabled = selected != null
                                ) {
                                    val packdir = selected ?: return@CircleIconButton
                                    scope.launch {
                                        val result = exportLogsPack(packdir)
                                        if (result.isFailure) {
                                            errorMessage = result.exceptionOrNull()?.message ?: "导出日志失败"
                                        }
                                    }
                                }

                                CircleIconButton(
                                    "\uDB82\uDD5E",
                                    "导出RDI整合包",
                                    size = size,
                                    enabled = selected != null
                                ) {
                                    val packdir = selected ?: return@CircleIconButton
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            packActionMessage = "开始导出..."
                                            exportRdiModpack(packdir) { msg -> packActionMessage = msg }
                                        }
                                        if (result.isFailure) {
                                            errorMessage = result.exceptionOrNull()?.message ?: "导出失败"
                                            packActionMessage = null
                                        } else {
                                            packActionMessage = "导出完成"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                if (!loading && localDirs.isEmpty()) {
                    Text("尚未安装整合包。".asIconText, color = Color.Black)
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(280.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(min = 0.dp, max = 1200.dp)
                ) {
                    items(localDirs, key = { "${it.vo.id}_${it.verName}" }) { packdir ->
                        val versionId = "${packdir.vo.id}_${packdir.verName}"
                        val runningArgs = McPlayStore.current
                        val isRunning = runningArgs?.versionId == versionId && McPlayStore.process?.isAlive == true
                        ModpackManageCard(
                            packdir = packdir,
                            isRunning = isRunning,
                            selected = selectedPack?.versionId == packdir.versionId,
                            onClick = { selectedPack = packdir }
                        )
                    }
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colors.error) }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    fclDialogText?.let {
        AlertDialog(
            onDismissRequest = {
                fclDialogText = null
                fclDialogDirName = null
            },
            title = { Text("FCL下载提示") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = it.asIconText,
                        color = MaterialColor.GRAY_900.color
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    fclDialogText = null
                    fclDialogDirName = null
                }) {
                    Text("取消")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val dirName = fclDialogDirName
                    if (!dirName.isNullOrBlank()) {
                        copyToClipboard(dirName)
                    }
                    openGameLauncher()
                    fclDialogText = null
                    fclDialogDirName = null
                }) {
                    Text("复制版本名称并启动FCL")
                }
            }
        )
    }
}
