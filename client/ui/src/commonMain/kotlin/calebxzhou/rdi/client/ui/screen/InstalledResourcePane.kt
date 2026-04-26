package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.client.service.ModpackLocalDir
import calebxzhou.rdi.client.service.ModpackService
import calebxzhou.rdi.client.service.ModpackService.modpackInstallTaskKey
import calebxzhou.rdi.client.service.ModpackService.startInstallTask2
import calebxzhou.rdi.client.service.codeeditor.CodeEditorValidation
import calebxzhou.rdi.client.service.codeeditor.CodeLanguage
import calebxzhou.rdi.client.service.codeeditor.validateCodeContent
import calebxzhou.rdi.client.service.getLocalPackDirs
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.CodeEditor
import calebxzhou.rdi.client.ui.comp.LoadingFlowGrid
import calebxzhou.rdi.client.ui.comp.ModpackManageCard
import calebxzhou.rdi.common.isExcludedConfigPath
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.Modpack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

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

private const val MAX_LOCAL_CONFIG_FILE_BYTES = 2L * 1024 * 1024
private val localConfigEditableExtensions = setOf(
    "cfg",
    "conf",
    "ini",
    "json",
    "json5",
    "js",
    "lang",
    "list",
    "properties",
    "snbt",
    "toml",
    "txt",
    "yaml",
    "yml",
    "zs"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InstalledResourceScreen(
    onBack: () -> Unit,
    onOpenPlay: ((McPlayArgs) -> Unit)? = null,
    onOpenMcVersionManage: (() -> Unit)? = null,
    onOpenTaskList: ((String) -> Unit)? = null
) {
    var titleActions by remember { mutableStateOf<ResourceScreenTitleActions?>(null) }
    MainBox {
        MainColumn {
            TitleRow("已安装资源", onBack) {
                titleActions?.invoke(this)
            }
            Space8h()
            InstalledResourcePane(
                onOpenPlay = onOpenPlay,
                onOpenMcVersionManage = onOpenMcVersionManage,
                onOpenTaskList = onOpenTaskList,
                onTitleActionsChange = { titleActions = it }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InstalledResourcePane(
    onOpenPlay: ((McPlayArgs) -> Unit)? = null,
    onOpenMcVersionManage: (() -> Unit)? = null,
    onOpenTaskList: ((String) -> Unit)? = null,
    showMcVersionShortcut: Boolean = true,
    showPaneActions: Boolean = false,
    onTitleActionsChange: (ResourceScreenTitleActions?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var localDirs by remember { mutableStateOf<List<ModpackLocalDir>>(emptyList()) }
    var selectedPack by remember { mutableStateOf<ModpackLocalDir?>(null) }
    var packActionMessage by remember { mutableStateOf<String?>(null) }
    var deleteConfirmPack by remember { mutableStateOf<ModpackLocalDir?>(null) }
    var deleteIncludedMods by remember { mutableStateOf(false) }
    var reinstallConfirmPack by remember { mutableStateOf<ModpackLocalDir?>(null) }
    var copyDataSourcePack by remember { mutableStateOf<ModpackLocalDir?>(null) }
    var copyDataTargetVersionId by remember { mutableStateOf<String?>(null) }
    var copyDataSelectedKeys by remember { mutableStateOf(personalDataCopyEntries.map { it.key }.toSet()) }
    var configEditorPack by remember { mutableStateOf<ModpackLocalDir?>(null) }
    var localConfigFilesLoading by remember { mutableStateOf(false) }
    var localConfigContentLoading by remember { mutableStateOf(false) }
    var localConfigSaving by remember { mutableStateOf(false) }
    var localConfigFiles by remember { mutableStateOf<List<Host.ConfigFileEntry>>(emptyList()) }
    var selectedLocalConfigPath by remember { mutableStateOf<String?>(null) }
    var localConfigEditorText by remember { mutableStateOf("") }
    var localConfigOriginalText by remember { mutableStateOf("") }
    var localConfigSyntaxErrorMessage by remember { mutableStateOf<String?>(null) }
    var localConfigStatusMessage by remember { mutableStateOf<String?>(null) }
    val localConfigDirty = selectedLocalConfigPath != null && localConfigEditorText != localConfigOriginalText

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

    fun resetLocalConfigEditorState(clearPack: Boolean = false) {
        if (clearPack) {
            configEditorPack = null
        }
        localConfigFilesLoading = false
        localConfigContentLoading = false
        localConfigSaving = false
        localConfigFiles = emptyList()
        selectedLocalConfigPath = null
        localConfigEditorText = ""
        localConfigOriginalText = ""
        localConfigSyntaxErrorMessage = null
        localConfigStatusMessage = null
    }

    fun loadLocalConfigFile(packdir: ModpackLocalDir, relativePath: String) {
        val versionId = packdir.versionId
        localConfigContentLoading = true
        localConfigStatusMessage = "正在读取 $relativePath"
        localConfigSyntaxErrorMessage = null
        selectedLocalConfigPath = relativePath
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val file = resolveLocalConfigFile(packdir.dir, relativePath)
                    file.readText()
                }
            }
            if (configEditorPack?.versionId != versionId) return@launch
            result.onSuccess { content ->
                selectedLocalConfigPath = relativePath
                localConfigEditorText = content
                localConfigOriginalText = content
                localConfigSyntaxErrorMessage = validateCodeContent(
                    text = content,
                    language = CodeLanguage.fromPath(relativePath)
                )?.takeIf { !it.isValid }?.message
                localConfigStatusMessage = "已打开 $relativePath"
            }.onFailure {
                errorMessage = it.message ?: "读取配置文件失败"
            }
            localConfigContentLoading = false
        }
    }

    fun loadLocalConfigFiles(packdir: ModpackLocalDir, preferredPath: String? = selectedLocalConfigPath) {
        val versionId = packdir.versionId
        configEditorPack = packdir
        localConfigFilesLoading = true
        localConfigStatusMessage = "正在扫描配置文件..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { collectLocalConfigFiles(packdir.dir) }
            }
            if (configEditorPack?.versionId != versionId) return@launch
            result.onSuccess { files ->
                localConfigFiles = files
                if (files.isEmpty()) {
                    selectedLocalConfigPath = null
                    localConfigEditorText = ""
                    localConfigOriginalText = ""
                    localConfigSyntaxErrorMessage = null
                    localConfigStatusMessage = "当前整合包没有可编辑配置文件"
                    return@onSuccess
                }

                when {
                    preferredPath != null && files.any { it.path == preferredPath } && selectedLocalConfigPath == null -> {
                        loadLocalConfigFile(packdir, preferredPath)
                    }

                    selectedLocalConfigPath == null -> {
                        loadLocalConfigFile(packdir, files.first().path)
                    }

                    selectedLocalConfigPath != null && files.none { it.path == selectedLocalConfigPath } -> {
                        if (!localConfigDirty) {
                            loadLocalConfigFile(packdir, files.first().path)
                        } else {
                            localConfigStatusMessage = "当前文件已不在配置列表中，请先保存或还原内容"
                        }
                    }

                    else -> {
                        localConfigStatusMessage = "已加载${files.size}个配置文件"
                    }
                }
            }.onFailure {
                errorMessage = it.message ?: "加载配置文件列表失败"
                localConfigStatusMessage = null
            }
            localConfigFilesLoading = false
        }
    }

    fun saveLocalConfigFile(packdir: ModpackLocalDir) {
        val relativePath = selectedLocalConfigPath ?: run {
            errorMessage = "请先选择配置文件"
            return
        }
        val versionId = packdir.versionId
        localConfigSaving = true
        localConfigStatusMessage = "正在保存 $relativePath"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val file = resolveLocalConfigFile(packdir.dir, relativePath)
                    file.writeText(localConfigEditorText)
                    Host.ConfigFileEntry(
                        path = relativePath,
                        size = file.length(),
                        updateTime = file.lastModified()
                    )
                }
            }
            if (configEditorPack?.versionId != versionId) return@launch
            result.onSuccess { saved ->
                localConfigOriginalText = localConfigEditorText
                localConfigSyntaxErrorMessage = null
                localConfigStatusMessage = "已保存 ${saved.path}"
                localConfigFiles = localConfigFiles.map { entry ->
                    if (entry.path == saved.path) saved else entry
                }
                if (localConfigFiles.none { it.path == saved.path }) {
                    localConfigFiles = (localConfigFiles + saved).sortedBy { it.path.lowercase() }
                }
            }.onFailure {
                errorMessage = it.message ?: "保存配置文件失败"
            }
            localConfigSaving = false
        }
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

    @Composable
    fun PackGrid(
        modifier: Modifier = Modifier,
        portrait: Boolean
    ) {
        LoadingFlowGrid(
            loading = loading,
            items = localDirs,
            emptyText = "尚未安装整合包。",
            modifier = modifier
        ) { packdir ->
            val versionId = "${packdir.vo.id}_${packdir.verName}"
            val runningArgs = McPlayStore.current
            val isRunning = runningArgs?.versionId == versionId && McPlayStore.process?.isAlive == true
            ModpackManageCard(
                modifier = Modifier.widthIn(max = 350.dp),
                packdir = packdir,
                isRunning = isRunning,
                selected = selectedPack?.versionId == packdir.versionId,
                miniMode = true,
                onClick = { selectedPack = packdir }
            )
        }
    }


    fun importRdiModpack() {
        scope.launch {
            val task = withContext(Dispatchers.IO) {
                runCatching {
                    packActionMessage = "开始导入..."
                    importRdiModpackTask2 { msg -> packActionMessage = msg }
                }
            }.getOrElse {
                errorMessage = it.message ?: "导入失败"
                packActionMessage = null
                return@launch
            }
            val runId = ClientTaskManager.submit(task)
            if (onOpenTaskList != null) {
                onOpenTaskList(runId)
            } else {
                packActionMessage = "已加入任务列表"
            }
        }
    }

    val titleActions: ResourceScreenTitleActions =
        remember(showMcVersionShortcut, onOpenMcVersionManage, onOpenTaskList) {
            {
                CircleIconButton(
                    "\uDB82\uDD5D",
                    "导入RDI整合包",
                    bgColor = MaterialColor.GREEN_800.color,
                ) {
                    importRdiModpack()
                }
                if (showMcVersionShortcut) {
                    Space8w()
                    ImageIconButton("grass_block", "MC资源", bgColor = MaterialColor.GREEN_200.color) {
                        onOpenMcVersionManage?.invoke()
                    }
                }
                Space8w()
                CircleIconButton(
                    "\uDB86\uDDD8",
                    "网盘备用下包"
                ) {
                    openUrl("https://www.123684.com/s/iWSWvd-Gjtdd")
                }
            }
        }

    if (!showPaneActions) {
        SideEffect {
            onTitleActionsChange(titleActions)
        }
        DisposableEffect(Unit) {
            onDispose {
                onTitleActionsChange(null)
            }
        }
    }

    @Composable
    fun PackActionPanel(
        selected: ModpackLocalDir?,
        portrait: Boolean,
        modifier: Modifier = Modifier
    ) {
        val primaryActionColor = MaterialColor.INDIGO_800.color
        val secondaryActionColor = MaterialColor.BLUE_GRAY_800.color
        val utilityActionColor = MaterialColor.TEAL_800.color
        val size = 32

        @Composable
        fun ActionButtons() {
            CircleIconButton(
                "\uEA81",
                "删除",
                size = size,
                bgColor = MaterialColor.RED_900.color,
                enabled = selected != null
            ) {
                deleteConfirmPack = selected
                deleteIncludedMods = false
            }
            if (isDesktop) {
                CircleIconButton(
                    "\uDB82\uDD5E",
                    "导出RDI包",
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
                "\uE713",
                "配置文件",
                size = size,
                enabled = selected != null,
                bgColor = MaterialColor.DEEP_PURPLE_700.color
            ) {
                val packdir = selected ?: return@CircleIconButton
                resetLocalConfigEditorState()
                configEditorPack = packdir
                loadLocalConfigFiles(packdir)
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
                        "${server.hqUrl}\n" +
                                "127.0.0.1:55667\n" +
                                "test\n" +
                                "55555\n" +
                                "${loggedAccount.uuid}\n" +
                                loggedAccount.name
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

        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = if (portrait) Alignment.Start else Alignment.CenterHorizontally
        ) {
            Text(
                selected?.let { "${it.vo.name} ${it.verName}" } ?: "请选择整合包",
                style = MaterialTheme.typography.subtitle1
            )
            packActionMessage?.let {
                Text(it, color = MaterialTheme.colors.primary)
            }
            if (portrait) {
                FlowRowV(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButtons()
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButtons()
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        FlowRowV(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "管理已安装整合包",
                style = MaterialTheme.typography.subtitle1
            )
            if (showPaneActions) {
                RowV(horizontalArrangement = Arrangement.End) {
                    titleActions(this)
                }
            }
        }
        Space8h()
        errorMessage?.let {
            Text(it, color = MaterialTheme.colors.error)
            Space8h()
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val portrait = maxHeight > maxWidth
            val selected = selectedPack
            if (portrait) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PackActionPanel(
                        selected = selected,
                        portrait = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    PackGrid(
                        portrait = true,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PackGrid(
                            portrait = false,
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .width(140.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        PackActionPanel(
                            selected = selected,
                            portrait = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    configEditorPack?.let { packdir ->
        Dialog(
            onDismissRequest = { resetLocalConfigEditorState(clearPack = true) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.88f),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colors.surface,
                elevation = 10.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(min = 320.dp, max = 420.dp)
                            .fillMaxHeight()
                    ) {
                        HostConfigEditor(
                            files = localConfigFiles,
                            selectedPath = selectedLocalConfigPath,
                            loadingFiles = localConfigFilesLoading,
                            statusMessage = localConfigStatusMessage,
                            onSelectFile = { path ->
                                if (localConfigDirty && path != selectedLocalConfigPath) {
                                    errorMessage = "当前配置文件有未保存修改，请先保存或还原"
                                } else {
                                    loadLocalConfigFile(packdir, path)
                                }
                            },
                            onReloadList = { loadLocalConfigFiles(packdir) }
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TitleRow(
                            title = "配置文件 ${packdir.vo.name} ${packdir.verName}",
                            onBack = { resetLocalConfigEditorState(clearPack = true) }
                        ) {
                            Text(
                                text = when {
                                    localConfigSaving -> "保存中..."
                                    localConfigContentLoading -> "读取中..."
                                    localConfigSyntaxErrorMessage != null -> localConfigSyntaxErrorMessage.orEmpty()
                                    localConfigDirty -> "有未保存修改"
                                    localConfigStatusMessage != null -> localConfigStatusMessage.orEmpty()
                                    else -> ""
                                },
                                color = when {
                                    localConfigSyntaxErrorMessage != null -> MaterialColor.RED_800.color
                                    localConfigDirty -> MaterialColor.ORANGE_900.color
                                    else -> MaterialColor.GRAY_700.color
                                },
                                style = MaterialTheme.typography.caption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 420.dp)
                            )
                            Space8w()
                            CircleIconButton(
                                icon = "\uDB81\uDC50",
                                tooltip = "还原",
                                enabled = selectedLocalConfigPath != null && !localConfigContentLoading && !localConfigSaving,
                                showText = false
                            ) {
                                val path = selectedLocalConfigPath ?: run {
                                    errorMessage = "请先选择配置文件"
                                    return@CircleIconButton
                                }
                                loadLocalConfigFile(packdir, path)
                            }
                            Space8w()
                            CircleIconButton(
                                icon = "\uF0C7",
                                tooltip = "保存",
                                enabled = selectedLocalConfigPath != null && localConfigDirty && !localConfigContentLoading &&
                                        !localConfigSaving && localConfigSyntaxErrorMessage == null,
                                showText = false,
                                bgColor = MaterialColor.GREEN_900.color
                            ) {
                                if (localConfigSyntaxErrorMessage != null) {
                                    errorMessage = localConfigSyntaxErrorMessage
                                } else {
                                    saveLocalConfigFile(packdir)
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(bottom = 4.dp)
                        ) {
                            when {
                                localConfigContentLoading -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }

                                selectedLocalConfigPath == null -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("请选择左侧配置文件", color = MaterialColor.GRAY_700.color)
                                    }
                                }

                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFFFDFDFD), MaterialTheme.shapes.medium)
                                            .padding(1.dp)
                                    ) {
                                        CodeEditor(
                                            text = localConfigEditorText,
                                            enabled = !localConfigSaving,
                                            language = CodeLanguage.fromPath(selectedLocalConfigPath),
                                            modifier = Modifier.fillMaxSize(),
                                            onValueChange = {
                                                localConfigEditorText = it
                                                localConfigSyntaxErrorMessage = validateCodeContent(
                                                    text = it,
                                                    language = CodeLanguage.fromPath(selectedLocalConfigPath)
                                                )?.takeIf { validation -> !validation.isValid }?.message
                                            },
                                            onValidationChange = { validation: CodeEditorValidation? ->
                                                localConfigSyntaxErrorMessage =
                                                    validation?.takeIf { !it.isValid }?.message
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteConfirmPack?.let { packdir ->
        AlertDialog(
            onDismissRequest = { deleteConfirmPack = null },
            title = { Text("确认删除") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "确认删除整合包${packdir.vo.name} ${packdir.verName}吗\n" +
                                "截图、单机存档等数据将消失，重要数据请备份"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                deleteIncludedMods = !deleteIncludedMods
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = deleteIncludedMods,
                            onCheckedChange = { deleteIncludedMods = it }
                        )
                        Text("一并删除包中含有的Mod")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmPack = null }) { Text("取消") }
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteConfirmPack = null
                    scope.launch {
                        val result = ModpackService.deleteLocalPack(
                            packdir = packdir,
                            deleteIncludedMods = deleteIncludedMods
                        )
                        if (result.isFailure) {
                            errorMessage = "删除失败: ${result.exceptionOrNull()?.message}"
                        }
                        reload()
                    }
                }) { Text("确认") }
            }
        )
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
                        val runId = ClientTaskManager.submit(
                            task = version.startInstallTask2(packdir.vo.mcVer, packdir.vo.modloader, packdir.vo.name),
                            dedupeKey = modpackInstallTaskKey(version.modpackId, version.name)
                        )
                        if (onOpenTaskList != null) {
                            onOpenTaskList(runId)
                        } else {
                            packActionMessage = "已加入任务列表"
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
                                    copyDataSelectedKeys =
                                        if (checked) copyDataSelectedKeys - entry.key else copyDataSelectedKeys + entry.key
                                },
                                shape = MaterialTheme.shapes.medium,
                                color = if (checked) MaterialTheme.colors.primary.copy(alpha = 0.08f) else MaterialTheme.colors.surface,
                                border = BorderStroke(
                                    width = if (checked) 2.dp else 1.dp,
                                    color = if (checked) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(
                                        alpha = 0.18f
                                    )
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
                                            copyDataSelectedKeys =
                                                if (isChecked) copyDataSelectedKeys + entry.key else copyDataSelectedKeys - entry.key
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
                                            color = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(
                                                alpha = 0.18f
                                            )
                                        ),
                                        elevation = 0.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { copyDataTargetVersionId = target.versionId })
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
                                        packActionMessage =
                                            "已复制个人数据到${targetPack.vo.name} ${targetPack.verName}"
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

private fun collectLocalConfigFiles(packDir: File): List<Host.ConfigFileEntry> {
    val discovered = linkedMapOf<String, Host.ConfigFileEntry>()
    val rootDir = packDir.resolve("config")
    if (!rootDir.exists() || !rootDir.isDirectory || Files.isSymbolicLink(rootDir.toPath())) {
        return emptyList()
    }
    rootDir.walkTopDown()
        .onEnter { dir -> !Files.isSymbolicLink(dir.toPath()) }
        .filter { it.isEditableLocalConfigFile() }
        .forEach { file ->
            val relativePath = file.relativeTo(rootDir).path.replace('\\', '/')
            if (relativePath.isExcludedConfigPath()) return@forEach
            discovered[relativePath] = Host.ConfigFileEntry(
                path = relativePath,
                size = file.length(),
                updateTime = file.lastModified()
            )
        }

    return discovered.values.sortedBy { it.path.lowercase() }
}

private fun resolveLocalConfigFile(packDir: File, relativePath: String): File {
    val normalizedRelative = relativePath.replace('\\', '/').trimStart('/')
    val canonicalBase = packDir.resolve("config").canonicalFile
    val resolved = canonicalBase.resolve(normalizedRelative).canonicalFile
    val canonicalBasePath = canonicalBase.path
    val resolvedPath = resolved.path
    val withinBase = resolvedPath == canonicalBasePath ||
            resolvedPath.startsWith(canonicalBasePath + File.separator)
    if (!withinBase) {
        throw IllegalArgumentException("非法配置文件路径: $relativePath")
    }
    return resolved
}

private fun File.isEditableLocalConfigFile(): Boolean {
    if (!exists() || !isFile) return false
    if (length() > MAX_LOCAL_CONFIG_FILE_BYTES) return false
    return extension.lowercase() in localConfigEditableExtensions
}
