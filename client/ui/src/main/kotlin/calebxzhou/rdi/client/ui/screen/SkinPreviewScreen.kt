package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import calebxzhou.rdi.client.model.BSSkin
import calebxzhou.rdi.client.model.BSSkinData
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.service.SkinService
import calebxzhou.rdi.client.auth.AccountSessionStore
import calebxzau.rdi.client.ui.BottomSnakebarM3
import calebxzau.rdi.client.ui.CircleIconButton
import calebxzau.rdi.client.ui.ContentBody
import calebxzau.rdi.client.ui.MaxBox
import calebxzau.rdi.client.ui.ScreenContentSize
import calebxzau.rdi.client.ui.ScreenContentSurface
import calebxzau.rdi.client.ui.TitleRow
import calebxzhou.rdi.client.ui.comp.PlayerModel
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.net.httpRequest
import calebxzhou.rdi.common.serdesJson
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * calebxzhou @ 2026-02-27 19:18
 */
private const val BLESSING_URL_PREFIX = "https://littleskin.cn"

private data class PreviewCloth(
    val skinUrl: String,
    val capeUrl: String?,
    val isSlim: Boolean
)

private data class PreviewLoadState(
    val cloth: PreviewCloth? = null,
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinPreviewScreen(
    skin: BSSkinData,
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var applying by remember { mutableStateOf(false) }

    val loadState by produceState(
        initialValue = PreviewLoadState(),
        skin.tid,
        loggedAccount.cloth.skin,
        loggedAccount.cloth.cape
    ) {
        val result = withContext(Dispatchers.IO) {
            resolvePreviewCloth(
                urlPrefix = BLESSING_URL_PREFIX,
                selected = skin,
                current = loggedAccount.cloth
            )
        }
        value = result.fold(
            onSuccess = { PreviewLoadState(cloth = it) },
            onFailure = { err -> PreviewLoadState(error = err.message ?: "加载预览失败") }
        )
    }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.MEDIUM) {
            TitleRow(title = "预览：${skin.name}", onBack = onBack) {
                CircleIconButton(
                    icon = "\uF00C",
                    tooltip = "确认使用",
                    enabled = !applying && loadState.cloth != null
                ) {
                    if (applying || loadState.cloth == null) return@CircleIconButton
                    applying = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            SkinService.applyBlessingSkin(BLESSING_URL_PREFIX, skin)
                        }
                        applying = false
                        result.onSuccess { cloth ->
                            AccountSessionStore.updateCloth(cloth)
                            onBack()
                        }
                        result.onFailure { err ->
                            snackbarHostState.showSnackbar(
                                message = err.message ?: "设置失败",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                }
            }

            ContentBody {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        loadState.cloth != null -> {
                            val cloth = loadState.cloth!!
                            PlayerModel(
                                skinUrl = cloth.skinUrl,
                                capeUrl = cloth.capeUrl,
                                modifier = Modifier.fillMaxSize(0.9f),
                                backgroundColor = Color.Transparent,
                                autoRotate = true,
                                animateWalk = true,
                                showOuterLayer = true,
                                isSlim = cloth.isSlim,
                                maxRenderSide = 960
                            )
                        }

                        loadState.error != null -> {
                            Text(
                                text = loadState.error!!,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        else -> {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
        BottomSnakebarM3(snackbarHostState)
    }
}

private suspend fun resolvePreviewCloth(
    urlPrefix: String,
    selected: BSSkinData,
    current: RAccount.Cloth
): Result<PreviewCloth> = runCatching {
    val data = fetchBlessingTexture(urlPrefix, selected.tid).getOrThrow()
    val selectedUrl = "$urlPrefix/textures/${data.hash}"
    if (selected.isCape) {
        PreviewCloth(
            skinUrl = current.skin,
            capeUrl = selectedUrl,
            isSlim = current.isSlim
        )
    } else {
        PreviewCloth(
            skinUrl = selectedUrl,
            capeUrl = current.cape,
            isSlim = selected.isSlim
        )
    }
}

private suspend fun fetchBlessingTexture(
    urlPrefix: String,
    tid: Int
): Result<BSSkin> = runCatching {
    val response = httpRequest { url("$urlPrefix/texture/$tid") }
    if (!response.status.isSuccess()) {
        throw RequestError("获取皮肤数据失败: ${response.bodyAsText()}")
    }
    serdesJson.decodeFromString<BSSkin>(response.bodyAsText())
}
