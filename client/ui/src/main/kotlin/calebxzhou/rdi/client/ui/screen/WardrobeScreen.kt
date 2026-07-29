package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.RThinTextField
import calebxzau.rdi.client.ui.TitleRow
import calebxzau.rdi.client.ui.wM
import calebxzhou.rdi.client.model.BSSkinData
import calebxzhou.rdi.client.service.SkinService
import calebxzhou.rdi.client.ui.comp.HttpImage
import calebxzhou.rdi.common.net.httpRequest
import calebxzhou.rdi.common.serdesJson
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable


@Composable
fun WardrobeScreen(
    onBack: (() -> Unit) = {},
    onOpenSkinPreview: (BSSkinData) -> Unit = {}
) {
    val urlPrefix = "https://littleskin.cn"
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val snackbarHostState = remember { SnackbarHostState() }

    val keywordState = rememberTextFieldState()
    var capeMode by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var hasMoreData by remember { mutableStateOf(true) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var showMojangDialog by remember { mutableStateOf(false) }

    val skins = remember { mutableStateListOf<BSSkinData>() }

    fun refreshSkins() {
        if (loading) return
        loading = true
        page = 1
        hasMoreData = true
        skins.clear()
        scope.launch {
            val keyword = keywordState.text.toString()
            val newSkins = withContext(Dispatchers.IO) {
                querySkins(urlPrefix, page, keyword, capeMode)
            }
            if (newSkins.isNotEmpty()) {
                skins.addAll(newSkins)
            } else {
                toastMessage = "没有找到相关皮肤"
                hasMoreData = false
            }
            loading = false
        }
    }

    fun loadMoreSkins() {
        if (loading || !hasMoreData) return
        loading = true
        page += 1
        scope.launch {
            val keyword = keywordState.text.toString()
            val newSkins = withContext(Dispatchers.IO) {
                querySkins(urlPrefix, page, keyword, capeMode)
            }
            if (newSkins.isNotEmpty()) {
                skins.addAll(newSkins)
            } else {
                hasMoreData = false
                toastMessage = "没有更多皮肤了"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshSkins()
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastIndex >= skins.size - 6
        }
    }

    LaunchedEffect(shouldLoadMore, loading, hasMoreData) {
        if (shouldLoadMore) {
            loadMoreSkins()
        }
    }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.LARGE) {
            TitleRow("衣柜", onBack) {
                RThinTextField(
                    state = keywordState,
                    label = "搜索",
                    modifier = Modifier
                        .width(200.dp)
                        .height(36.dp)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                refreshSkins()
                                true
                            } else {
                                false
                            }
                        }
                )
                Checkbox(
                    checked = capeMode,
                    onCheckedChange = {
                        capeMode = it
                        refreshSkins()
                    }
                )
                Text("披风")
                Spacer(8.wM)
                CircleIconButton("\uDB81\uDDB3", "导入正版皮肤",) {
                    showMojangDialog = true
                }
            }

            ContentBody {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = skins,
                        key = { index, skin -> "${skin.tid}-$index" }
                    ) { _, skin ->
                        SkinCard(
                            skin = skin,
                            urlPrefix = urlPrefix,
                            onClick = { onOpenSkinPreview(skin) }
                        )
                    }
                    if (loading) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }

    if (showMojangDialog) {
        MojangSkinDialog(
            onDismiss = { showMojangDialog = false },
            onToast = { toastMessage = it }
        )
    }
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            toastMessage = null
        }
    }
}
@Composable
fun MojangSkinDialog(onDismiss: () -> Unit, onToast: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var importSkin by remember { mutableStateOf(true) }
    var importCape by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("导入正版皮肤/披风") },
        text = {
            Column {
                Text("")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.replace("\n", "").replace("\r", "") },
                    label = { Text("正版玩家名") },
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = importSkin,
                        onCheckedChange = { importSkin = it },
                        enabled = !loading
                    )
                    Text("导入皮肤")
                    Spacer(modifier = Modifier.width(12.dp))
                    Checkbox(
                        checked = importCape,
                        onCheckedChange = { importCape = it },
                        enabled = !loading
                    )
                    Text("导入披风")
                }
                if (loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (loading) return@Button
                    val trimmedName = name.trim()
                    if (trimmedName.isEmpty()) {
                        onToast("请输入玩家名")
                        errorMessage = null
                        return@Button
                    }
                    if (!importSkin && !importCape) {
                        onToast("请选择皮肤或披风")
                        errorMessage = null
                        return@Button
                    }
                    loading = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            SkinService.importMojangSkin(name, importSkin, importCape)
                        }
                        loading = false
                        result.onSuccess {
                            errorMessage = null
                            onDismiss()
                            onToast("导入成功")
                        }.onFailure { err ->
                            errorMessage = err.message ?: "导入失败"
                        }
                    }
                },
                enabled = !loading
            ) {
                Text("导入")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
                TextButton(onClick = { if (!loading) onDismiss() }) {
                    Text("取消")
                }
            }
        }
    )
}
@Composable
private fun SkinCard(
    skin: BSSkinData,
    urlPrefix: String,
    onClick: () -> Unit
) {
    val previewUrl = remember(skin.tid) { "$urlPrefix/preview/${skin.tid}?height=150&png" }

    Box(
        modifier = Modifier
            .size(150.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
    ) {
        HttpImage(previewUrl)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0x88000000))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${sanitizeName(skin.name)} ♥${skin.likes}",
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private suspend fun querySkins(
    urlPrefix: String,
    page: Int,
    keyword: String,
    cape: Boolean
): List<BSSkinData> {
    val datas = mutableListOf<BSSkinData>()
    val startPage = (page - 1) * 2 + 1

    for (subpage in 0..1) {
        val currentPage = startPage + subpage
        try {
            val response = httpRequest {
                url(
                    "$urlPrefix/skinlib/list?filter=${if (cape) "cape" else "skin"}" +
                            "&sort=likes&page=$currentPage&keyword=$keyword"
                )
            }
            @Serializable
            data class BSSkinListResp(
                val current_page: Int,
                val data: List<BSSkinData>
            )
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val skinData = serdesJson.decodeFromString<BSSkinListResp>(body).data
                datas.addAll(skinData)
            }
            if (subpage < 1) {
                delay(300)
            }
        } catch (_: Exception) {
        }
    }

    return datas
}

private fun sanitizeName(name: String): String {
    val trimmed = if (name.length > 6) "${name.substring(0, 5)}..." else name
    return trimmed.replace(
        Regex("[^\\p{L}\\p{N}\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{InCJKUnifiedIdeographs}]"),
        ""
    )
}
