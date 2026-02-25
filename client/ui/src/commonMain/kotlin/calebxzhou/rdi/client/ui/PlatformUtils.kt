package calebxzhou.rdi.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import java.io.InputStream

/**
 * Whether the current platform is desktop (JVM).
 */
expect val isDesktop: Boolean

/**
 * Handle system back press on the current platform.
 * Android: intercepts device back and calls [onBack] when [enabled].
 * Desktop: no-op.
 */
@Composable
expect fun platformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/**
 * Keep the screen awake while the current screen is visible.
 * Android: uses View.keepScreenOn.
 * Desktop: no-op.
 */
@Composable
expect fun platformKeepScreenOn(enabled: Boolean = true)

/**
 * Copy text to system clipboard.
 */
expect fun copyToClipboard(text: String)

/**
 * Open a URL in the system browser.
 */
expect fun openUrl(url: String)

/**
 * Open the Microsoft login verification URL.
 * Desktop: opens in system browser.
 * Android: opens via openUrl (user may add webview later).
 */
expect fun openMsaVerificationUrl(url: String)

/**
 * Show a platform file save dialog.
 * @param suggestedName default file name
 * @param extension file extension filter (e.g. "zip")
 * @return selected File, or null if user cancelled
 */
expect suspend fun pickSaveFile(suggestedName: String, extension: String): File?

/**
 * Check whether the OS supports creating symlinks.
 * Desktop: delegates to canCreateSymlink().
 * Android: always returns true (no symlink needed).
 */
expect fun checkCanCreateSymlink(): Boolean

/**
 * Run update flow.
 * Desktop: update MC cores + UI libs and may restart.
 * Android: update MC cores only.
 */
expect suspend fun runDesktopUpdateFlow(
    onStatus: (String) -> Unit,
    onDetail: (String) -> Unit,
    onRestart: suspend () -> Unit
)

/**
 * Create a desktop shortcut (Windows only).
 * Android: returns failure.
 */
expect fun createDesktopShortcut(): Result<Unit>

/**
 * Load a resource stream by name (e.g. "mcmeta/assets-index/1.20.json").
 * Desktop: from JAR resources. Android: from app assets.
 */
expect fun loadResourceStream(name: String): InputStream

/**
 * Export a resource to a target file.
 * Desktop: extracts from JAR. Android: copies from assets.
 */
expect fun exportResource(name: String, target: File)

/**
 * Load an ImageBitmap from a bundled resource name (e.g. "mc119.png").
 * Desktop: Skia Image. Android: BitmapFactory.
 */
expect fun loadImageBitmap(resourceName: String): ImageBitmap

/**
 * Check if the game launcher app is installed.
 * Desktop: always true. Android: checks for com.tungsten.fcl.
 */
expect fun checkLauncherInstalled(): Boolean

/**
 * Open the external game launcher (FCL on Android).
 * Desktop: no-op. Android: launches com.tungsten.fcl via intent.
 */
expect fun openGameLauncher()

/**
 * Open a folder/directory in the system file manager.
 * Desktop: java.awt.Desktop.open(). Android: no-op.
 */
expect fun openFolder(path: String)

/**
 * Decode raw image bytes into a Compose ImageBitmap.
 * Desktop: uses Skia. Android: uses BitmapFactory.
 */
expect fun decodeImageBitmap(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap

/**
 * Build an ImageBitmap directly from ARGB pixels (0xAARRGGBB).
 */
expect fun imageBitmapFromArgb(
    argb: IntArray,
    width: Int,
    height: Int
): ImageBitmap

/**
 * Get total physical memory in MB.
 * Desktop: uses OperatingSystemMXBean. Android: returns 0.
 */
expect fun getPlatformTotalPhysicalMemoryMb(): Int

/**
 * Validate a Java installation path (check executable + version).
 * Desktop: resolves path and runs `java -version`. Android: returns success (not applicable).
 */
expect fun validatePlatformJavaPath(rawPath: String, expectedMajor: Int): Result<Unit>

/**
 * Register desktop-only navigation routes (McPlayView, ModpackUpload) into the NavGraphBuilder.
 * Desktop: adds the actual composable routes. Android: no-op.
 */
expect fun androidx.navigation.NavGraphBuilder.addDesktopOnlyRoutes(
    navController: androidx.navigation.NavHostController
)

/**
 * Get hardware spec as a JSON string for login.
 * Desktop: uses oshi/HwSpec. Android: returns a stub JSON.
 */
expect fun getHwSpecJson(): String
