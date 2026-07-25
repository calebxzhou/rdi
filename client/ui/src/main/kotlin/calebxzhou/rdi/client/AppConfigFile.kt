package calebxzhou.rdi.client

import calebxzhou.rdi.RDIClient
import java.io.File

internal fun appConfigFile(): File =
    RDIClient.DIR.resolve("config.toml")
