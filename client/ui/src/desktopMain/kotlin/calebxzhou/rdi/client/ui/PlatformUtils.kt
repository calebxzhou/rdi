package calebxzhou.rdi.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.navigation.compose.composable
import calebxzhou.mykotutils.std.jarResource
import calebxzhou.mykotutils.std.readAllString
import calebxzhou.rdi.RDIClient
import calebxzhou.rdi.client.service.getCachedOrFetchHwSpecJson
import calebxzhou.rdi.client.ui.comp.DesktopWebViewHost
import calebxzhou.rdi.client.ui.screen.HostRoute
import calebxzhou.rdi.client.ui.screen.HostTab
import calebxzhou.rdi.client.ui.screen.McPlayView
import calebxzhou.rdi.client.ui.screen.ModpackUpload
import calebxzhou.rdi.client.ui.screen.ResourceRoute
import calebxzhou.rdi.client.ui.screen.ResourceTab
import calebxzhou.rdi.client.ui.screen.TaskList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI

actual val isDesktop: Boolean = true

@Composable
actual fun platformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No system back concept on desktop.
}

@Composable
actual fun platformKeepScreenOn(enabled: Boolean) {
    // No screen sleep handling here.
}

actual fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
}

actual fun openUrl(url: String) {
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(URI(url))
    }
}

actual fun openMsaVerificationUrl(url: String) {
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(URI(url))
    }
}

@Composable
actual fun PlatformWebView(
    url: String,
    title: String?,
    modifier: Modifier
) {
    DesktopWebViewHost(url = url, title = title, modifier = modifier)
}

actual suspend fun pickSaveFile(suggestedName: String, extension: String): File? =
    withContext(Dispatchers.IO) {
        pickAwtSaveFile(
            title = "选择保存位置",
            defaultFileName = suggestedName,
            defaultDirectory = File(System.getProperty("user.home")),
            requiredExtension = extension,
            filenameFilter = { _, name -> name.endsWith(".$extension", ignoreCase = true) }
        )
    }

actual suspend fun pickLocalMinecraftWorldDir(): String? =
    withContext(Dispatchers.IO) {
        pickAwtDirectory("选择本地存档目录")?.absolutePath
    }

actual suspend fun pickLocalModpackFile(): File? =
    withContext(Dispatchers.IO) {
        val owner = Frame()
        try {
            val dialog = FileDialog(owner, "选择客户端安装包", FileDialog.LOAD).apply {
                directory = File(System.getProperty("user.home"), "Downloads").absolutePath
                file = "*.zip;*.mrpack"
                filenameFilter = java.io.FilenameFilter { dir, name ->
                    val target = File(dir, name)
                    target.isDirectory ||
                        name.endsWith(".zip", ignoreCase = true) ||
                        name.endsWith(".mrpack", ignoreCase = true)
                }
            }
            dialog.isVisible = true
            val dir = dialog.directory ?: return@withContext null
            val name = dialog.file ?: return@withContext null
            val selected = File(dir, name)
            selected.takeIf {
                it.exists() && (
                    it.isDirectory ||
                        it.name.endsWith(".zip", ignoreCase = true) ||
                        it.name.endsWith(".mrpack", ignoreCase = true)
                    )
            }
        } finally {
            owner.dispose()
        }
    }

actual suspend fun pickLocalZipFile(title: String): File? =
    withContext(Dispatchers.IO) {
        val owner = Frame()
        try {
            val dialog = FileDialog(owner, title, FileDialog.LOAD).apply {
                directory = File(System.getProperty("user.home"), "Downloads").absolutePath
                file = "*.zip"
                filenameFilter = java.io.FilenameFilter { dir, name ->
                    val target = File(dir, name)
                    target.isDirectory || name.endsWith(".zip", ignoreCase = true)
                }
            }
            dialog.isVisible = true
            val dir = dialog.directory ?: return@withContext null
            val name = dialog.file ?: return@withContext null
            val selected = File(dir, name)
            selected.takeIf {
                it.exists() && it.isFile && it.name.endsWith(".zip", ignoreCase = true)
            }
        } finally {
            owner.dispose()
        }
    }

actual suspend fun pickLocalDirectory(title: String): File? =
    withContext(Dispatchers.IO) {
        pickAwtDirectory(title)
    }

