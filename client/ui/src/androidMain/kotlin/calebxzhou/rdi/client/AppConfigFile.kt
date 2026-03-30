package calebxzhou.rdi.client

import calebxzhou.rdi.client.ui.AndroidPlatform
import java.io.File

internal actual fun platformAppConfigFile(): File =
    AndroidPlatform.appContext.filesDir.resolve("config.toml")
