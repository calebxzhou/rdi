package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.ModpackLocalDir
import calebxzhou.rdi.client.service.ModpackService
import calebxzhou.rdi.client.service.ModpackService.startInstall
import calebxzhou.rdi.client.service.getLocalPackDirs
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.ModpackManageCard
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PersonalDataCopyEntry(
    val key: String,
    val label: String,
    val path: String,
    val isDirectory: Boolean = true
)

private val personalDataCopyEntries = listOf(
    PersonalDataCopyEntry("saves", "单机存档", "saves"),
    PersonalDataCopyEntry("resourcepacks", "资源包", "resourcepacks"),
    PersonalDataCopyEntry("shaderpacks", "光影包", "shaderpacks"),
    PersonalDataCopyEntry("schematics", "投影蓝图", "schematics"),
    PersonalDataCopyEntry("waypoints", "旅行地图坐标点", "waypoints"),
    PersonalDataCopyEntry("xaero", "Xaero小地图坐标点", "xaero"),
    PersonalDataCopyEntry("options", "键位画质设置", "options.txt", isDirectory = false)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModpackLocalManageScreen(
    onBack: () -> Unit,
    onOpenTask: ((Task) -> Unit)? = null,
    onOpenPlay: ((McPlayArgs) -> Unit)? = null,
    onOpenModpackList: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var localDirs by remember { mutableStateOf<List<ModpackLocalDir>>(emptyList()) }
    var selectedPack by remember { mutableStateOf<ModpackLocalDir?>(null) }
    var packActionMessage by remember { mutableStateOf<String?>(null) }
    var reinstallConfirmPack by remember { mutableStateOf<ModpackLocalDir?>(null) }
    var copyDataSourcePack by remember { mutableStateOf<ModpackLocalDir?>(null) }
    var copyDataTargetVersionId by remember { mutableStateOf<String?>(null) }
    var copyDataSelectedKeys by remember { mutableStateOf(personalDataCopyEntries.map { it.key }.toSet()) }

    fun copyPersonalData(sourceDir: java.io.File, targetDir: java.io.File, selectedKeys: Set<String>) {
        personalDataCopyEntries.filter { it.key in selectedKeys }.forEach { entry ->
            val sourceChild = sourceDir.resolve(entry.path)
            if (!sourceChild.exists()) return@forEach
            val targetChild = targetDir.resolve(entry.path)
            if (entry.isDirectory) {
                sourceChild.copyRecursively(targetChild, overwrite = true)
            } else {
                targetChild.parentFile?.mkdirs()
                sourceChild.copyTo(targetChild, overwrite = true)
            }
        }
    }

    fun resetCopyDialog() {
        copyDataSourcePack = null
        copyDataTargetVersionId = null
        copyDataSelectedKeys = personalDataCopyEntries.map { it.key }.toSet()
    }

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
            TitleRow("整合包管理", onBack) {
                errorMessage?.let { Text(it, color = MaterialTheme.colors.error) }
                CircleIconButton(
                    "\uDB86\uDDD8",
                    "下载整合包"
                ) {
                    onOpenModpackList?.invoke()
                }
            }
            Space8h()
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.Top
            ) {
                val selected = selectedPack
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "管理已安装整合包",
                        style = MaterialTheme.typography.subtitle1
                    )
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
                        columns = GridCells.Adaptive(320.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
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
                }

                Column(
                    modifier = Modifier
                        .widthIn(min = 220.dp, max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val primaryActionColor = MaterialColor.INDIGO_800.color
                    val secondaryActionColor = MaterialColor.BLUE_GRAY_800.color
                    val utilityActionColor = MaterialColor.TEAL_800.color
                    val size = 32
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            selected?.let { "${it.vo.name} ${it.verName}" } ?: "请选择整合包",
                            style = MaterialTheme.typography.subtitle1
                        )
                        packActionMessage?.let {
                            Text(it, color = MaterialTheme.colors.primary)
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
                        if (isDesktop) {
                            CircleIconButton(
                                "\uDB82\uDD5D",
                                "导入RDI整合包",
                                bgColor = MaterialColor.GREEN_800.color,
                                size = size
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
                            CircleIconButton(
                                "\uDB82\uDD5E",
                                "导出RDI整合包",
                                size = size,
                                bgColor = MaterialColor.BLUE_800.color,
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
                        CircleIconButton(
                            "\uDB81\uDC53",
                            "重新下载",
                            size = size,
                            enabled = selected != null,
                            bgColor = secondaryActionColor
                        ) {
                            reinstallConfirmPack = selected
                        }
                        CircleIconButton(
                            "\uE8C8",
                            "复制数据",
                            size = size,
                            enabled = selected != null && localDirs.size > 1,
                            bgColor = utilityActionColor
                        ) {
                            val packdir = selected ?: return@CircleIconButton
                            copyDataSourcePack = packdir
                            copyDataTargetVersionId = localDirs.firstOrNull {
                                it.versionId != packdir.versionId
                            }?.versionId
                            copyDataSelectedKeys = personalDataCopyEntries.map { it.key }.toSet()
                        }
                        if (isDesktop) {
                            CircleIconButton(
                                "\uEAED",
                                "安装目录",
                                size = size,
                                enabled = selected != null,
                                bgColor = MaterialColor.GREEN_800.color
                            ) {
                                val packdir = selected ?: return@CircleIconButton
                                val dir = packdir.dir
                                if (!dir.exists()) {
                                    errorMessage = "目录不存在: ${dir.absolutePath}"
                                } else {
                                    runCatching { openFolder(dir.absolutePath) }
                                        .onFailure { errorMessage = "无法打开目录: ${it.message}" }
                                }
                            }
                        }
                        CircleIconButton(
                            "\uEB9B",
                            "单机运行",
                            size = size,
                            enabled = selected != null,
                            bgColor = primaryActionColor
                        ) {
                            selected?.let { packdir ->
                                val playArgs = McPlayArgs(
                                    title = "单机 - ${packdir.vo.name} ${packdir.verName}",
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
                                bgColor = MaterialColor.GRAY_800.color,
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

                        }
                    }
                }
            }
        }
    }

    reinstallConfirmPack?.let { packdir ->
        AlertDialog(
            onDismissRequest = { reinstallConfirmPack = null },
            title = { Text("确认重装") },
            text = {
                Text(
                    "将重装${packdir.vo.name} ${packdir.verName}。\n" +
                        "重装会清空该整合包的 所有个人数据\n--包括: 单机存档 小地图路径点 资源/光影包 日志等\n确定继续吗？"
                )
            },
            dismissButton = {
                TextButton(onClick = { reinstallConfirmPack = null }) { Text("取消") }
            },
            confirmButton = {
                TextButton(onClick = {
                    reinstallConfirmPack = null
                    scope.launch {
                        val version = server.makeRequest<Modpack.Version>(
                            "modpack/${packdir.vo.id}/version/${packdir.verName}"
                        ).data
                        if (version == null) {
                            errorMessage = "未找到对应版本信息，可能已被删除"
                            return@launch
                        }
                        val task = version.startInstall(packdir.vo.mcVer, packdir.vo.modloader, packdir.vo.name)
                        if (onOpenTask != null) {
                            onOpenTask(task)
                        } else {
                            errorMessage = "暂不支持在此页面下载"
                        }
                    }
                }) { Text("确认重装") }
            }
        )
    }

    copyDataSourcePack?.let { sourcePack ->
        val targetCandidates = localDirs.filter { it.versionId != sourcePack.versionId }
        val selectedTarget = targetCandidates.firstOrNull { it.versionId == copyDataTargetVersionId }
        Dialog(
            onDismissRequest = { resetCopyDialog() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.75f),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colors.surface,
                elevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
                    Text("复制个人数据", style = MaterialTheme.typography.h6)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("把个人数据复制到另一个整合包，请先勾选要复制的内容，再选择目标整合包。")
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        personalDataCopyEntries.forEach { entry ->
                            val checked = entry.key in copyDataSelectedKeys
                            Surface(
                                modifier = Modifier.clickable {
                                    copyDataSelectedKeys = if (checked) copyDataSelectedKeys - entry.key else copyDataSelectedKeys + entry.key
                                },
                                shape = MaterialTheme.shapes.medium,
                                color = if (checked) MaterialTheme.colors.primary.copy(alpha = 0.08f) else MaterialTheme.colors.surface,
                                border = BorderStroke(
                                    width = if (checked) 2.dp else 1.dp,
                                    color = if (checked) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.18f)
                                ),
                                elevation = 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            copyDataSelectedKeys = if (isChecked) copyDataSelectedKeys + entry.key else copyDataSelectedKeys - entry.key
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(entry.label)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("来源：${sourcePack.vo.name} ${sourcePack.verName}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                    ) {
                        if (targetCandidates.isEmpty()) {
                            Text("没有可复制的目标整合包")
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                targetCandidates.forEach { target ->
                                    val isSelected = copyDataTargetVersionId == target.versionId
                                    Surface(
                                        modifier = Modifier.widthIn(min = 180.dp, max = 220.dp).clickable {
                                            copyDataTargetVersionId = target.versionId
                                        },
                                        shape = MaterialTheme.shapes.medium,
                                        color = if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.08f) else MaterialTheme.colors.surface,
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.18f)
                                        ),
                                        elevation = 0.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = isSelected, onClick = { copyDataTargetVersionId = target.versionId })
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = target.vo.name.ifBlank { "未命名整合包" },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = TextUnit(14f, TextUnitType.Sp)
                                                )
                                                Text(
                                                    text = target.verName,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { resetCopyDialog() }) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            enabled = selectedTarget != null && copyDataSelectedKeys.isNotEmpty(),
                            onClick = {
                                val targetPack = selectedTarget ?: return@TextButton
                                val selectedKeys = copyDataSelectedKeys
                                resetCopyDialog()
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { copyPersonalData(sourcePack.dir, targetPack.dir, selectedKeys) }
                                    }
                                    if (result.isFailure) {
                                        errorMessage = "复制个人数据失败: ${result.exceptionOrNull()?.message}"
                                        packActionMessage = null
                                    } else {
                                        errorMessage = null
                                        packActionMessage = "已复制个人数据到${targetPack.vo.name} ${targetPack.verName}"
                                    }
                                }
                            }
                        ) {
                            Text("复制")
                        }
                    }
                }
            }
        }
    }
}
