package calebxzhou.rdi.common.ai

import calebxzhou.rdi.common.DIR
import calebxzhou.rdi.common.util.javaExePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile

private const val MAX_JAVA_TOOL_OUTPUT_CHARS = 32 * 1024
private const val JAVA_TOOL_TIMEOUT_MILLIS = 15_000L

object JarClassSearchAiTool : AiTool {
    override val name = "jar_class_search"

    override fun definition(context: AiToolContext) = AiToolDefinition(
        name = name,
        description = "List and search class names inside a local jar under the RDI directory before using javap.",
        parameters = AiToolParameters(
            properties = mapOf(
                "jarPath" to AiToolProperty(type = "string"),
                "query" to AiToolProperty(type = "string"),
                "limit" to AiToolProperty(type = "integer")
            ),
            required = listOf("jarPath")
        )
    )

    override suspend fun execute(argumentsJson: String, context: AiToolContext): AiToolExecution {
        val args = decodeArgs<JarClassSearchToolArgs>(argumentsJson) ?: return argError(name)
        val jar = resolveAllowedJar(args.jarPath).getOrElse {
            return AiToolExecution(result = AiToolResult(tool = name, target = args.jarPath, error = it.message ?: "Jar不在允许分析范围内"))
        }
        val access = AiToolAccess(jar.name, "分析")
        val result = runCatching {
            val query = args.query?.trim()?.takeIf(String::isNotBlank)
            val limit = args.limit.coerceIn(1, 500)
            val classes = withContext(Dispatchers.IO) {
                JarFile(jar).use { jarFile ->
                    jarFile.entries().asSequence()
                        .map { it.name }
                        .filter { it.endsWith(".class") && !it.endsWith("module-info.class") }
                        .map { it.removeSuffix(".class").replace('/', '.') }
                        .filter { query == null || it.contains(query, ignoreCase = true) }
                        .take(limit)
                        .toList()
                }
            }
            AiToolResult(
                tool = name,
                target = jar.absolutePath,
                output = classes.joinToString("\n"),
                resultCount = classes.size,
                truncated = classes.size == limit
            )
        }.getOrElse {
            AiToolResult(tool = name, target = jar.absolutePath, error = it.message ?: "Jar类搜索失败")
        }
        return AiToolExecution(access, result)
    }
}

object JavapClassAiTool : AiTool {
    override val name = "javap_class"

    override fun definition(context: AiToolContext) = AiToolDefinition(
        name = name,
        description = "Run javap from the current Java bin directory against a class in a jar under the RDI directory to inspect methods, fields, constants, and bytecode.",
        parameters = AiToolParameters(
            properties = mapOf(
                "jarPath" to AiToolProperty(type = "string"),
                "className" to AiToolProperty(type = "string"),
                "visibility" to AiToolProperty(type = "string", enum = listOf("public", "private")),
                "bytecode" to AiToolProperty(type = "boolean"),
                "constants" to AiToolProperty(type = "boolean")
            ),
            required = listOf("jarPath", "className")
        )
    )

    override suspend fun execute(argumentsJson: String, context: AiToolContext): AiToolExecution {
        val args = decodeArgs<JavapClassToolArgs>(argumentsJson) ?: return argError(name)
        val jar = resolveAllowedJar(args.jarPath).getOrElse {
            return AiToolExecution(result = AiToolResult(tool = name, target = args.jarPath, error = it.message ?: "Jar不在允许分析范围内"))
        }
        val className = args.className.trim()
        if (!className.matches(Regex("""[A-Za-z0-9_.$]+"""))) {
            return AiToolExecution(result = AiToolResult(tool = name, target = jar.absolutePath, error = "类名格式不合法: $className"))
        }
        val javap = resolveJavaTool("javap").getOrElse {
            return AiToolExecution(result = AiToolResult(tool = name, target = jar.absolutePath, error = it.message ?: "找不到javap"))
        }
        val command = buildList {
            add(javap.absolutePath)
            add(if (args.visibility == "private") "-private" else "-public")
            if (args.bytecode) add("-c")
            if (args.constants) add("-constants")
            add("-classpath")
            add(jar.absolutePath)
            add(className)
        }
        return executeJavaCommandTool(name, "${jar.name}::$className", jar.absolutePath, command)
    }
}

object JdepsJarAiTool : AiTool {
    override val name = "jdeps_jar"

