package calebxzhou.ktutils.std

import java.io.InputStream

inline fun <reified T> ok(obj: T): Result<T> {
    return Result.success(obj)
}
fun ok(): Result<Unit> {
    return Result.success(Unit)
}
fun Any.jarResource(path: String): InputStream {
    val cl = Thread.currentThread().contextClassLoader
        ?: this::class.java.classLoader
        ?: ClassLoader.getSystemClassLoader()
    return cl?.getResourceAsStream(path)
        ?: throw IllegalArgumentException("Resource not found: $path")
}
fun String.spaceSplit(): List<String> {
    val text = trim()
    val spaceRegex = Regex("\\s+")
    return if (text.contains(spaceRegex)) {
        text.split(spaceRegex)
    } else {
        listOf(text)
    }
}
