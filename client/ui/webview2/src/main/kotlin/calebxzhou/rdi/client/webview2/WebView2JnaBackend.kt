package calebxzhou.rdi.client.webview2

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Kernel32Util
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.win32.StdCallLibrary
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyBoundsAdapter
import java.awt.event.HierarchyListener
import java.io.File
import java.util.Locale
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.roundToInt

private val WEBVIEW2_DEBUG_ENABLED: Boolean = run {
    val value = System.getenv("RDI_WEBVIEW2_DEBUG")
        ?: System.getProperty("rdi.webview2.debug")
        ?: return@run false
    value.equals("1", ignoreCase = true) ||
        value.equals("true", ignoreCase = true) ||
        value.equals("yes", ignoreCase = true) ||
        value.equals("on", ignoreCase = true)
}

private inline fun webView2DebugLog(message: () -> String) {
    if (WEBVIEW2_DEBUG_ENABLED) {
        println(message())
    }
}

private data class ScreenBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

internal object DesktopWebView2Platform {
    fun createBackend(): DesktopEmbeddedWebViewBackend {
        return if (WebView2Platform.isWindowsX64()) {
            DesktopJnaWebView2Backend()
        } else {
            DesktopPlaceholderWebViewBackend("WebView2仅支持Windows x64。")
        }
    }
}

internal class DesktopJnaWebView2Backend : DesktopEmbeddedWebViewBackend {
    private var host: DesktopWebViewHostPanel? = null
    private var requestedUrl: String? = null
    private var lastNavigatedUrl: String? = null
    private var pendingNavigatedUrl: String? = null
    private var bridgeLibrary: WebView2BridgeLibrary? = null
    private var bridgeHandle: Pointer? = null
    private var hostComponentListener: ComponentAdapter? = null
    private var hostHierarchyListener: HierarchyListener? = null
    private var hostHierarchyBoundsListener: HierarchyBoundsAdapter? = null
    private var statePollTimer: Timer? = null

    override fun attach(host: DesktopWebViewHostPanel) {
        runOnEdt {
            if (this.host === host) {
                requestedUrl?.let(::ensureWebView)
                return@runOnEdt
            }
            detachInternal()
            this.host = host
            installHostListeners(host)
            requestedUrl?.let(::ensureWebView)
            SwingUtilities.invokeLater {
                if (this.host === host) {
                    syncControllerPlacement()
                    requestedUrl?.let(::ensureWebView)
                }
            }
        }
    }

    override fun loadUrl(url: String) {
        requestedUrl = url
        runOnEdt {
            ensureWebView(url)
        }
    }

    override fun detach() {
        runOnEdt {
            detachInternal()
        }
    }