    override fun definition(context: AiToolContext) = AiToolDefinition(
        name = name,
        description = "Run jdeps from the current Java bin directory against a jar under the RDI directory to inspect Java package and class dependencies.",
        parameters = AiToolParameters(
            properties = mapOf(
                "jarPath" to AiToolProperty(type = "string"),
                "verbose" to AiToolProperty(type = "boolean"),
                "packageFilter" to AiToolProperty(type = "string")
            ),
            required = listOf("jarPath")
        )
    )

    override suspend fun execute(argumentsJson: String, context: AiToolContext): AiToolExecution {
        val args = decodeArgs<JdepsJarToolArgs>(argumentsJson) ?: return argError(name)
        val jar = resolveAllowedJar(args.jarPath).getOrElse {
            return AiToolExecution(result = AiToolResult(tool = name, target = args.jarPath, error = it.message ?: "Jar不在允许分析范围内"))
        }
        val jdeps = resolveJavaTool("jdeps").getOrElse {
            return AiToolExecution(result = AiToolResult(tool = name, target = jar.absolutePath, error = it.message ?: "找不到jdeps"))
        }
        val packageFilter = args.packageFilter?.trim()?.takeIf(String::isNotBlank)
        if (packageFilter != null && !packageFilter.matches(Regex("""[A-Za-z0-9_.$*]+"""))) {
            return AiToolExecution(result = AiToolResult(tool = name, target = jar.absolutePath, error = "包名过滤格式不合法: $packageFilter"))
        }
        val command = buildList {
            add(jdeps.absolutePath)
            add("-q")
            if (args.verbose) add("-verbose:class")
            packageFilter?.let {
                add("-p")
                add(it)
            }
            add(jar.absolutePath)
        }
        return executeJavaCommandTool(name, jar.name, jar.absolutePath, command)
    }
}

private fun resolveAllowedJar(rawPath: String): Result<File> = runCatching {
    val jar = File(rawPath.trim()).canonicalFile
    require(jar.isFile) { "Jar不存在: $rawPath" }
    require(jar.extension.equals("jar", ignoreCase = true)) { "只能分析.jar文件: $rawPath" }
    val allowedRoots = listOf(DIR).map { it.canonicalFile }
    require(allowedRoots.any { root -> jar.toPath().startsWith(root.toPath()) }) {
        "Jar不在RDI目录内: $rawPath"
    }
    jar
}

private fun resolveJavaTool(name: String): Result<File> = runCatching {
    val javaExe = File(javaExePath).canonicalFile
    val binDir = javaExe.parentFile ?: throw IllegalStateException("无法定位Java bin目录: $javaExe")
    val exeName = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) "$name.exe" else name
    val tool = binDir.resolve(exeName).canonicalFile
    require(tool.isFile) { "当前Java缺少$name，请使用完整JDK运行启动器: $tool" }
    tool
}

private suspend fun executeJavaCommandTool(
    toolName: String,
    accessTarget: String,
    resultTarget: String,
    command: List<String>
): AiToolExecution {
    val access = AiToolAccess(accessTarget, "分析")
    val result = runCatching {
        val output = withContext(Dispatchers.IO) {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val outputFuture = CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            if (!process.waitFor(JAVA_TOOL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                throw IllegalStateException("Java工具执行超时")
            }
            process.exitValue() to outputFuture.get(2, TimeUnit.SECONDS)
        }
        val text = output.second
        AiToolResult(
            tool = toolName,
            target = resultTarget,
            command = command.joinToString(" "),
            exitCode = output.first,
            output = text.take(MAX_JAVA_TOOL_OUTPUT_CHARS),
            truncated = text.length > MAX_JAVA_TOOL_OUTPUT_CHARS
        )
    }.getOrElse {
        AiToolResult(
            tool = toolName,
            target = resultTarget,
            command = command.joinToString(" "),
            error = it.message ?: "Java工具执行失败"
        )
    }
    return AiToolExecution(access, result)
}

@Serializable
private data class JarClassSearchToolArgs(
    val jarPath: String,
    val query: String? = null,
    val limit: Int = 200
)

@Serializable
private data class JavapClassToolArgs(
    val jarPath: String,
    val className: String,
    val visibility: String = "public",
    val bytecode: Boolean = true,
    val constants: Boolean = false
)

@Serializable
private data class JdepsJarToolArgs(
    val jarPath: String,
    val verbose: Boolean = false,
    val packageFilter: String? = null
)
