package calebxzau.rdi.mcinstall

import java.nio.file.Path
import java.util.Locale

internal fun pathKey(path: Path): String =
    path.toAbsolutePath().normalize().toString().lowercase(Locale.ROOT)

internal fun isWindows(): Boolean = System.getProperty("os.name").contains("Windows", ignoreCase = true)
