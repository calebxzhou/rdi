package calebxzhou.rdi.client.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.navigation.compose.composable
import calebxzhou.rdi.client.service.fetchHwSpec
import calebxzhou.rdi.client.service.UpdateService
import calebxzhou.rdi.client.ui.screen.ModpackList
import calebxzhou.rdi.client.ui.screen.ModpackUpload
import calebxzhou.rdi.common.hwspec.HwSpec
import java.io.File

/**
 * Android implementation requires a Context. We store a reference
 * from MainActivity initialization.
 */
object AndroidPlatform {
    lateinit var appContext: Context
}

actual val isDesktop: Boolean = false

@Composable
actual fun platformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun platformKeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val previous = view.keepScreenOn
        view.keepScreenOn = enabled || previous
        onDispose {
            view.keepScreenOn = previous
        }
    }
}

actual fun copyToClipboard(text: String) {
    val cm = AndroidPlatform.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("rdi", text))
}

actual fun openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    AndroidPlatform.appContext.startActivity(intent)
}

actual fun openMsaVerificationUrl(url: String) {
    openUrl(url)
}

actual suspend fun pickSaveFile(suggestedName: String, extension: String): File? {
    // On Android, save directly to Downloads directory
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    downloadsDir.mkdirs()
    var file = File(downloadsDir, suggestedName)
    if (!file.name.endsWith(".$extension", ignoreCase = true)) {
        file = File(downloadsDir, "${suggestedName}.$extension")
    }
    return file
}

actual fun checkCanCreateSymlink(): Boolean = true // Android doesn't need symlinks

actual suspend fun runDesktopUpdateFlow(
    onStatus: (String) -> Unit,
    onDetail: (String) -> Unit,
    onRestart: suspend () -> Unit
) {
    UpdateService.startUpdateFlow(
        onStatus = onStatus,
        onDetail = onDetail,
        onRestart = null
    )
}

actual fun createDesktopShortcut(): Result<Unit> =
    Result.failure(UnsupportedOperationException("Not supported on Android"))

actual fun loadResourceStream(name: String): java.io.InputStream {
    return AndroidPlatform.appContext.assets.open(name)
}

actual fun exportResource(name: String, target: File) {
    loadResourceStream(name).use { input ->
        target.parentFile?.mkdirs()
        target.outputStream().use { output -> input.copyTo(output) }
    }
}

actual fun loadImageBitmap(resourceName: String): androidx.compose.ui.graphics.ImageBitmap {
    return loadResourceStream(resourceName).use { stream ->
        val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
            ?: throw IllegalStateException("Failed to decode image: $resourceName")
        bitmap.asImageBitmap()
    }
}

actual fun checkLauncherInstalled(): Boolean {
    val packages = listOf("com.tungsten.fcl", )
    val pm = AndroidPlatform.appContext.packageManager
    return packages.any { pkg ->
        try {
            pm.getPackageInfo(pkg, 0)
            true
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }
}

actual fun openGameLauncher() {
    val packages = listOf("com.tungsten.fcl", )
    val pm = AndroidPlatform.appContext.packageManager
    for (pkg in packages) {
        val intent = pm.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            AndroidPlatform.appContext.startActivity(intent)
            return
        }
    }
}

actual fun openFolder(path: String) {
    // No-op on Android — user can use system file manager
}

actual fun decodeImageBitmap(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap {
    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw IllegalArgumentException("Failed to decode image")
    return bmp.asImageBitmap()
}

actual fun imageBitmapFromArgb(
    argb: IntArray,
    width: Int,
    height: Int
): androidx.compose.ui.graphics.ImageBitmap {
    require(width > 0 && height > 0) { "Invalid bitmap size: ${width}x$height" }
    val size = width * height
    val safePixels = if (argb.size >= size) {
        argb
    } else {
        IntArray(size).also { argb.copyInto(it, endIndex = argb.size) }
    }
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    bitmap.setPixels(safePixels, 0, width, 0, 0, width, height)
    return bitmap.asImageBitmap()
}

actual fun getPlatformTotalPhysicalMemoryMb(): Int = 0

actual fun validatePlatformJavaPath(rawPath: String, expectedMajor: Int): Result<Unit> =
    Result.success(Unit) // Not applicable on Android

actual fun androidx.navigation.NavGraphBuilder.addDesktopOnlyRoutes(
    navController: androidx.navigation.NavHostController
) {
    composable<ModpackUpload> {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Android 暂不支持上传整合包")
            TextButton(onClick = {
                navController.navigate(ModpackList) {
                    popUpTo<ModpackList> { inclusive = true }
                    launchSingleTop = true
                    restoreState = false
                }
            }) {
                Text("返回整合包列表")
            }
        }
    }
}

actual fun getHwSpecJson(): String {
    val hwSpec = runCatching { fetchHwSpec() }
        .onFailure {
            // Some Android devices cannot initialize OSHI/JNA native probes.
            Log.w("RDI-HwSpec", "Failed to fetch hardware info on Android, fallback to empty HwSpec.", it)
        }
        .getOrElse {
            HwSpec(
                model = "",
                os = "",
                cpu = HwSpec.Cpu(name = "", cores = 0, threads = 0, frequency = 0L),
                gpus = emptyList(),
                mems = emptyList(),
                disk = emptyList(),
                display = emptyList(),
                videoMode = emptyList()
            )
        }
    return calebxzhou.rdi.common.serdesJson.encodeToString<HwSpec>(hwSpec)
}