actual suspend fun pickJavaExecutable(title: String): String? =
    withContext(Dispatchers.IO) {
        val owner = Frame()
        try {
            val dialog = FileDialog(owner, title, FileDialog.LOAD).apply {
                directory = File(System.getProperty("user.home")).absolutePath
                file = if (System.getProperty("os.name").contains("windows", ignoreCase = true)) {
                    "java.exe"
                } else {
                    "java"
                }
                filenameFilter = java.io.FilenameFilter { dir, name ->
                    val target = File(dir, name)
                    if (target.isDirectory) return@FilenameFilter true
                    val lower = name.lowercase()
                    lower == "java" || lower == "java.exe" || lower == "javaw.exe"
                }
            }
            dialog.isVisible = true
            val dir = dialog.directory ?: return@withContext null
            val name = dialog.file ?: return@withContext null
            File(dir, name)
                .takeIf { it.exists() && it.isFile }
                ?.absolutePath
        } finally {
            owner.dispose()
        }
    }

actual fun checkCanCreateSymlink(): Boolean {
    return calebxzhou.mykotutils.std.canCreateSymlink()
}

actual suspend fun runDesktopUpdateFlow(
    onStatus: (String) -> Unit,
    onDetail: (String) -> Unit,
    onRestart: suspend () -> Unit
) {
    calebxzhou.rdi.client.service.UpdateService.startUpdateFlow(
        onStatus = onStatus,
        onDetail = onDetail,
        onRestart = onRestart
    )
}

