package calebxzhou.rdi.client.webview2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebView2Test {
    @Test
    fun `normalizes web urls`() {
        assertEquals("https://example.com", normalizeWebView2Url("example.com"))
        assertEquals("https://example.com", normalizeWebView2Url("https://example.com"))
        assertEquals("http://example.com", normalizeWebView2Url("http://example.com"))
    }

    @Test
    fun `loads native backend only on windows x64`() {
        assertTrue(WebView2Platform.isWindowsX64("Windows 11", "amd64"))
        assertTrue(WebView2Platform.isWindowsX64("Windows 10", "x86_64"))
        assertFalse(WebView2Platform.isWindowsX64("Windows 11", "aarch64"))
        assertFalse(WebView2Platform.isWindowsX64("Linux", "amd64"))
    }

    @Test
    fun `bundles windows x64 native libraries`() {
        listOf(
            "webview2/win-x64/rdi_webview2_bridge.dll",
            "webview2/win-x64/WebView2Loader.dll"
        ).forEach { resource ->
            val stream = assertNotNull(WebView2Platform::class.java.classLoader.getResourceAsStream(resource))
            stream.use { assertTrue(it.read() >= 0, "$resource is empty") }
        }
    }
}
