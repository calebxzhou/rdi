package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.RadioButton as M3RadioButton
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import calebxzau.rdi.client.ui.AlertErr
import calebxzau.rdi.client.ui.BottomSnakebarM3
import calebxzau.rdi.client.ui.ConfirmDialog
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.RVerticalScrollbar
import calebxzau.rdi.client.ui.Space8w
import calebxzau.rdi.client.ui.TinyClickCopyText
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzau.rdi.client.ui.themeNow
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.service.GithubReleaseAsset
import calebxzhou.rdi.client.ui.*
import calebxzau.rdi.client.ui.viewmodel.ExtraModDraft
import calebxzau.rdi.client.ui.viewmodel.HostInfoViewModel
import calebxzau.rdi.client.ui.viewmodel.HostModsEvent
import calebxzau.rdi.client.ui.viewmodel.HostModsViewModel
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.model.isAdmin
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.bson.types.ObjectId

/**
 * calebxzhou @ 2026-01-15 19:38
 */

@Composable
fun HostInfoScreen(
    hostId: ObjectId,
    onBack: () -> Unit = {},
    onOpenModpackInfo: (String) -> Unit,
    viewModel: HostInfoViewModel = koinViewModel(key = "host-info:$hostId") {
        parametersOf(hostId)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HostDetailSurface(
        size = ScreenContentSize.SMALL,
        title = state.host?.name ?: "房间详情",
        loading = state.loading,
        errorMessage = state.errorMessage,
        onBack = onBack,
        titleActions = {
            state.host?.let {
                Column {
                    TinyClickCopyText("hid", it._id.toHexString())
                    TinyClickCopyText("mid", it.modpack.id.toHexString())
                    TinyClickCopyText("wid", it.worldId?.toHexString())
                }
            }
        },
    ) {
        state.host?.let { HostOverviewPane(it, onOpenModpackInfo) }
    }
}

/** Shared route entry for the released Legacy room contract. */
@Composable
fun HostInfoScreen(
    target: HostTarget,
    onBack: () -> Unit = {},
    onOpenModpackInfo: (String) -> Unit = {},
    onOpenPlay: ((calebxzhou.rdi.client.ui.McPlayArgs) -> Unit)? = null,
    onOpenTaskList: (String) -> Unit = {},
) {
    if (target.kind == HostKind.Legacy) {
        val objectId = target.objectIdOrNull()
        if (objectId == null) {
            HostDetailRouteError("房间ID格式错误", onBack)
        } else {
            HostInfoScreen(objectId, onBack, onOpenModpackInfo)
        }
    } else {
        HostDetailRouteError("该房间类型暂不可用", onBack)
    }
}

@Composable
internal fun HostDetailRouteError(message: String, onBack: () -> Unit) {
    MaxBox {
        ScreenContentSurface(ScreenContentSize.SMALL) {
            TitleRow("房间详情", onBack)
            ContentBody { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message, color = MaterialTheme.colorScheme.error) } }
        }
    }
}

