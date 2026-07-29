package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.service.codeeditor.CodeEditorValidation
import calebxzhou.rdi.client.service.codeeditor.CodeLanguage
import calebxzhou.rdi.client.service.codeeditor.validateCodeContent
import calebxzau.rdi.client.ui.AlertErr
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.RRow
import calebxzau.rdi.client.ui.RThinTextField
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.asIconText
import calebxzhou.rdi.client.ui.comp.CodeEditor
import calebxzhou.rdi.client.ui.comp.RVerticalScrollbar
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.util.toFriendlyDateTime
import io.ktor.http.HttpMethod
import org.bson.types.ObjectId

private val hostFileExplorerHeaderHeight = 42.dp

@Composable
fun HostFileExplorer(
    hostId: ObjectId,
    modifier: Modifier = Modifier
) = HostFileExplorer(hostId.toHexString(), "host", modifier)

@Composable
fun HostFileExplorer(
    hostId: String,
    apiRoot: String,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var files by remember(hostId) { mutableStateOf<List<Host.FileEntry>>(emptyList()) }
    var selectedPath by remember(hostId) { mutableStateOf<String?>(null) }
    var initialLoading by remember(hostId) { mutableStateOf(false) }
    var loadingDirPath by remember(hostId) { mutableStateOf<String?>(null) }
    var loadingContent by remember(hostId) { mutableStateOf(false) }
    var saving by remember(hostId) { mutableStateOf(false) }
    var statusMessage by remember(hostId) { mutableStateOf<String?>(null) }
    var errorMessage by remember(hostId) { mutableStateOf<String?>(null) }
    var editorOpen by remember(hostId) { mutableStateOf(false) }
    var editorText by remember(hostId) { mutableStateOf("") }
    var originalText by remember(hostId) { mutableStateOf("") }
    var syntaxErrorMessage by remember(hostId) { mutableStateOf<String?>(null) }
    var searchResults by remember(hostId) { mutableStateOf<List<Host.FileEntry>>(emptyList()) }
    var activeSearchQuery by remember(hostId) { mutableStateOf("") }
    val dirty = selectedPath != null && editorText != originalText

    fun mergeHostFileEntries(parentPath: String, entries: List<Host.FileEntry>) {
        files = (files
            .filterNot { it.path.directParentPath() == parentPath }
                + entries)
            .distinctBy { it.path }
            .sortedWith(compareBy<Host.FileEntry> { it.path.count { ch -> ch == '/' } }.thenBy { it.path.lowercase() })
    }

    fun loadFile(path: String) {
        val switchingFile = selectedPath != path
        selectedPath = path
        statusMessage = "正在读取 $path"
        errorMessage = null
        syntaxErrorMessage = null
        if (switchingFile) {
            editorText = ""
            originalText = ""
        }
        loadingContent = true
        scope.rdiRequest<Host.FileContentVo>(
            path = "$apiRoot/$hostId/files/file",
            params = mapOf("path" to path),
            onOk = { response ->
                val file = response.data ?: run {
                    errorMessage = "读取文件失败"
                    return@rdiRequest
                }
                selectedPath = file.path
                editorText = file.content
                originalText = file.content
                syntaxErrorMessage = validateCodeContent(
                    text = file.content,
                    language = CodeLanguage.fromPath(file.path)
                )?.takeIf { !it.isValid }?.message
                statusMessage = "已打开 ${file.path}"
            },
            onErr = { errorMessage = it.message ?: "读取文件失败" },
            onDone = { loadingContent = false }
        )
    }

    fun loadFiles(path: String = "", preferredPath: String? = selectedPath) {
        val showInitialLoading = files.isEmpty() && path.isBlank()
        if (showInitialLoading) {
            initialLoading = true
        } else {
            loadingDirPath = path
            statusMessage = if (path.isBlank()) "正在刷新文件目录" else "正在加载 $path"
        }
        errorMessage = null
        val params = if (path.isBlank()) emptyMap() else mapOf("path" to path)
        scope.rdiRequest<List<Host.FileEntry>>(
            path = "$apiRoot/$hostId/files",
            params = params,
            onOk = { response ->
                val loadedFiles = response.data ?: emptyList()
                mergeHostFileEntries(path, loadedFiles)
                statusMessage = if (path.isBlank()) "已加载文件目录" else "已加载 $path"

                when {
                    preferredPath != null && loadedFiles.any { it.path == preferredPath && !it.directory } && selectedPath == null -> {
                        editorOpen = true
                        loadFile(preferredPath)
                    }

                    selectedPath != null && path.isNotBlank() && files.none { it.path == selectedPath } -> {
                        if (editorText == originalText) {
                            loadedFiles.firstOrNull { !it.directory }?.let {
                                editorOpen = true
                                loadFile(it.path)
                            }
                        } else {
                            statusMessage = "当前文件已不在文件列表中，请先保存或还原内容"
                        }
                    }
                }
            },
            onErr = { errorMessage = it.message ?: "加载文件列表失败" },
            onDone = {
                if (showInitialLoading) {
                    initialLoading = false
                }
                if (loadingDirPath == path) {
                    loadingDirPath = null
                }
            }
        )
    }

    fun saveFile() {
        val path = selectedPath ?: return
        saving = true
        errorMessage = null
        scope.rdiRequest<Host.FileUploadVo>(
            path = "$apiRoot/$hostId/files/file",
            method = HttpMethod.Put,
            body = serdesJson.encodeToString(
                Host.FileWriteDto(
                    path = path,
                    content = editorText
                )
            ),
            onOk = { response ->
                val saved = response.data ?: run {
                    errorMessage = "保存文件失败"
                    return@rdiRequest
                }
                selectedPath = saved.path
                originalText = editorText
                syntaxErrorMessage = null
                statusMessage = "已保存 ${saved.path}"
                files = files.map { entry ->
                    if (entry.path == saved.path) entry.copy(size = saved.size, updateTime = saved.updateTime) else entry
                }
                if (files.none { it.path == saved.path }) {
                    files = (files + Host.FileEntry(
                        path = saved.path,
                        name = saved.path.substringAfterLast('/'),
                        directory = false,
                        size = saved.size,
                        updateTime = saved.updateTime
                    )).sortedBy { it.path.lowercase() }
                }
            },
            onErr = { errorMessage = it.message ?: "保存文件失败" },
            onDone = { saving = false }
        )
    }

    fun searchFiles(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            activeSearchQuery = ""
            searchResults = emptyList()
            return
        }
        activeSearchQuery = normalizedQuery
        searchResults = emptyList()
        errorMessage = null
        scope.rdiRequest<List<Host.FileEntry>>(
            path = "$apiRoot/$hostId/files/search",
            params = mapOf("query" to normalizedQuery),
            onOk = { response ->
                if (activeSearchQuery == normalizedQuery) {
                    searchResults = response.data ?: emptyList()
                }
            },
            onErr = {
                if (activeSearchQuery == normalizedQuery) {
                    errorMessage = it.message ?: "搜索文件失败"
                }
            }
        )
    }

    fun createFile(path: String, directory: Boolean) {
        if (path.isBlank()) {
            errorMessage = "请输入文件路径"
            return
        }
        saving = true
        errorMessage = null
        scope.rdiRequest<Host.FileUploadVo>(
            path = "$apiRoot/$hostId/files/create",
            method = HttpMethod.Post,
            body = serdesJson.encodeToString(Host.FileCreateDto(path = path, directory = directory)),
            onOk = { response ->
                val created = response.data ?: return@rdiRequest
                loadFiles(path = created.path.substringBeforeLast('/', ""))
                statusMessage = "已创建 ${created.path}"
            },
            onErr = { errorMessage = it.message ?: "创建文件失败" },
            onDone = { saving = false }
        )
    }

    fun renameFile(from: String, to: String) {
        if (from.isBlank() || to.isBlank()) {
            errorMessage = "请输入新路径"
            return
        }
        saving = true
        errorMessage = null
        scope.rdiRequest<Host.FileUploadVo>(
            path = "$apiRoot/$hostId/files/rename",
            method = HttpMethod.Put,
            body = serdesJson.encodeToString(Host.FileRenameDto(from = from, to = to)),
            onOk = { response ->
                val renamed = response.data ?: return@rdiRequest
                files = files.filterNot { it.path == from || it.path.startsWith("$from/") }
                loadFiles(path = from.substringBeforeLast('/', ""))
                loadFiles(path = renamed.path.substringBeforeLast('/', ""))
                selectedPath = selectedPath?.let { path ->
                    when {
                        path == from -> renamed.path
                        path.startsWith("$from/") -> renamed.path + path.removePrefix(from)
                        else -> path
                    }
                }
                statusMessage = "已重命名为 ${renamed.path}"
            },
            onErr = { errorMessage = it.message ?: "重命名文件失败" },
            onDone = { saving = false }
        )
    }

    fun deleteFile(path: String) {
        if (path.isBlank()) return
        saving = true
        errorMessage = null
        scope.rdiRequestU(
            path = "$apiRoot/$hostId/files/file",
            method = HttpMethod.Delete,
            body = serdesJson.encodeToString(Host.FileDeleteDto(path)),
            onOk = {
                files = files.filterNot { it.path == path || it.path.startsWith("$path/") }
                if (selectedPath == path || selectedPath?.startsWith("$path/") == true) {
                    selectedPath = null
                    editorOpen = false
                    editorText = ""
                    originalText = ""
                    syntaxErrorMessage = null
                }
                loadFiles(path = path.substringBeforeLast('/', ""))
                statusMessage = "已删除 $path"
            },
            onErr = { errorMessage = it.message ?: "删除文件失败" },
            onDone = { saving = false }
        )
    }

    LaunchedEffect(hostId) {
        files = emptyList()
        selectedPath = null
        initialLoading = false
        loadingDirPath = null
        editorOpen = false
        editorText = ""
        originalText = ""
        syntaxErrorMessage = null
        searchResults = emptyList()
        activeSearchQuery = ""
        statusMessage = null
        errorMessage = null
        loadFiles()
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!initialLoading && files.isEmpty() && errorMessage != null) {
            HostFileExplorerError(
                message = errorMessage ?: "加载文件失败",
                onRetry = { loadFiles() },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            HostFileExplorer(
                files = files,
                searchResults = searchResults,
                selectedPath = selectedPath,
                loadingFiles = initialLoading,
                loadingDirPath = loadingDirPath,
                statusMessage = statusMessage,
                onSelectFile = { path ->
                    if (path.startsWith("tacz/")) {
                        errorMessage = "TaCZ zip文件不能用文本编辑器打开"
                    } else if (dirty && path != selectedPath) {
                        errorMessage = "当前文件有未保存修改，请先保存或还原"
                    } else {
                        editorOpen = true
                        loadFile(path)
                    }
                },
                onLoadDir = { path -> loadFiles(path = path) },
                onCreateFile = ::createFile,
                onRenameFile = ::renameFile,
                onDeleteFile = ::deleteFile,
                onReloadList = { loadFiles(path = "") },
                onSearchFiles = ::searchFiles
            )
        }

        HostConfigEditorOverlay(
            visible = editorOpen,
            selectedPath = selectedPath,
            editorText = editorText,
            loadingContent = loadingContent,
            saving = saving,
            dirty = dirty,
            validationErrorMessage = syntaxErrorMessage,
            onClose = { editorOpen = false },
            onReload = {
                val path = selectedPath
                if (path == null) {
                    errorMessage = "请先选择配置文件"
                } else {
                    loadFile(path)
                }
            },
            onSave = {
                if (selectedPath == null) {
                    errorMessage = "请先选择配置文件"
                } else if (syntaxErrorMessage != null) {
                    errorMessage = syntaxErrorMessage
                } else {
                    saveFile()
                }
            },
            onEditorChange = {
                editorText = it
                syntaxErrorMessage = validateCodeContent(
                    text = it,
                    language = CodeLanguage.fromPath(selectedPath)
                )?.takeIf { validation -> !validation.isValid }?.message
            },
            onValidationChange = { validation ->
                syntaxErrorMessage = validation?.takeIf { !it.isValid }?.message
            }
        )

        errorMessage?.let { message ->
            AlertErr(message) { errorMessage = null }
        }
    }
}