    private fun ensureWebView(url: String) {
        val currentHost = host ?: return
        val nativeHost = currentHost.nativeHost()
        if (!nativeHost.isDisplayable) {
            currentHost.showStatus("正在等待WebView宿主窗口(HWND)创建...")
            return
        }
        val hwnd = WebView2Win32Host.tryGetWindowHandle(nativeHost)
        if (hwnd == null) {
            currentHost.showStatus("未能获取WebView宿主窗口句柄(HWND)，稍后会自动重试。")
            return
        }
        val runtime = WebView2RuntimeLocator.detect()
        if (!runtime.installed) {
            currentHost.showStatus(
                buildString {
                    appendLine("未检测到Edge WebView2 Runtime。")
                    runtime.details?.let(::append)
                }
            )
            return
        }
        val bridge = runCatching { bridgeLibrary ?: WebView2BridgeLocator.load().also { bridgeLibrary = it } }
            .getOrElse {
                currentHost.showStatus(
                    buildString {
                        appendLine("未找到内置WebView2桥(native bridge)DLL资源。")
                        appendLine()
                        append("错误: ${it.message ?: it::class.simpleName}")
                    }
                )
                return
            }
        val handle = bridgeHandle ?: bridge.rdi_webview2_create()?.also {
            bridgeHandle = it
        }
        if (handle == null || Pointer.nativeValue(handle) == 0L) {
            currentHost.showStatus("创建WebView2桥(native bridge)实例失败。")
            return
        }
        val loaderPath = runCatching { WebView2LoaderLocator.resolveLoaderPath() }
            .getOrElse {
                currentHost.showStatus(
                    buildString {
                        appendLine("未找到WebView2Loader.dll。")
                        appendLine()
                        append(it.message ?: it::class.simpleName)
                    }
                )
                return
            }
        val userDataDir = WebView2RuntimeLocator.userDataDir().apply { mkdirs() }

        webView2DebugLog {
            "[WebView2Debug] handle=${Pointer.nativeValue(handle)} hwnd=${hwnd.pointer} loaderPath=${loaderPath.absolutePath} userDataDir=${userDataDir.absolutePath}"
        }
        startPollingState()
        val attachHr = WinNT.HRESULT(
            bridge.rdi_webview2_attach(
                handle,
                hwnd.pointer,
                WString(loaderPath.absolutePath),
                WString(userDataDir.absolutePath)
            )
        )
        if (COMUtils.FAILED(attachHr)) {
            currentHost.showStatus(
                buildString {
                    append("请求创建WebView2环境失败: ")
                    append(formatHRESULT(attachHr))
                    appendBridgeError(bridge, handle)
                }
            )
            return
        }
        updateControllerBounds()
        setVisible(nativeHost.isShowing)
        navigateIfNeeded(url)
    }

    private fun navigateIfNeeded(targetUrl: String? = requestedUrl) {
        val currentHost = host ?: return
        val bridge = bridgeLibrary ?: return
        val handle = bridgeHandle ?: return
        if (targetUrl.isNullOrBlank()) return
        val state = bridge.rdi_webview2_get_state(handle)
        if (state != WebView2BridgeState.READY.value) {
            val hr = WinNT.HRESULT(bridge.rdi_webview2_navigate(handle, WString(targetUrl)))
            if (COMUtils.SUCCEEDED(hr)) {
                pendingNavigatedUrl = targetUrl
            }
            if (state == WebView2BridgeState.FAILED.value) {
                currentHost.showStatus(
                    bridgeErrorMessage(
                        prefix = "WebView2初始化失败。",
                        bridge = bridge,
                        handle = handle
                    )
                )
            } else {
                currentHost.showStatus("正在初始化WebView2环境...")
            }
            return
        }
        if (targetUrl == pendingNavigatedUrl) {
            lastNavigatedUrl = targetUrl
            pendingNavigatedUrl = null
            syncControllerPlacement()
            currentHost.clearStatus()
            return
        }
        if (targetUrl == lastNavigatedUrl) {
            syncControllerPlacement()
            currentHost.clearStatus()
            return
        }
        val hr = WinNT.HRESULT(bridge.rdi_webview2_navigate(handle, WString(targetUrl)))
        if (COMUtils.SUCCEEDED(hr)) {
            lastNavigatedUrl = targetUrl
            pendingNavigatedUrl = null
            syncControllerPlacement()
            currentHost.clearStatus()
        } else {
            currentHost.showStatus(
                bridgeErrorMessage(
                    prefix = "导航失败: ${formatHRESULT(hr)}",
                    bridge = bridge,
                    handle = handle
                )
            )
        }
    }

    private fun updateControllerBounds() {
        val currentHost = host ?: return
        val nativeHost = currentHost.nativeHost()
        val bridge = bridgeLibrary ?: return
        val handle = bridgeHandle ?: return
        val location = runCatching { nativeHost.locationOnScreen }.getOrNull() ?: return
        val transform = nativeHost.graphicsConfiguration?.defaultTransform
        val scaleX = transform?.scaleX ?: 1.0
        val scaleY = transform?.scaleY ?: 1.0
        val bounds = scaledScreenBounds(nativeHost, location.x, location.y, scaleX, scaleY)
        val hr = WinNT.HRESULT(
            bridge.rdi_webview2_set_bounds(
                handle,
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom
            )
        )
        if (COMUtils.FAILED(hr)) {
            currentHost.showStatus("同步WebView尺寸失败: ${formatHRESULT(hr)}")
        }
    }

