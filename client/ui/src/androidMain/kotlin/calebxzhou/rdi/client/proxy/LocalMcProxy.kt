package calebxzhou.rdi.client.proxy

import android.util.Log

actual object LocalMcProxy {
    private const val TAG = "LocalMcProxy"

    @Volatile
    private var logSink: (String) -> Unit = {}

    actual val gameAddr: String
        get() = currentEndpointFromCarrier().let { "${it.host}:${it.port}" }

    actual fun start(onLog: (String) -> Unit) {
        logSink = onLog
        reportLog("android local proxy disabled, using direct endpoint")
    }

    actual fun stop() = Unit

    actual internal fun currentEndpointFromCarrier(): ProxyEndpoint =
        LocalMcProxyCommon.currentEndpointFromCarrier()

    actual internal fun reportLog(message: String) {
        Log.d(TAG, message)
        runCatching {
            logSink("[LocalMcProxy] $message")
        }
    }
}
