package calebxzau.rdi.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import calebxzhou.mykotutils.std.canCreateSymlink
import calebxzhou.mykotutils.std.jarResource
import calebxzau.rdi.client.RDIClient
import calebxzhou.rdi.client.service.UpdateService
import calebxzhou.rdi.client.ui.comp.WebViewHost
import calebxzhou.rdi.client.ui.pickAwtDirectory
import calebxzhou.rdi.client.ui.pickAwtSaveFile
import com.sun.management.OperatingSystemMXBean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.io.File
import java.io.FilenameFilter
import java.io.InputStream
import java.lang.management.ManagementFactory
import java.net.URI

fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
}

fun openUrl(url: String) {
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(URI(url))
    }
}

fun openMsaVerificationUrl(url: String) {
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(URI(url))
    }
}

@Composable
fun WebView(
    url: String,
    title: String?,
    modifier: Modifier
) {
    WebViewHost(url = url, title = title, modifier = modifier)
}

suspend fun pickSaveFile(suggestedName: String, extension: String): File? =
    withContext(Dispatchers.IO) {
        pickAwtSaveFile(
            title = "选择保存位置",
            defaultFileName = suggestedName,
            defaultDirectory = File(System.getProperty("user.home")),
            requiredExtension = extension,
            filenameFilter = { _, name -> name.endsWith(".$extension", ignoreCase = true) }
        )
    }

suspend fun pickLocalMinecraftWorldDir(): String? =
    withContext(Dispatchers.IO) {
        pickAwtDirectory("选择本地存档目录")?.absolutePath
    }

suspend fun pickLocalModpackFile(): File? =
    withContext(Dispatchers.IO) {
        val owner = Frame()
        try {
            val dialog = FileDialog(owner, "选择客户端安装包", FileDialog.LOAD).apply {
                directory = File(System.getProperty("user.home"), "Downloads").absolutePath
                file = "*.zip;*.mrpack"
                filenameFilter = FilenameFilter { dir, name ->
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

suspend fun pickLocalZipFile(title: String): File? =
    withContext(Dispatchers.IO) {
        val owner = Frame()
        try {
            val dialog = FileDialog(owner, title, FileDialog.LOAD).apply {
                directory = File(System.getProperty("user.home"), "Downloads").absolutePath
                file = "*.zip"
                filenameFilter = FilenameFilter { dir, name ->
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

suspend fun pickLocalDirectory(title: String): File? =
    withContext(Dispatchers.IO) {
        pickAwtDirectory(title)
    }

suspend fun pickJavaExecutable(title: String): String? =
    withContext(Dispatchers.IO) {
        val owner = Frame()
        try {
            val dialog = FileDialog(owner, title, FileDialog.LOAD).apply {
                directory = File(System.getProperty("user.home")).absolutePath
                file = "java.exe"
                filenameFilter = FilenameFilter { dir, name ->
                    val target = File(dir, name)
                    if (target.isDirectory) return@FilenameFilter true
                    val lower = name.lowercase()
                    lower == "java" || lower == "java.exe" || lower == "javaw.exe"
                }
            }
            dialog.isVisible = true
            val dir = dialog.directory ?: return@withContext null
            val name = dialog.file ?: return@withContext null
            normalizeJavaExecutablePath(File(dir, name).absolutePath)
        } finally {
            owner.dispose()
        }
    }

fun checkCanCreateSymlink(): Boolean {
    return canCreateSymlink()
}

suspend fun runUpdateFlow(
    onStatus: (String) -> Unit,
    onDetail: (String) -> Unit,
    onRestart: suspend () -> Unit
) {
    UpdateService.startUpdateFlow(
        onStatus = onStatus,
        onDetail = onDetail
    )
}

fun loadResourceStream(name: String): InputStream {
    return RDIClient.jarResource(name)
}

fun exportResource(name: String, target: File) {
    loadResourceStream(name).use { input ->
        target.parentFile?.mkdirs()
        target.outputStream().use { output -> input.copyTo(output) }
    }
}

fun loadImageBitmap(resourceName: String): ImageBitmap {
    return loadResourceStream(resourceName).use { stream ->
        Image.makeFromEncoded(stream.readBytes()).toComposeImageBitmap()
    }
}

fun openFolder(path: String) {
    val dir = File(path)
    if (dir.exists()) {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(dir)
        } else {
            ProcessBuilder("explorer", dir.absolutePath).start()
        }
    }
}

fun decodeImageBitmap(bytes: ByteArray): ImageBitmap {
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
}

fun imageBitmapFromArgb(
    argb: IntArray,
    width: Int,
    height: Int
): ImageBitmap {
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

fun readTotalPhysicalMemoryMb(): Int {
    val osBean = runCatching {
        ManagementFactory.getOperatingSystemMXBean()
    }.getOrNull()
    val totalBytes = (osBean as? OperatingSystemMXBean)
        ?.totalPhysicalMemorySize
        ?: return 0
    return (totalBytes / (1024L * 1024L)).toInt()
}

private fun resolveJavaExecutable(raw: String): File? {
    val input = File(raw.trim())
    if (input.isDirectory) {
        val exe = input.resolve("bin").resolve("java.exe")
        return exe.takeIf { it.exists() }
    }
    if (input.exists()) {
        val name = input.name.lowercase()
        if (name == "java.exe") return input
        if (name == "javaw.exe") {
            return input.parentFile?.resolve("java.exe")?.takeIf { it.exists() } ?: input
        }
        return null
    }
    val exe = File(raw.trim() + ".exe")
    return exe.takeIf { it.exists() }
}

fun normalizeJavaExecutablePath(rawPath: String): String? =
    resolveJavaExecutable(rawPath)?.absolutePath

fun validateJavaExecutablePath(rawPath: String, expectedMajor: Int): Result<Unit> {
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
        IllegalStateException("Java路径无效: $rawPath")
    )
    val version = readJavaMajorVersion(resolved) ?: return Result.failure(
        IllegalStateException("无法识别Java版本: ${resolved.absolutePath}")
    )
    if (version != expectedMajor) {
        return Result.failure(
            IllegalStateException("Java版本应为$expectedMajor，当前为$version")
        )
    }
    return Result.success(Unit)
}

fun currentJavaMajor(): Int = Runtime.version().feature()

