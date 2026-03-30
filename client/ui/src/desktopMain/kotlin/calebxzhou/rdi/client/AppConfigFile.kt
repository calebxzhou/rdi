package calebxzhou.rdi.client

import calebxzhou.rdi.RDIClient
import java.io.File

internal actual fun platformAppConfigFile(): File =
    RDIClient.DIR.resolve("config.toml")
