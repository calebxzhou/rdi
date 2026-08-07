package calebxzau.rdi.client.service

private val reservedJvmArguments = listOf(
    "-Xmx",
    "-agentlib:jdwp",
    "-Drdi.play=",
)

fun parseModpackJvmParams(raw: String): Result<List<String>> = runCatching {
    val arguments = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()
    arguments.forEach { argument ->
        require(argument.startsWith("-")) { "自定义JVM参数必须以-开头：$argument" }
        require(reservedJvmArguments.none { argument.startsWith(it) }) {
            "该JVM参数由RDI设置管理：$argument"
        }
        require(argument != "-cp" && argument != "-classpath" && argument != "-jar") {
            "自定义JVM参数不能修改游戏启动结构：$argument"
        }
    }
    arguments
}

fun normalizeModpackJvmParams(raw: String): Result<String> =
    parseModpackJvmParams(raw).map { it.joinToString("\n") }