@Composable
private fun HostFileExplorerError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Composable
fun HostFileExplorer(
    files: List<Host.FileEntry>,
    searchResults: List<Host.FileEntry>,
    selectedPath: String?,
    loadingFiles: Boolean,
    loadingDirPath: String? = null,
    statusMessage: String?,
    onSelectFile: (String) -> Unit,
    onLoadDir: (String) -> Unit,
    onCreateFile: (String, Boolean) -> Unit,
    onRenameFile: (String, String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onReloadList: () -> Unit,
    onSearchFiles: (String) -> Unit
) {
    var sortColumn by remember { mutableStateOf(HostConfigSortColumn.PATH) }
    var sortAscending by remember { mutableStateOf(true) }
    var currentDirPath by remember { mutableStateOf(selectedPath?.substringBeforeLast('/', "") ?: "") }
    val searchState = rememberTextFieldState()
    val createNameState = rememberTextFieldState()
    val renameNameState = rememberTextFieldState()
    val dirTreeListState = rememberLazyListState()
    val contentListState = rememberLazyListState()
    val expandedDirs = remember { mutableStateMapOf<String, Boolean>() }
    var createParentPath by remember { mutableStateOf("") }
    var createDirectory by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createErrorMessage by remember { mutableStateOf<String?>(null) }
    var renameFromPath by remember { mutableStateOf<String?>(null) }
    var renameErrorMessage by remember { mutableStateOf<String?>(null) }
    var deletePath by remember { mutableStateOf<String?>(null) }
    var dirContextMenuPath by remember { mutableStateOf<String?>(null) }
    var submittedSearchKeyword by remember { mutableStateOf("") }
    LaunchedEffect(selectedPath) {
        selectedPath?.substringBeforeLast('/', "")?.let { currentDirPath = it }
    }

    val treeEntries = if (submittedSearchKeyword.isBlank()) files else searchResults
    val treeNodes = remember(treeEntries) {
        buildHostConfigDirTree(treeEntries)
    }
    val expandedSnapshot = expandedDirs.toMap()
    val visibleDirNodes = remember(treeNodes, currentDirPath, selectedPath, expandedSnapshot, submittedSearchKeyword) {
        flattenVisibleConfigDirNodes(
            nodes = treeNodes,
            expandedDirs = expandedSnapshot,
            currentDirPath = currentDirPath,
            selectedPath = selectedPath,
            searchKeyword = submittedSearchKeyword
        )
    }
    val currentDirEntries = remember(files, currentDirPath, sortColumn, sortAscending) {
        val directEntries = files.filter { it.path.directParentPath() == currentDirPath }
        directEntries.sortedForHostFileExplorer(sortColumn, sortAscending)
    }
    val loadingCurrentDir = loadingDirPath == currentDirPath

    fun toggleSort(column: HostConfigSortColumn) {
        if (sortColumn == column) {
            sortAscending = !sortAscending
        } else {
            sortColumn = column
            sortAscending = true
        }
    }

    fun openCreateDialog(parentPath: String, directory: Boolean) {
        createParentPath = parentPath
        createDirectory = directory
        createNameState.setTextAndPlaceCursorAtEnd("")
        showCreateDialog = true
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        /*TextButton(enabled = !loadingFiles, onClick = onReloadList) {
            Text("刷新列表")
        }
*/
        Surface(
            modifier = Modifier
                .fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when {
                    loadingFiles -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    files.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("没有可操作文件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    else -> {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            /*RRow {
                                CircleIconButton(
                                    icon = "\uF021",
                                    tooltip = "刷新",
                                    enabled = !loadingFiles
                                ) { onReloadList() }
                                CircleIconButton(
                                    icon = "\uF15B",
                                    tooltip = "新建",
                                    enabled = !loadingFiles
                                ) { openCreateDialog(parentPath = currentDirPath, directory = false) }
                                CircleIconButton(
                                    icon = "\uF07B",
                                    tooltip = "新建目录",
                                    showText = false,
                                    enabled = !loadingFiles
                                ) { openCreateDialog(parentPath = currentDirPath, directory = true) }
                                statusMessage?.let {
                                    Text(
                                        text = it,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }*/
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .widthIn(min = 220.dp, max = 320.dp)
                                        .fillMaxHeight()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        HostConfigSearchHeaderBar(
                                            state = searchState,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(hostFileExplorerHeaderHeight),
                                            onSearch = { query ->
                                                submittedSearchKeyword = query.trim()
                                                onSearchFiles(query)
                                            }
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        Box(modifier = Modifier.weight(1f)) {
                                            LazyColumn(
                                                state = dirTreeListState,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(end = 10.dp)
                                            ) {
                                                item(key = "dir:") {
                                                    HostConfigDirTreeRow(
                                                        name = "文件",
                                                        directory = true,
                                                        depth = 0,
                                                        expanded = true,
                                                        selected = currentDirPath.isBlank(),
                                                        hasChildren = treeNodes.isNotEmpty(),
                                                        contextMenuExpanded = dirContextMenuPath == "",
                                                        onToggleDir = {
                                                            currentDirPath = ""
                                                            expandedDirs[""] = true
                                                            onLoadDir("")
                                                        },
                                                        onSelectDir = {
                                                            currentDirPath = ""
                                                            expandedDirs[""] = true
                                                            onLoadDir("")
                                                        },
                                                        onSelectFile = {},
                                                        onOpenContextMenu = { dirContextMenuPath = "" },
                                                        onDismissContextMenu = { dirContextMenuPath = null },
                                                        onCreateFile = {
                                                            openCreateDialog(parentPath = "", directory = false)
                                                        },
                                                        onCreateDirectory = {
                                                            openCreateDialog(parentPath = "", directory = true)
                                                        }
                                                    )
                                                }
                                                items(visibleDirNodes, key = { it.key }) { visibleNode ->
                                                    HostConfigDirTreeRow(
                                                        name = visibleNode.node.name,
                                                        directory = visibleNode.node.directory,
                                                        depth = visibleNode.depth + 1,
                                                        expanded = visibleNode.expanded,
                                                        selected = visibleNode.selected,
                                                        hasChildren = visibleNode.node.directory && visibleNode.node.children.isNotEmpty(),
                                                        contextMenuExpanded = dirContextMenuPath == visibleNode.node.path,
                                                        onToggleDir = {
                                                            if (visibleNode.node.directory) {
                                                                expandedDirs[visibleNode.node.path] = !visibleNode.expanded
                                                                onLoadDir(visibleNode.node.path)
                                                            }
                                                        },
                                                        onSelectDir = {
                                                            if (visibleNode.node.directory) {
                                                                currentDirPath = visibleNode.node.path
                                                                expandedDirs[visibleNode.node.path] = true
                                                                onLoadDir(visibleNode.node.path)
                                                            }
                                                        },
                                                        onSelectFile = { onSelectFile(visibleNode.node.path) },
                                                        onOpenContextMenu = {
                                                            if (visibleNode.node.directory) dirContextMenuPath = visibleNode.node.path
                                                        },
                                                        onDismissContextMenu = { dirContextMenuPath = null },
                                                        onCreateFile = {
                                                            openCreateDialog(parentPath = visibleNode.node.path, directory = false)
                                                        },
                                                        onCreateDirectory = {
                                                            openCreateDialog(parentPath = visibleNode.node.path, directory = true)
                                                        },
                                                        onRename = {
                                                            renameFromPath = visibleNode.node.path
                                                            renameNameState.setTextAndPlaceCursorAtEnd(visibleNode.node.path.substringAfterLast('/'))
                                                        },
                                                        onDelete = {
                                                            deletePath = visibleNode.node.path
                                                        }
                                                    )
                                                }
                                            }
                                            if (visibleDirNodes.isNotEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterEnd)
                                                        .fillMaxHeight()
                                                        .width(10.dp)
                                                ) {
                                                    RVerticalScrollbar(
                                                        listState = dirTreeListState,
                                                        modifier = Modifier.fillMaxHeight()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                ) {
                                    HostConfigTableHeader(
                                        sortColumn = sortColumn,
                                        sortAscending = sortAscending,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(hostFileExplorerHeaderHeight),
                                        onSortChange = ::toggleSort
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (currentDirEntries.isEmpty()) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = when {
                                                        loadingCurrentDir -> "正在加载目录..."
                                                        else -> "当前目录为空"
                                                    },
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        } else {
                                            LazyColumn(
                                                state = contentListState,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(end = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(0.dp)
                                            ) {
                                                items(currentDirEntries, key = { "${if (it.directory) "dir" else "file"}:${it.path}" }) { entry ->
                                                    HostConfigContentRow(
                                                        entry = entry,
                                                        selected = entry.path == selectedPath,
                                                        showOperations = !(currentDirPath.isBlank() && entry.directory),
                                                        onOpenDir = { path ->
                                                            currentDirPath = path
                                                            expandedDirs[path] = true
                                                            onLoadDir(path)
                                                        },
                                                        onSelectFile = onSelectFile,
                                                        onRenameFile = { path ->
                                                            renameFromPath = path
                                                            renameNameState.setTextAndPlaceCursorAtEnd(path.substringAfterLast('/'))
                                                        },
                                                        onDeleteFile = { path ->
                                                            deletePath = path
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        if (currentDirEntries.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .fillMaxHeight()
                                                    .width(12.dp)
                                            ) {
                                                RVerticalScrollbar(
                                                    listState = contentListState,
                                                    modifier = Modifier.fillMaxHeight()
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
        }
    }

    if (showCreateDialog) {
        val parentPrefix = createParentPath.pathWithChildPrefix()
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (createDirectory) "新建目录" else "新建文件") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (parentPrefix.isNotBlank()) {
                        Text(
                            text = parentPrefix,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    RThinTextField(
                        state = createNameState,
                        label = "名称",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = createNameState.text.toString().trim()
                    val invalidMessage = validateHostFileName(newName)
                    if (invalidMessage != null) {
                        createErrorMessage = invalidMessage
                    } else {
                        val newPath = if (createParentPath.isBlank()) newName else "$createParentPath/$newName"
                        onCreateFile(newPath, createDirectory)
                        showCreateDialog = false
                    }
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
            }
        )
    }

    createErrorMessage?.let { message ->
        AlertErr(message) { createErrorMessage = null }
    }

    renameFromPath?.let { fromPath ->
        val parentPath = fromPath.directParentPath()
        val parentPrefix = parentPath.pathWithChildPrefix()
        AlertDialog(
            onDismissRequest = { renameFromPath = null },
            title = { Text("重命名") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (parentPrefix.isNotBlank()) {
                        Text(
                            text = parentPrefix,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    RThinTextField(
                        state = renameNameState,
                        label = "名称",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = renameNameState.text.toString().trim()
                    val invalidMessage = validateHostFileName(newName)
                    if (invalidMessage != null) {
                        renameErrorMessage = invalidMessage
                    } else {
                        val newPath = if (parentPath.isBlank()) newName else "$parentPath/$newName"
                        onRenameFile(fromPath, newPath)
                        renameFromPath = null
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameFromPath = null }) { Text("取消") }
            }
        )
    }

    renameErrorMessage?.let { message ->
        AlertErr(message) { renameErrorMessage = null }
    }

    deletePath?.let { path ->
        AlertDialog(
            onDismissRequest = { deletePath = null },
            title = { Text("删除文件") },
            text = { Text("确定删除 $path？") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFile(path)
                    deletePath = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deletePath = null }) { Text("取消") }
            }
        )
    }
}


@Composable
fun HostConfigEditorOverlay(
    visible: Boolean,
    selectedPath: String?,
    editorText: String,
    loadingContent: Boolean,
    saving: Boolean,
    dirty: Boolean,
    validationErrorMessage: String?,
    onClose: () -> Unit,
    onReload: () -> Unit,
    onSave: () -> Unit,
    onEditorChange: (String) -> Unit,
    onValidationChange: (CodeEditorValidation?) -> Unit
) {
    if (!visible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {},
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.9f),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TitleRow(
                    selectedPath ?: "", { onClose() },
                ) {
                    Text(
                        text = when {
                            saving -> "保存中..."
                            loadingContent -> "读取中..."
                            validationErrorMessage != null -> validationErrorMessage
                            dirty -> "有未保存修改"
                            else -> ""
                        },
                        color = when {
                            validationErrorMessage != null -> MaterialTheme.colorScheme.error
                            dirty -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodySmall
                    )

                    CircleIconButton(
                        icon = "\uDB81\uDC50",
                        tooltip = "还原",
                        enabled = selectedPath != null && !loadingContent && !saving,
                        showText = false
                    ) {
                        onReload()
                    }
                    CircleIconButton(
                        icon = "\uF0C7",
                        tooltip = "保存",
                        enabled = selectedPath != null && dirty && !loadingContent && !saving && validationErrorMessage == null,
                        showText = false,
                        bgColor = MaterialTheme.colorScheme.tertiary
                    ) {
                        onSave()
                    }

                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (loadingContent) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "正在读取配置文件...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            CodeEditor(
                                text = editorText,
                                enabled = selectedPath != null && !saving,
                                language = CodeLanguage.fromPath(selectedPath),
                                onValueChange = onEditorChange,
                                onValidationChange = onValidationChange,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostConfigTableHeader(
    sortColumn: HostConfigSortColumn,
    sortAscending: Boolean,
    modifier: Modifier = Modifier,
    onSortChange: (HostConfigSortColumn) -> Unit
) {
    Row(
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HostConfigHeaderCell(
            title = "名称",
            column = HostConfigSortColumn.PATH,
            activeColumn = sortColumn,
            ascending = sortAscending,
            modifier = Modifier.weight(0.4f),
            onSortChange = onSortChange
        )
        HostConfigHeaderCell(
            title = "修改时间",
            column = HostConfigSortColumn.TIME,
            activeColumn = sortColumn,
            ascending = sortAscending,
            modifier = Modifier.weight(0.2f),
            onSortChange = onSortChange
        )
        HostConfigHeaderCell(
            title = "大小",
            column = HostConfigSortColumn.SIZE,
            activeColumn = sortColumn,
            ascending = sortAscending,
            modifier = Modifier.weight(0.1f),
            onSortChange = onSortChange
        )
        Spacer(Modifier.weight(0.06f))
    }
}

@Composable
private fun HostConfigHeaderCell(
    title: String,
    column: HostConfigSortColumn,
    activeColumn: HostConfigSortColumn,
    ascending: Boolean,
    modifier: Modifier = Modifier,
    onSortChange: (HostConfigSortColumn) -> Unit
) {
    val suffix = if (column == activeColumn) {
        if (ascending) " ↑" else " ↓"
    } else {
        ""
    }
    Text(
        text = title + suffix,
        modifier = modifier.clickable { onSortChange(column) },
        color = if (column == activeColumn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun HostConfigSearchHeaderBar(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit
) {
    RThinTextField(
        state = state,
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                onSearch(state.text.toString())
                true
            } else {
                false
            }
        },
        label = "",
        shape = RoundedCornerShape(0f),
        leadingIcon = {
            Text(
                text = "\uF002".asIconText,
                fontSize = 14.sp
            )
        },
        trailingIcon = {
            if (state.text.isNotBlank()) {
                Text(
                    text = "\uF00D".asIconText,
                    modifier = Modifier.clickable {
                        state.setTextAndPlaceCursorAtEnd("")
                        onSearch("")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }
    )
}

@Composable
private fun HostConfigDirTreeRow(
    name: String,
    directory: Boolean,
    depth: Int,
    expanded: Boolean,
    selected: Boolean,
    hasChildren: Boolean,
    onToggleDir: () -> Unit,
    onSelectDir: () -> Unit,
    onSelectFile: () -> Unit,
    contextMenuExpanded: Boolean,
    onOpenContextMenu: () -> Unit,
    onDismissContextMenu: () -> Unit,
    onCreateFile: () -> Unit,
    onCreateDirectory: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val textColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                )
                .pointerInput(name) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (directory && event.buttons.isSecondaryPressed) {
                                onOpenContextMenu()
                            }
                        }
                    }
                }
                .clickable {
                    if (directory) onSelectDir() else onSelectFile()
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width((depth * 16).dp))
            Text(
                text = if (hasChildren) {
                    if (expanded) "\uF078" else "\uF054"
                } else {
                    ""
                },
                modifier = Modifier
                    .width(18.dp)
                    .clickable(enabled = hasChildren) { onToggleDir() },
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1
            )
            Text(
                text = (if (!directory) "\uF15B" else if (expanded) "\uF07C" else "\uF07B").asIconText,
                modifier = Modifier.width(24.dp),
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                maxLines = 1
            )
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (directory) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        if (directory) {
            HostFileDirContextMenu(
                expanded = contextMenuExpanded,
                onDismissRequest = onDismissContextMenu,
                onCreateFile = onCreateFile,
                onCreateDirectory = onCreateDirectory,
                onRename = onRename,
                onDelete = onDelete
            )
        }
    }
}

@Composable
private fun HostFileDirContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onCreateFile: () -> Unit,
    onCreateDirectory: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = { Text("新建文件") },
            onClick = {
                onDismissRequest()
                onCreateFile()
            }
        )
        DropdownMenuItem(
            text = { Text("新建目录") },
            onClick = {
                onDismissRequest()
                onCreateDirectory()
            }
        )
        if (onRename != null) {
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = {
                    onDismissRequest()
                    onRename()
                }
            )
        }
        if (onDelete != null) {
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = {
                    onDismissRequest()
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun HostConfigContentRow(
    entry: Host.FileEntry,
    selected: Boolean,
    showOperations: Boolean,
    onOpenDir: (String) -> Unit,
    onSelectFile: (String) -> Unit,
    onRenameFile: (String) -> Unit,
    onDeleteFile: (String) -> Unit
) {
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,

            )
            .clickable {
                if (entry.directory) onOpenDir(entry.path) else onSelectFile(entry.path)
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(0.4f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (if (entry.directory) "\uF07B" else "\uF15B").asIconText,
                modifier = Modifier.width(28.dp),
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                maxLines = 1
            )
            Text(
                text = entry.name.ifBlank { entry.path.substringAfterLast('/') },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (entry.directory) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        Text(
            text = entry.updateTime.takeIf { it > 0 }?.toFriendlyDateTime().orEmpty(),
            modifier = Modifier.weight(0.2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = if (entry.directory) "" else entry.size.humanFileSize,
            modifier = Modifier.weight(0.1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        if (showOperations) {
            RRow(
                modifier = Modifier.weight(0.06f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CircleIconButton(
                    icon = "\uF044",
                    tooltip = "重命名",
                    showText = false,
                    size = 28
                ) { onRenameFile(entry.path) }
                CircleIconButton(
                    icon = "\uF1F8",
                    tooltip = "删除",
                    showText = false,
                    size = 28,
                    bgColor = MaterialTheme.colorScheme.error
                ) { onDeleteFile(entry.path) }
            }
        } else {
            Spacer(Modifier.weight(0.06f))
        }
    }
}

private data class HostConfigDirNode(
    val name: String,
    val path: String,
    val directory: Boolean,
    val children: List<HostConfigDirNode>
)

private data class HostConfigVisibleDirNode(
    val node: HostConfigDirNode,
    val depth: Int,
    val expanded: Boolean,
    val selected: Boolean
) {
    val key: String = "${if (node.directory) "dir" else "file"}:${node.path}"
}

private class MutableHostConfigDir(
    val name: String,
    val path: String
) {
    val dirs = linkedMapOf<String, MutableHostConfigDir>()
    val files = linkedMapOf<String, HostConfigDirNode>()
}

private fun buildHostConfigDirTree(files: List<Host.FileEntry>): List<HostConfigDirNode> {
    val root = MutableHostConfigDir("", "")
    files.forEach { file ->
        val parts = file.path.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return@forEach
        var current = root
        val dirParts = if (file.directory) parts else parts.dropLast(1)
        dirParts.forEach { part ->
            val dirPath = if (current.path.isBlank()) part else "${current.path}/$part"
            current = current.dirs.getOrPut(part) { MutableHostConfigDir(part, dirPath) }
        }
        if (!file.directory) {
            current.files[file.path] = HostConfigDirNode(
                name = file.name.ifBlank { file.path.substringAfterLast('/') },
                path = file.path,
                directory = false,
                children = emptyList()
            )
        }
    }
    return root.toImmutableDirChildren()
}

private fun MutableHostConfigDir.toImmutableDirChildren(): List<HostConfigDirNode> =
    dirs.values
        .sortedBy { it.name.lowercase() }
        .map { dir ->
            HostConfigDirNode(
                name = dir.name,
                path = dir.path,
                directory = true,
                children = dir.toImmutableDirChildren()
            )
        } + files.values.sortedBy { it.name.lowercase() }

private fun flattenVisibleConfigDirNodes(
    nodes: List<HostConfigDirNode>,
    expandedDirs: Map<String, Boolean>,
    currentDirPath: String,
    selectedPath: String?,
    searchKeyword: String
): List<HostConfigVisibleDirNode> {
    val expandedBySelection = currentDirPath.parentConfigPaths(includeSelf = false) +
            selectedPath?.parentConfigPaths(includeSelf = false).orEmpty()
    val normalizedSearchKeyword = searchKeyword.trim()
    val visibleNodes = mutableListOf<HostConfigVisibleDirNode>()

    fun HostConfigDirNode.matchesSearch(): Boolean =
        normalizedSearchKeyword.isBlank() ||
                name.contains(normalizedSearchKeyword, ignoreCase = true) ||
                path.contains(normalizedSearchKeyword, ignoreCase = true)

    fun HostConfigDirNode.hasSearchMatch(): Boolean =
        matchesSearch() || children.any { it.hasSearchMatch() }

    fun visit(node: HostConfigDirNode, depth: Int) {
        if (normalizedSearchKeyword.isNotBlank() && !node.hasSearchMatch()) return
        val expanded = node.directory && if (normalizedSearchKeyword.isNotBlank()) {
            node.children.any { it.hasSearchMatch() }
        } else {
            node.path in expandedBySelection || (expandedDirs[node.path] ?: (depth == 0))
        }
        visibleNodes += HostConfigVisibleDirNode(
            node = node,
            depth = depth,
            expanded = expanded,
            selected = if (node.directory) node.path == currentDirPath else node.path == selectedPath
        )
        if (expanded) {
            node.children.forEach { child -> visit(child, depth + 1) }
        }
    }

    nodes.forEach { visit(it, 0) }
    return visibleNodes
}

private fun String.parentConfigPaths(includeSelf: Boolean): Set<String> {
    val parts = split('/').filter { it.isNotBlank() }.let { if (includeSelf) it else it.dropLast(1) }
    if (parts.isEmpty()) return emptySet()
    val paths = mutableSetOf<String>()
    parts.indices.forEach { index ->
        paths += parts.take(index + 1).joinToString("/")
    }
    return paths
}

private fun String.directParentPath(): String =
    substringBeforeLast('/', "")

private fun String.pathWithChildPrefix(): String =
    if (isBlank()) "" else "$this/"

private fun validateHostFileName(name: String): String? =
    when {
        name.isBlank() -> "名称不能为空"
        '/' in name || '\\' in name -> "名称不能包含路径分隔符"
        name == "." || name == ".." -> "名称不能为${name}"
        else -> null
    }

private fun List<Host.FileEntry>.sortedForHostFileExplorer(
    sortColumn: HostConfigSortColumn,
    sortAscending: Boolean
): List<Host.FileEntry> =
    sortedWith { left, right ->
        val dirResult = right.directory.compareTo(left.directory)
        if (dirResult != 0) return@sortedWith dirResult
        val result = when (sortColumn) {
            HostConfigSortColumn.PATH -> left.name.lowercase().compareTo(right.name.lowercase())
            HostConfigSortColumn.TIME -> left.updateTime.compareTo(right.updateTime)
            HostConfigSortColumn.SIZE -> left.size.compareTo(right.size)
        }
        if (sortAscending) result else -result
    }

private enum class HostConfigSortColumn {
    PATH,
    TIME,
    SIZE
}
