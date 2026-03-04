package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.mykotutils.std.millisToHumanDateTime
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.rdiRequest
import calebxzhou.rdi.client.net.rdiRequestU
import calebxzhou.rdi.client.service.ModpackService
import calebxzhou.rdi.client.service.ModpackService.startInstall
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.HeadButton
import calebxzhou.rdi.client.ui.comp.ModCard
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.Task
import calebxzhou.rdi.common.model.isDav
import calebxzhou.rdi.common.service.CurseForgeService.fillCurseForgeVo
import calebxzhou.rdi.common.service.ModrinthService.fillModrinthVo
import calebxzhou.rdi.common.service.latest
import calebxzhou.rdi.common.service.validate
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bson.types.ObjectId

/**
 * calebxzhou @ 2026-01-17 20:44
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModpackInfoScreen(
    modpackId: String,
    onBack: () -> Unit,
    onOpenUpload: ((ObjectId, String) -> Unit)? = null,
    onCreateHost: ((String, String, String, Boolean) -> Unit)? = null,
    onOpenTask: ((Task) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var okMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("整合包详情") }
    var loading by remember { mutableStateOf(true) }
    var modpack by remember { mutableStateOf<Modpack.DetailVo?>(null) }
    var mods by remember { mutableStateOf<List<Mod>>(emptyList()) }
    var modsLoading by remember { mutableStateOf(false) }
    var confirmDeletePack by remember { mutableStateOf(false) }
    var confirmDeleteVersion by remember { mutableStateOf<Modpack.Version?>(null) }
    var confirmRebuildVersion by remember { mutableStateOf<Modpack.Version?>(null) }
    var confirmRedownloadVersion by remember { mutableStateOf<Modpack.Version?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editIconUrl by remember { mutableStateOf("") }
    var editInfo by remember { mutableStateOf("") }
    var editSourceUrl by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var modSearch by remember { mutableStateOf("") }

    fun reload() {
        loading = true
        errorMessage = null
        scope.rdiRequest<Modpack.DetailVo>(
            path = "modpack/$modpackId/detail",
            onOk = { response ->
                modpack = response.data
                val versions = response.data?.versions.orEmpty()
                if (versions.isNotEmpty()) {
                    val latest = versions.latest
                    modsLoading = true
                    mods = emptyList()
                    scope.launch {
                        val loaded = withContext(Dispatchers.IO) {
                            runCatching {
                                latest.mods.fillCurseForgeVo()
                                latest.mods.fillModrinthVo(null)
                            }.getOrElse {
                                it.printStackTrace();
                                emptyList()
                            }
                        }
                        mods = loaded
                        modsLoading = false
                    }
                } else {
                    modsLoading = false
                    mods = emptyList()
                }
            },
            onErr = { errorMessage = "加载整合包信息失败: ${it.message}" },
            onDone = { loading = false }
        )
    }

    fun startDownload(pack: Modpack.DetailVo, version: Modpack.Version) {
        val task = version.startInstall(pack.mcVer, pack.modloader, pack.name)
        if (onOpenTask != null) {
            onOpenTask(task)
        } else {
            okMessage = "暂不支持在此页面下载"
        }
    }

    LaunchedEffect(modpackId) {
        reload()
    }
    LaunchedEffect(okMessage) {
        okMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            okMessage = null
        }
    }
    val pack = modpack
    val isAuthor = pack?.let {  it.authorId == loggedAccount._id || loggedAccount.isDav } ?: false
    LaunchedEffect(showEditDialog, pack) {
        if (!showEditDialog) return@LaunchedEffect
        pack?.let {
            editName = it.name
            editIconUrl = it.icon ?: ""
            editInfo = it.info ?: ""
            editSourceUrl = it.sourceUrl ?: ""
        }
    }

    MainBox {
        MainColumn {
            TitleRow(title, onBack) {
                errorMessage?.let { Text(it, color = MaterialTheme.colors.error) }

                pack?.let { pack->
                    HeadButton(pack.authorId)
                    Space8w()
                    ImageIconButton("grass_block")
                    Text(pack.mcVer.mcVer)
                    ImageIconButton(pack.modloader.name)
                }
                if (isAuthor) {
                    CircleIconButton(
                        icon = "\uF01F",
                        tooltip = "修改信息",
                        bgColor = MaterialColor.YELLOW_900.color
                    ) {
                        showEditDialog = true
                    }
                    // Upload button — desktop only
                    if (isDesktop) {
                        Space8w()
                        CircleIconButton(
                            icon = "\uF093",
                            tooltip = "上传新版本",
                            bgColor = MaterialColor.BLUE_900.color
                        ) {
                            if (onOpenUpload == null) {
                                okMessage = "暂不支持在此页面上传新版本"
                                return@CircleIconButton
                            }
                            ModpackUploadStore.preset = ModpackUploadStore.Preset(
                                updateModpackId = pack?._id,
                                updateModpackName = pack?.name
                            )
                            onOpenUpload(pack!!._id, pack!!.name)
                        }
                        Space8w()
                        CircleIconButton(
                            icon = "\uEA81",
                            tooltip = "删除整合包",
                            bgColor = MaterialColor.RED_900.color,
                            showText = false
                        ) { confirmDeletePack = true }
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

            if (!loading && pack == null) {
                Text("未找到整合包信息")
            }

            if (pack != null) {
                title="整合包 · "+pack.name
                val tabTitles = listOf(
                    "Mod列表(${pack.modCount})",
                    "简介",
                    "\uF019 下载版本(${pack.versions.size})"
                )
                TabRow(selectedTabIndex = selectedTab, backgroundColor = Color.White) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
                Space8h()
                when (selectedTab) {
                    0 -> {
                        if (pack.versions.isEmpty()) {
                            Text("此整合包暂无可用版本，等待作者上传....", color = Color.Gray)
                        } else {
                            if (modsLoading) {
                                Text("正在载入${pack.modCount}个Mod的详细信息...")
                            }
                            Space8h()
                            OutlinedTextField(
                                value = modSearch,
                                onValueChange = { modSearch = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                label = { Text("搜索 Mod 名称") },
                                singleLine = true,
                                maxLines = 1,
                                placeholder = { Text("输入 Mod 名称或英文名...") },
                                trailingIcon = {
                                    if (modSearch.isNotBlank()) {
                                        IconButton(onClick = { modSearch = "" }) {
                                            Text("✕")
                                        }
                                    }
                                }
                            )
                            val sortedMods = remember(mods) {
                                mods.sortedWith(
                                    compareBy(
                                        String.CASE_INSENSITIVE_ORDER
                                    ) { mod ->mod.vo?.name?.takeIf { it.isNotBlank() }
                                            ?: mod.slug
                                    }
                                )

                            }
                            val filteredMods = remember(sortedMods, modSearch) {
                                val query = modSearch.trim()
                                if (query.isBlank()) sortedMods
                                else sortedMods.filter { mod ->
                                    val name = mod.vo?.name ?: ""
                                    val nameCn = mod.vo?.nameCn ?: ""
                                    name.contains(query, true) || nameCn.contains(query, true) || mod.slug.contains(query, true)
                                }
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(320.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(filteredMods, key = { it.hash }) { mod ->
                                    val card = mod.vo
                                    if (card != null) {
                                        card.ModCard()
                                    } else {
                                        Text(mod.slug, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        Text(pack.info?:"无")
                    }
                    else -> {
                        Space8h()
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(pack.versions, key = { it.name }) { version ->
                                val statusText = when (version.status) {
                                    Modpack.Status.OK -> "\uF058 可用"
                                    Modpack.Status.BUILDING -> "\uEEFF 构建中"
                                    Modpack.Status.FAIL -> "\uEA87 构建失败"
                                    Modpack.Status.WAIT -> "\uE641 等待构建"
                                }
                                val statusColor = when (version.status) {
                                    Modpack.Status.OK -> MaterialColor.GREEN_700.color
                                    Modpack.Status.BUILDING -> MaterialColor.BLUE_700.color
                                    Modpack.Status.FAIL -> MaterialColor.RED_700.color
                                    Modpack.Status.WAIT -> MaterialColor.GRAY_700.color
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "V${version.name} - \uE641 ${version.time.millisToHumanDateTime} - \uF0C7${version.totalSize?.humanFileSize ?: ""}".asIconText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(statusText.asIconText, color = statusColor)
                                    Space8w()
                                    if (isAuthor) {
                                        Space8w()
                                        CircleIconButton(
                                            icon = "\uEA81",
                                            tooltip = "删除版本",
                                            bgColor = MaterialColor.RED_900.color
                                        ) { confirmDeleteVersion = version }
                                        // Rebuild — desktop only
                                        if (isDesktop) {
                                            Space8w()
                                            CircleIconButton(
                                                icon = "\uF0AD",
                                                tooltip = "重构",
                                                bgColor = MaterialColor.BLUE_700.color
                                            ) { confirmRebuildVersion = version }
                                        }
                                    }
                                    if (version.status == Modpack.Status.OK) {
                                        Space8w()
                                        CircleIconButton(
                                            icon = "\uF019",
                                            tooltip = "下载整合包"
                                        ) {
                                            if (ModpackService.getVersionDir(pack._id, version.name).exists()) {
                                                confirmRedownloadVersion = version
                                                return@CircleIconButton
                                            }
                                            startDownload(pack, version)
                                        }
                                    }
                                }
                            }
                            item {
                                if (pack.versions.isEmpty()) {
                                    Text("此整合包暂无可用版本，等待作者上传....", color = Color.Gray)
                                    Space8h()
                                    if (isDesktop) {
                                        Button(onClick = {
                                            if (onOpenUpload != null) {
                                                onOpenUpload(pack._id, pack.name)
                                            } else {
                                                okMessage = "暂不支持上传新版"
                                            }
                                        }) {
                                            Text("上传新版")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        BottomSnakebar(snackbarHostState)
    }

    if (confirmDeletePack && pack != null) {
        AlertDialog(
            onDismissRequest = { confirmDeletePack = false },
            title = { Text("确认删除") },
            text = { Text("确定要永久删除整合包 ${pack.name} 吗？无法恢复！") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeletePack = false
                    scope.rdiRequestU(
                        path = "modpack/${pack._id}",
                        method = HttpMethod.Delete,
                        onOk = {
                            okMessage = "删完了"
                            onBack()
                        },
                        onErr = { errorMessage = "删除失败: ${it.message}" }
                    )
                }) {
                    Text("删除", color = MaterialTheme.colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletePack = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showEditDialog && pack != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("修改整合包信息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editIconUrl,
                        onValueChange = { editIconUrl = it },
                        label = { Text("图标链接") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editSourceUrl,
                        onValueChange = { editSourceUrl = it },
                        label = { Text("来源链接") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editInfo,
                        onValueChange = { editInfo = it },
                        label = { Text("简介") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val options = runCatching {
                            Modpack.OptionsDto(
                                name = editName.trim().ifBlank { null },
                                iconUrl = editIconUrl.trim().ifBlank { null },
                                info = editInfo.trim().ifBlank { null },
                                sourceUrl = editSourceUrl.trim().ifBlank { null }
                            ).validate()
                        }.getOrElse {
                            errorMessage = it.message
                            return@launch
                        }
                        scope.rdiRequestU(
                            path = "modpack/${pack._id}/options",
                            method = HttpMethod.Put,
                            body = options.json,
                            onOk = {
                                okMessage = "已更新"
                                reload()
                            },
                            onErr = { errorMessage = it.message ?: "更新失败" }
                        )
                        showEditDialog = false
                    }
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    confirmRedownloadVersion?.let { version ->
        val currentPack = pack
        if (currentPack != null) {
            ConfirmDialog(
                title = "确认重新下载",
                message = "整合包版本 ${version.name} 已存在，是否重新下载？",
                onConfirm = {
                    confirmRedownloadVersion = null
                    startDownload(currentPack, version)
                },
                onDismiss = { confirmRedownloadVersion = null }
            )
        } else {
            confirmRedownloadVersion = null
        }
    }

    confirmDeleteVersion?.let { version ->
        AlertDialog(
            onDismissRequest = { confirmDeleteVersion = null },
            title = { Text("确认删除版本") },
            text = { Text("确定要永久删除版本 V${version.name} 吗？无法恢复！") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteVersion = null
                    scope.rdiRequestU(
                        path = "modpack/$modpackId/version/${version.name}",
                        method = HttpMethod.Delete,
                        onOk = {
                            okMessage = "删完了"
                            reload()
                        },
                        onErr = { errorMessage = "删除失败: ${it.message}" }
                    )
                }) {
                    Text("删除", color = MaterialTheme.colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteVersion = null }) {
                    Text("取消")
                }
            }
        )
    }

    confirmRebuildVersion?.let { version ->
        AlertDialog(
            onDismissRequest = { confirmRebuildVersion = null },
            title = { Text("确认重构版本") },
            text = { Text("整合包出现mod不完整等问题，可重构以解决。确定吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRebuildVersion = null
                    scope.rdiRequestU(
                        path = "modpack/$modpackId/version/${version.name}/rebuild",
                        method = HttpMethod.Post,
                        onOk = {
                            okMessage = "提交请求了 完事了发信箱告诉你"
                            reload()
                        },
                        onErr = { errorMessage = "重构失败: ${it.message}" }
                    )
                }) {
                    Text("重构")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRebuildVersion = null }) {
                    Text("取消")
                }
            }
        )
    }

}