@Composable
fun HostModsScreen(
    modCatalog: ModCatalog,
    hostId: ObjectId,
    onBack: () -> Unit,
    onOpenResourceMods: (McVersion, ModLoader) -> Unit,
    onOpenTaskList: (String) -> Unit,
    viewModel: HostModsViewModel = koinViewModel(key = "host-mods:$hostId") {
        parametersOf(hostId)
    },
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember(hostId) { mutableStateOf(false) }
    var removeConfirm by remember(hostId) { mutableStateOf<List<Mod>?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HostModsEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                HostModsEvent.ExtraModAdded -> showAddDialog = false
            }
        }
    }
    state.errorMessage?.let { AlertErr(it, viewModel::clearError) }

    HostDetailSurface(
        size = ScreenContentSize.FULL,
        title = state.host?.let { "${it.name} - 模组" } ?: "房间模组",
        loading = state.loading,
        errorMessage = state.errorMessage,
        onBack = onBack,
        titleActions = { Text("增删mod可能会导致整合包bug！", fontWeight = FontWeight.SemiBold) },
        snackbarHostState = snackbarHostState,
    ) {
        val host = state.host ?: return@HostDetailSurface
        val canViewMods = host.ownerId == loggedAccount._id ||
            host.members.any { member -> member.id == loggedAccount._id } ||
            loggedAccount.isDav
        if (!canViewMods) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("仅房间成员可查看模组", color = MaterialTheme.colorScheme.error)
            }
        } else {
            val canManage = host.isAdmin(loggedAccount) || host.ownerId == loggedAccount._id || loggedAccount.isDav
            HostModsPane(
                modCatalog = modCatalog,
                hostId = hostId,
                extraMods = state.extraMods,
                baseVersionMods = state.baseVersionMods,
                disabledMods = state.disabledMods,
                canManage = canManage,
                addExtraModLoading = state.extraModLoading,
                addExtraModLoadingText = state.extraModLoadingText,
                onChangeDisabledMods = viewModel::changeDisabledMods,
                onRemoveExtraMods = { removeConfirm = it },
                onOpenResourceMods = { onOpenResourceMods(host.modpack.mcVer, host.modpack.modloader) },
                onAddExtraModAdvanced = {
                    viewModel.resetExtraModDraft()
                    showAddDialog = true
                },
                onOk = { scope.launch { snackbarHostState.showSnackbar(it) } },
                onError = { scope.launch { snackbarHostState.showSnackbar(it) } },
                onOpenTaskList = onOpenTaskList,
            )
        }
    }

    if (showAddDialog) {
        HostExtraModDialog(
            state = state,
            onDismiss = {
                if (!state.extraModLoading) {
                    viewModel.resetExtraModDraft()
                    showAddDialog = false
                }
            },
            viewModel = viewModel,
        )
    }
    removeConfirm?.let { mods ->
        val ids = mods.map(Mod::projectId).distinct()
        ConfirmDialog(
            title = "删除附加Mod",
            message = if (ids.size > 1) {
                "确定删除已选择的${ids.size}个附加Mod吗？"
            } else {
                "确定删除附加Mod《${mods.single().displaySlugOrProject}》吗？"
            },
            onConfirm = {
                viewModel.removeExtraMods(mods)
                removeConfirm = null
            },
            onDismiss = { removeConfirm = null },
        )
    }
}

@Composable
fun HostModsScreen(
    modCatalog: ModCatalog,
    target: HostTarget,
    onBack: () -> Unit,
    onOpenResourceMods: (McVersion, ModLoader) -> Unit,
    onOpenTaskList: (String) -> Unit,
) {
    if (target.kind == HostKind.Legacy) {
        val objectId = target.objectIdOrNull()
        if (objectId == null) HostDetailRouteError("房间ID格式错误", onBack)
        else HostModsScreen(modCatalog, objectId, onBack, onOpenResourceMods, onOpenTaskList)
    } else {
        HostDetailRouteError("该房间类型暂不可用", onBack)
    }
}

@Composable
private fun HostDetailSurface(
    size: ScreenContentSize,
    title: String,
    loading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    titleActions: @Composable RowScope.() -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable () -> Unit,
) {
    MaxBox {
        ScreenContentSurface(size) {
            TitleRow(title, onBack) { titleActions() }
            ContentBody {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                    }
                    else -> content()
                }
            }
        }
        BottomSnakebarM3(snackbarHostState)
    }
}