actual fun createDesktopShortcut(): Result<Unit> {
    return runCatching {
        val osName = System.getProperty("os.name")
        if (!osName.contains("windows", ignoreCase = true)) {
            throw IllegalStateException("仅支持 Windows")
        }
        val baseDir = File(".").absoluteFile
        val javaExe = File(calebxzhou.mykotutils.std.javaExePath)
        val javawCandidate = javaExe.parentFile?.resolve("javaw.exe")
        val javawPath = when {
            javaExe.name.equals("java.exe", ignoreCase = true) && javawCandidate?.exists() == true -> javawCandidate
            javaExe.name.equals("javaw.exe", ignoreCase = true) -> javaExe
            else -> javaExe
        }
        if (!javawPath.exists()) {
            throw IllegalStateException("未找到javaw: ${javawPath.absolutePath}")
        }
        val resourcesDir = File(baseDir, "resources").apply { mkdirs() }
        val iconFile = File(resourcesDir, "icon.ico")
        if (!iconFile.exists()) {
            calebxzhou.rdi.RDIClient.jarResource("icon.ico").use { input ->
                iconFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val args = "-cp \"lib/*;rdi-5-ui.jar\" calebxzhou.rdi.client.MainKt"
        fun esc(path: String) = path.replace("'", "''")
        val template = calebxzhou.rdi.RDIClient.jarResource("shortcut_maker.ps1").readAllString()
        val script = template
            .replace("__JAVAW__", esc(javawPath.absolutePath))
            .replace("__ARGS__", esc(args))
            .replace("__WORKDIR__", esc(baseDir.absolutePath))
            .replace("__ICON__", esc(iconFile.absolutePath))
        val scriptFile = File(resourcesDir, "shortcut_maker.ps1")
        scriptFile.outputStream().use { output ->
            output.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
            output.write(script.toByteArray(Charsets.UTF_16LE))
        }

        val psCommands = listOf("powershell", "pwsh")
        val proc = psCommands.firstNotNullOfOrNull { cmd ->
            runCatching {
                ProcessBuilder(
                    cmd,
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    scriptFile.absolutePath
                ).redirectErrorStream(true).start()
            }.getOrNull()
        }
        if (proc == null) {
            throw IllegalStateException("未找到 PowerShell 或 pwsh")
        }
        val exit = proc.waitFor()
        val shortcutPath = File(System.getProperty("user.home"), "Desktop").resolve("RDI.lnk")
        if (exit != 0 || !shortcutPath.exists()) {
            throw IllegalStateException("创建快捷方式失败")
        }
    }
}

actual fun loadResourceStream(name: String): java.io.InputStream {
    return RDIClient.jarResource(name)
}

actual fun exportResource(name: String, target: File) {
    loadResourceStream(name).use { input ->
        target.parentFile?.mkdirs()
        target.outputStream().use { output -> input.copyTo(output) }
    }
}

actual fun loadImageBitmap(resourceName: String): ImageBitmap {
    return loadResourceStream(resourceName).use { stream ->
        org.jetbrains.skia.Image.makeFromEncoded(stream.readBytes()).toComposeImageBitmap()
    }
}

actual fun checkLauncherInstalled(): Boolean = true // Desktop always has its own launcher

actual fun openGameLauncher() {
    // No-op on desktop — game is launched directly via ProcessBuilder
}

actual fun openFolder(path: String) {
    val dir = java.io.File(path)
    if (dir.exists()) {
        if (java.awt.Desktop.isDesktopSupported()) {
            java.awt.Desktop.getDesktop().open(dir)
        } else {
            ProcessBuilder("explorer", dir.absolutePath).start()
        }
    }
}

actual fun decodeImageBitmap(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap {
    return org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
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
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
        setRGB(0, 0, width, height, safePixels, 0, width)
    }
    // Avoid PNG encode/decode roundtrip (very expensive during frequent map updates).
    return image.toComposeImageBitmap()
}

actual fun getPlatformTotalPhysicalMemoryMb(): Int {
    val osBean = runCatching {
        java.lang.management.ManagementFactory.getOperatingSystemMXBean()
    }.getOrNull()
    val totalBytes = (osBean as? com.sun.management.OperatingSystemMXBean)
        ?.totalPhysicalMemorySize
        ?: return 0
    return (totalBytes / (1024L * 1024L)).toInt()
}

actual fun validatePlatformJavaPath(rawPath: String, expectedMajor: Int): Result<Unit> {
    fun isWindows(): Boolean =
        System.getProperty("os.name").contains("windows", ignoreCase = true)

    fun resolveJavaExecutable(raw: String): File? {
        val input = File(raw.trim())
        if (input.isDirectory) {
            val exe = input.resolve("bin").resolve(if (isWindows()) "java.exe" else "java")
            return exe.takeIf { it.exists() }
        }
        if (input.exists()) {
            val name = input.name.lowercase()
            return if (isWindows()) input.takeIf { name == "java.exe" }
            else input.takeIf { name == "java" }
        }
        val exe = File(raw.trim() + if (isWindows()) ".exe" else "")
        return exe.takeIf { it.exists() }
    }

    fun readJavaMajorVersion(javaExe: File): Int? = runCatching {
        val process = ProcessBuilder(javaExe.absolutePath, "-version")
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        val match = Regex("""version "([0-9]+)(?:\.([0-9]+))?.*""").find(output)
            ?: return@runCatching null
        val major = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@runCatching null
        if (major == 1) match.groupValues.getOrNull(2)?.toIntOrNull() else major
    }.getOrNull()

    val resolved = resolveJavaExecutable(rawPath) ?: return Result.failure(
        IllegalStateException("Java 路径无效: $rawPath")
    )
    val version = readJavaMajorVersion(resolved) ?: return Result.failure(
        IllegalStateException("无法识别 Java 版本: ${resolved.absolutePath}")
    )
    if (version != expectedMajor) {
        return Result.failure(
            IllegalStateException("Java 版本应为 $expectedMajor，当前为 $version")
        )
    }
    return Result.success(Unit)
}

actual fun currentPlatformJavaMajor(): Int? = Runtime.version().feature()

actual fun androidx.navigation.NavGraphBuilder.addDesktopOnlyRoutes(
    navController: androidx.navigation.NavHostController
) {
    composable<McPlayView> {
        val launchArgs = androidx.compose.runtime.remember {
            if (McPlayStore.openConsoleOnly) {
                McPlayStore.openConsoleOnly = false
                null
            } else {
                McPlayStore.pendingLaunch.also { McPlayStore.pendingLaunch = null }
            }
        }
        calebxzhou.rdi.client.ui.screen.McPlayScreen(
            launchArgs = launchArgs,
            autoStart = launchArgs != null,
            onBack = {
                val callback = McPlayStore.onBack
                McPlayStore.onBack = null
                if (callback != null) {
                    callback()
                } else {
                    navController.navigate(HostRoute(HostTab.MyHosts.name))
                }
            }
        )
    }
    composable<ModpackUpload> {
        calebxzhou.rdi.client.ui.screen.ModpackUploadScreen2(
            onBack = {
                navController.navigate(ResourceRoute(ResourceTab.All.name)) {
                    popUpTo<ModpackUpload> { inclusive = true }
                    launchSingleTop = true
                    restoreState = false
                }
            },
            onUploadSubmitted = { runId ->
                navController.navigate(TaskList(runId)) {
                    popUpTo<ModpackUpload> { inclusive = true }
                    launchSingleTop = true
                    restoreState = false
                }
            }
        )
    }
}

actual fun getHwSpecJson(): String {
    return getCachedOrFetchHwSpecJson()
}
