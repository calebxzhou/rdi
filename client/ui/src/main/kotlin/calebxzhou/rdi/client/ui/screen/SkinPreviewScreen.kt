package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import calebxzau.rdi.client.ui.*
import calebxzau.rdi.client.blessingskin.BlessingSkinClient
import calebxzau.rdi.client.blessingskin.ResolvedBlessingTexture
import calebxzhou.rdi.client.auth.AccountSessionStore
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.service.SkinService
import calebxzhou.rdi.client.service.playerInfoCache
import calebxzhou.rdi.client.ui.comp.PlayerModel
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.mykotutils.log.Loggers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val lgr by Loggers

/**
 * calebxzhou @ 2026-02-27 19:18
 */
private data class PreviewCloth(
    val skinUrl: String,
    val capeUrl: String?,
    val isSlim: Boolean
)

private data class PreviewLoadState(
    val texture: ResolvedBlessingTexture? = null,
    val cloth: PreviewCloth? = null,
    val error: String? = null
)


@Composable
fun SkinPreviewScreen(
    blessingSkin: BlessingSkinClient,
    textureId: Int,
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var applying by remember { mutableStateOf(false) }

    val loadState by produceState(
        initialValue = PreviewLoadState(),
        textureId,
        loggedAccount.cloth.skin,
        loggedAccount.cloth.cape
    ) {
        val result = withContext(Dispatchers.IO) {
            blessingSkin.resolve(textureId).map { texture ->
                texture to resolvePreviewCloth(texture, loggedAccount.cloth)
            }
        }
        value = result.fold(
            onSuccess = { (texture, cloth) -> PreviewLoadState(texture, cloth) },
            onFailure = { err ->
                lgr.warn { "Blessing Skin预览加载失败\n$err" }
                PreviewLoadState(error = err.message ?: "加载预览失败")
            }
        )
    }

    MaxBox {
        ScreenContentSurface(size = ScreenContentSize.MEDIUM) {
            TitleRow(title = "预览：${loadState.texture?.name ?: "皮肤"}", onBack = onBack) {
                CircleIconButton(
                    icon = "\uF00C",
                    label = "确认使用",
                    enabled = !applying && loadState.cloth != null && loadState.texture != null
                ) {
                    val texture = loadState.texture ?: return@CircleIconButton
                    if (applying || loadState.cloth == null) return@CircleIconButton
                    applying = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            SkinService.applyBlessingTexture(loggedAccount.cloth, texture)
                        }
                        applying = false
                        if (result.isSuccess) {
                            val cloth = result.getOrThrow()
                            AccountSessionStore.updateCloth(cloth)
                            playerInfoCache.put(AccountSessionStore.current.dto)
                            onBack()
                        } else {
                            val err = result.exceptionOrNull() ?: IllegalStateException("设置皮肤失败")
                            lgr.warn { "Blessing Skin设置失败\n$err" }
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

private fun resolvePreviewCloth(
    selected: ResolvedBlessingTexture,
    current: RAccount.Cloth
): PreviewCloth {
    return if (selected.type.isCape) {
        PreviewCloth(
            skinUrl = current.skin,
            capeUrl = selected.textureUrl,
            isSlim = current.isSlim
        )
    } else {
        PreviewCloth(
            skinUrl = selected.textureUrl,
            capeUrl = current.cape,
            isSlim = selected.type.isSlim
        )
    }
}