@Composable
private fun HostExtraModDialog(
    state: calebxzau.rdi.client.ui.viewmodel.HostModsUiState,
    onDismiss: () -> Unit,
    viewModel: HostModsViewModel,
) {
    val draft = state.extraModDraft
    val listState = rememberLazyListState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.72f).fillMaxHeight(0.86f),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
        ) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("添加附加Mod（高级模式）", style = MaterialTheme.typography.titleSmall)
                Text("仅供高级玩家使用。通常情况不建议使用此功能")
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { ExtraModPlatformRow(draft, state.extraModLoading, viewModel) }
                        if (draft.platform == "github") {
                            item { GithubExtraModFields(draft, state.extraModLoading, viewModel) }
                            draft.githubReleases.forEach { release ->
                                item { Text(release.name.ifBlank { release.tagName }, fontSize = 17.sp) }
                                items(release.assets, key = { it.key }) { asset ->
                                    GithubReleaseAssetRow(
                                        asset = asset,
                                        selected = draft.selectedGithubAsset?.key == asset.key,
                                        enabled = !state.extraModLoading,
                                        onClick = { viewModel.selectGithubAsset(asset) },
                                    )
                                }
                            }
                        } else {
                            item { ManualExtraModFields(draft, state.extraModLoading, viewModel) }
                        }
                        item { ExtraModSideRow(draft, state.extraModLoading, viewModel) }
                    }
                    RVerticalScrollbar(listState, Modifier.align(Alignment.CenterEnd))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    val status = state.extraModError ?: state.extraModLoadingText
                    if (!status.isNullOrBlank()) {
                        Text(
                            status,
                            color = if (state.extraModError == null) themeNow.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(enabled = !state.extraModLoading, onClick = onDismiss) { Text("取消") }
                    Space8w()
                    TextButton(
                        enabled = !state.extraModLoading,
                        onClick = {
                            if (draft.platform == "github") viewModel.submitGithubExtraMod()
                            else viewModel.submitManualExtraMod()
                        },
                    ) {
                        Text(if (draft.platform == "github" && draft.githubReleases.isEmpty()) "下一步" else "添加")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraModPlatformRow(draft: ExtraModDraft, loading: Boolean, viewModel: HostModsViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("平台", color = themeNow.onSurfaceVariant)
        listOf("github" to "GitHub", "mr" to "Modrinth", "cf" to "CurseForge").forEach { (value, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                M3RadioButton(draft.platform == value, enabled = !loading, onClick = { viewModel.switchExtraModPlatform(value) })
                Text(label)
            }
        }
    }
}

@Composable
private fun GithubExtraModFields(draft: ExtraModDraft, loading: Boolean, viewModel: HostModsViewModel) {
    if (draft.githubReleases.isEmpty()) {
        OutlinedTextField(
            value = draft.githubRepoUrl,
            onValueChange = { viewModel.updateExtraModDraft(draft.copy(githubRepoUrl = it)) },
            enabled = !loading,
            label = { Text("GitHub仓库链接") },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(draft.githubRepo?.projectId.orEmpty(), fontSize = 18.sp)
                Text("选择正确的jar文件，否则房间将无法启动。", color = themeNow.onSurfaceVariant)
            }
            TextButton(enabled = !loading, onClick = viewModel::resetGithubRepo) { Text("重新输入") }
        }
    }
}

@Composable
private fun ManualExtraModFields(draft: ExtraModDraft, loading: Boolean, viewModel: HostModsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("【开发用界面】请使用“资源-模组”界面添加附加mod。")
        listOf(
            "pj" to draft.projectId,
            "slug" to draft.slug,
            "file" to draft.fileId,
            "hash" to draft.hash,
            "dl url" to draft.downloadUrls,
        ).forEach { (label, value) ->
            OutlinedTextField(
                value = value,
                onValueChange = {
                    viewModel.updateExtraModDraft(
                        when (label) {
                            "pj" -> draft.copy(projectId = it)
                            "slug" -> draft.copy(slug = it)
                            "file" -> draft.copy(fileId = it)
                            "hash" -> draft.copy(hash = it)
                            else -> draft.copy(downloadUrls = it)
                        }
                    )
                },
                enabled = !loading,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ExtraModSideRow(draft: ExtraModDraft, loading: Boolean, viewModel: HostModsViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("使用侧", color = themeNow.onSurfaceVariant)
        listOf(Mod.Side.BOTH, Mod.Side.CLIENT, Mod.Side.SERVER).forEach { side ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                M3RadioButton(
                    selected = draft.side == side,
                    enabled = !loading,
                    onClick = { viewModel.updateExtraModDraft(draft.copy(side = side)) },
                )
                Text(side.text)
            }
        }
    }
}

@Composable
private fun GithubReleaseAssetRow(
    asset: GithubReleaseAsset,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) themeNow.secondaryContainer else themeNow.onSecondary,
                RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(asset.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${asset.releaseTag} · ${asset.sizeText}",
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) Text("已选")
    }
}
