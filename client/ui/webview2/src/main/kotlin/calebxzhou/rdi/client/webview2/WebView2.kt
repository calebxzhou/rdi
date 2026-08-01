package calebxzhou.rdi.client.webview2

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Color
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

internal interface DesktopEmbeddedWebViewBackend {
    fun attach(host: DesktopWebViewHostPanel)
    fun loadUrl(url: String)
    fun detach()
}

internal class DesktopPlaceholderWebViewBackend(
    private val reason: String? = null
) : DesktopEmbeddedWebViewBackend {
    private var host: DesktopWebViewHostPanel? = null

    override fun attach(host: DesktopWebViewHostPanel) {
        this.host = host
    }

    override fun loadUrl(url: String) {
        host?.showStatus(
            buildString {
                appendLine(reason ?: "当前desktop内嵌网页暂不可用。")
                appendLine()
                appendLine("可先使用系统浏览器打开该页面。")
                appendLine()
                append("目标地址: $url")
            }
        )
    }

    override fun detach() {
        host = null
    }
}

internal class DesktopWebViewHostPanel : JPanel(BorderLayout()) {
    private val statusBody = JTextArea().apply {
        isEditable = false
        lineWrap = false
        wrapStyleWord = false
        border = null
        background = Color(0xF7, 0xFA, 0xFC)
    }
    private val statusPane = JScrollPane(statusBody).apply {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color(0xD7, 0xDC, 0xE3)),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)
        )
        preferredSize = Dimension(1, 30)
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
        isVisible = true
        background = Color(0xF7, 0xFA, 0xFC)
        viewport.background = Color(0xF7, 0xFA, 0xFC)
    }
    private val nativeHost = Canvas().apply {
        background = Color.WHITE
        minimumSize = Dimension(1, 1)
    }
    private var pageTitle: String? = null
    private var pageUrl: String = ""
    private var explicitStatus: String = ""

    init {
        background = Color.WHITE
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0xD7, 0xDC, 0xE3), 1, true),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)
        )
        add(nativeHost, BorderLayout.CENTER)
        add(statusPane, BorderLayout.SOUTH)
    }

    fun nativeHost() = nativeHost

    fun setPageInfo(title: String?, url: String) {
        pageTitle = title?.takeIf { it.isNotBlank() }
        pageUrl = url
        refreshStatus()
    }

    fun showStatus(message: String) {
        explicitStatus = message.trim()
        refreshStatus()
    }

    private fun refreshStatus() {
        val fallback = listOfNotNull(pageTitle, pageUrl.takeIf { it.isNotBlank() }).joinToString("  ")
        val text = explicitStatus.takeIf { it.isNotBlank() } ?: fallback
        statusBody.text = text
        statusPane.isVisible = text.isNotBlank()
        revalidate()
        repaint()
    }

    fun clearStatus() {
        showStatus("")
    }
}

private fun createDesktopEmbeddedWebViewBackend(): DesktopEmbeddedWebViewBackend {
    return DesktopWebView2Platform.createBackend()
}

internal fun normalizeWebView2Url(url: String): String = when {
    url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) -> url
    else -> "https://$url"
}

@Composable
fun WebView2(
    url: String,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    val normalizedUrl = remember(url) { normalizeWebView2Url(url) }
    val backend = remember { createDesktopEmbeddedWebViewBackend() }
    DisposableEffect(backend) {
        onDispose {
            backend.detach()
        }
    }
    SwingPanel(
        modifier = modifier.fillMaxSize(),
        factory = {
            DesktopWebViewHostPanel().also {
                backend.attach(it)
                it.setPageInfo(title, normalizedUrl)
                backend.loadUrl(normalizedUrl)
            }
        },
        update = { panel ->
            backend.attach(panel)
            panel.setPageInfo(title, normalizedUrl)
            backend.loadUrl(normalizedUrl)
        }
    )
}