    private fun setVisible(visible: Boolean) {
        if (host == null) return
        val bridge = bridgeLibrary ?: return
        val handle = bridgeHandle ?: return
        bridge.rdi_webview2_set_visible(handle, if (visible) 1 else 0)
    }

    private fun syncControllerPlacement() {
        val nativeHost = host?.nativeHost() ?: return
        updateControllerBounds()
        setVisible(nativeHost.isShowing)
    }

    private fun installHostListeners(host: DesktopWebViewHostPanel) {
        val nativeHost = host.nativeHost()
        hostComponentListener = object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                updateControllerBounds()
            }

            override fun componentMoved(e: ComponentEvent?) {
                updateControllerBounds()
            }

            override fun componentShown(e: ComponentEvent?) {
                updateControllerBounds()
                setVisible(true)
                requestedUrl?.let(::ensureWebView)
            }

            override fun componentHidden(e: ComponentEvent?) {
                setVisible(false)
            }
        }.also(nativeHost::addComponentListener)
        hostHierarchyListener = HierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong() != 0L && nativeHost.isDisplayable) {
                requestedUrl?.let(::ensureWebView)
            }
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) {
                setVisible(nativeHost.isShowing)
            }
        }.also(nativeHost::addHierarchyListener)
        hostHierarchyBoundsListener = object : HierarchyBoundsAdapter() {
            override fun ancestorMoved(e: HierarchyEvent?) {
                updateControllerBounds()
            }

            override fun ancestorResized(e: HierarchyEvent?) {
                updateControllerBounds()
            }
        }.also(nativeHost::addHierarchyBoundsListener)
    }

    private fun startPollingState() {
        stopPollingState()
        statePollTimer = Timer(120) {
            val currentHost = host ?: return@Timer
            val bridge = bridgeLibrary ?: return@Timer
            val handle = bridgeHandle ?: return@Timer
            val state = bridge.rdi_webview2_get_state(handle)
            webView2DebugLog { "[WebView2Debug] polledState=$state" }
            when (state) {
                WebView2BridgeState.IDLE.value,
                WebView2BridgeState.CREATING_ENVIRONMENT.value,
                WebView2BridgeState.CREATING_CONTROLLER.value -> {
                    currentHost.showStatus("正在初始化WebView2环境...")
                }

                WebView2BridgeState.READY.value -> {
                    syncControllerPlacement()
                    currentHost.clearStatus()
                    requestedUrl?.let(::navigateIfNeeded)
                    stopPollingState()
                }

                WebView2BridgeState.FAILED.value -> {
                    currentHost.showStatus(
                        bridgeErrorMessage(
                            prefix = "WebView2初始化失败。",
                            bridge = bridge,
                            handle = handle
                        )
                    )
                    stopPollingState()
                }
            }
        }.apply {
            isRepeats = true
            start()
        }
    }

    private fun detachInternal() {
        stopPollingState()
        lastNavigatedUrl = null
        pendingNavigatedUrl = null
        host?.nativeHost()?.let { nativeHost ->
            hostComponentListener?.let(nativeHost::removeComponentListener)
            hostHierarchyListener?.let(nativeHost::removeHierarchyListener)
            hostHierarchyBoundsListener?.let(nativeHost::removeHierarchyBoundsListener)
        }
        hostComponentListener = null
        hostHierarchyListener = null
        hostHierarchyBoundsListener = null
        bridgeHandle?.let { handle ->
            runCatching {
                bridgeLibrary?.rdi_webview2_destroy(handle)
            }
        }
        bridgeHandle = null
        host?.clearStatus()
        host = null
    }

    private fun bridgeErrorMessage(prefix: String, bridge: WebView2BridgeLibrary, handle: Pointer): String {
        return buildString {
            append(prefix)
            appendBridgeError(bridge, handle)
        }
    }

    private fun StringBuilder.appendBridgeError(bridge: WebView2BridgeLibrary, handle: Pointer) {
        val nativeErrPtr = bridge.rdi_webview2_get_last_error(handle)
        val nativeMessage = nativeErrPtr?.getWideString(0)
        if (!nativeMessage.isNullOrBlank()) {
            appendLine()
            appendLine()
            append(nativeMessage)
        } else {
            val hr = WinNT.HRESULT(bridge.rdi_webview2_get_last_hresult(handle))
            if (hr.toInt() != 0) {
                appendLine()
                appendLine()
                append("HRESULT: ${formatHRESULT(hr)}")
            }
        }
    }

    private fun stopPollingState() {
        statePollTimer?.stop()
        statePollTimer = null
    }

    private fun scaledScreenBounds(
        component: Component,
        xOnScreen: Int,
        yOnScreen: Int,
        scaleX: Double,
        scaleY: Double
    ): ScreenBounds {
        val width = maxOf(component.width, 1)
        val height = maxOf(component.height, 1)
        val left = (xOnScreen * scaleX).roundToInt()
        val top = (yOnScreen * scaleY).roundToInt()
        val right = ((xOnScreen + width) * scaleX).roundToInt()
        val bottom = ((yOnScreen + height) * scaleY).roundToInt()
        return ScreenBounds(left, top, right, bottom)
    }

    private fun runOnEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeLater(block)
        }
    }
}

private enum class WebView2BridgeState(val value: Int) {
    IDLE(0),
    CREATING_ENVIRONMENT(1),
    CREATING_CONTROLLER(2),
    READY(3),
    FAILED(4)
}

internal object WebView2Win32Host {
    fun tryGetWindowHandle(component: Component): WinDef.HWND? {
        return runCatching {
            if (!component.isDisplayable) return null
            val pointer = Native.getComponentPointer(component)
            if (pointer == null || Pointer.nativeValue(pointer) == 0L) null
            else WinDef.HWND(pointer)
        }.getOrNull()
    }
}

internal data class WebView2RuntimeStatus(
    val installed: Boolean,
    val details: String? = null
)

internal object WebView2RuntimeLocator {
    fun detect(): WebView2RuntimeStatus {
        if (!WebView2Platform.isWindowsX64()) {
            return WebView2RuntimeStatus(
                installed = false,
                details = "WebView2仅支持Windows x64。"
            )
        }
        val roots = listOfNotNull(
            System.getenv("ProgramFiles(x86)")?.let {
                File(it, "Microsoft\\EdgeWebView\\Application")
            },
            System.getenv("ProgramFiles")?.let {
                File(it, "Microsoft\\EdgeWebView\\Application")
            },
            System.getenv("LOCALAPPDATA")?.let {
                File(it, "Microsoft\\EdgeWebView\\Application")
            }
        )
        val runtimeDir = roots.firstOrNull { it.exists() && it.isDirectory }
        return if (runtimeDir != null) {
            val versionDir = runtimeDir.listFiles()
                ?.filter { it.isDirectory }
                ?.maxByOrNull { it.name }
            WebView2RuntimeStatus(
                installed = true,
                details = versionDir?.absolutePath ?: runtimeDir.absolutePath
            )
        } else {
            WebView2RuntimeStatus(
                installed = false,
                details = "未在常见目录找到Microsoft Edge WebView2 Runtime。"
            )
        }
    }

    fun userDataDir(): File {
        val baseDir = System.getenv("LOCALAPPDATA")
            ?.let(::File)
            ?: File(System.getProperty("user.home"), "AppData\\Local")
        return File(baseDir, "rdi5\\webview2")
    }
}

internal object WebView2Platform {
    fun isWindowsX64(
        osName: String? = System.getProperty("os.name"),
        osArch: String? = System.getProperty("os.arch")
    ): Boolean {
        val windows = osName?.lowercase(Locale.ROOT)?.contains("windows") == true
        val architecture = osArch?.lowercase(Locale.ROOT)
        return windows && architecture in setOf("amd64", "x86_64")
    }
}

private object WebView2BridgeLocator {
    fun load(): WebView2BridgeLibrary {
        val bridgeFile = resolveBridgePath()
        webView2DebugLog { "[WebView2Debug] bridgePath=${bridgeFile.absolutePath}" }
        return Native.load(
            bridgeFile.absolutePath,
            WebView2BridgeLibrary::class.java,
            mapOf(
                com.sun.jna.Library.OPTION_CALLING_CONVENTION to com.sun.jna.Function.ALT_CONVENTION
            )
        )
    }

    private fun resolveBridgePath(): File {
        return extractBundledBridge()
            ?: error("未在jar resource中找到rdi_webview2_bridge.dll")
    }

    private fun extractBundledBridge(): File? {
        val resourceNames = listOf(
            "webview2/win-x64/rdi_webview2_bridge.dll",
            "rdi_webview2_bridge.dll"
        )
        val stream = resourceNames.asSequence()
            .mapNotNull { resource ->
                DesktopWebView2Platform::class.java.classLoader.getResourceAsStream(resource)
                    ?.let { resource to it }
            }
            .firstOrNull()
            ?: return null
        val targetDir = webView2TempDir()
        val targetFile = File.createTempFile("rdi_webview2_bridge-", ".dll", targetDir).apply { deleteOnExit() }
        stream.second.use { input ->
            targetFile.outputStream().use(input::copyTo)
        }
        return targetFile
    }
}

private object WebView2LoaderLocator {
    fun resolveLoaderPath(): File {
        return extractBundledLoader()
            ?: error("未在jar resource中找到WebView2Loader.dll")
    }

    private fun extractBundledLoader(): File? {
        val resourceNames = listOf(
            "webview2/win-x64/WebView2Loader.dll",
            "WebView2Loader.dll"
        )
        val stream = resourceNames.asSequence()
            .mapNotNull { resource ->
                DesktopWebView2Platform::class.java.classLoader.getResourceAsStream(resource)
                    ?.let { resource to it }
            }
            .firstOrNull()
            ?: return null
        val targetDir = webView2TempDir()
        val targetFile = File.createTempFile("WebView2Loader-", ".dll", targetDir).apply { deleteOnExit() }
        stream.second.use { input ->
            targetFile.outputStream().use(input::copyTo)
        }
        return targetFile
    }
}

private fun webView2TempDir(): File =
    File(System.getProperty("java.io.tmpdir"), "rdi-webview2").apply { mkdirs() }

private interface WebView2BridgeLibrary : StdCallLibrary {
    fun rdi_webview2_create(): Pointer?
    fun rdi_webview2_destroy(handle: Pointer)
    fun rdi_webview2_attach(
        handle: Pointer,
        hwnd: Pointer,
        loaderPath: WString?,
        userDataDir: WString?
    ): Int
    fun rdi_webview2_set_bounds(
        handle: Pointer,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Int
    fun rdi_webview2_set_visible(handle: Pointer, visible: Int): Int
    fun rdi_webview2_navigate(handle: Pointer, url: WString?): Int
    fun rdi_webview2_get_state(handle: Pointer): Int
    fun rdi_webview2_get_last_error(handle: Pointer): Pointer?
    fun rdi_webview2_get_last_hresult(handle: Pointer): Int
}

private fun formatHRESULT(hr: WinNT.HRESULT): String {
    return runCatching {
        "${Kernel32Util.formatMessage(hr).trim()} (0x${hr.toInt().toUInt().toString(16)})"
    }.getOrElse {
        "0x${hr.toInt().toUInt().toString(16)}"
    }
}
