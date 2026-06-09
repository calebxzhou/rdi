package calebxzhou.rdi.mc.rcmd

import calebxzhou.rdi.mc.rcmd.Rcmd.stripPrefix
import kotlin.math.min

class RcmdDispatcher {
    private val commands = mutableListOf<RcmdCommandSpec>()

    fun register(spec: RcmdCommandSpec) {
        commands += spec
    }

    fun getCommands(): List<RcmdCommandSpec> = commands.toList()

    fun execute(source: RcmdSource, rawInput: String): RcmdResult {
        val dispatchResult = dispatch(source, rawInput)
        if (dispatchResult.found) {
            return dispatchResult.result
        }
        return try {
            val tokens = RcmdParser.tokenize(stripPrefix(rawInput))
            if (tokens.isEmpty()) {
                RcmdResult.error("rcmd命令为空")
            } else {
                RcmdResult.error("未知rcmd命令：${tokens.first()}")
            }
        } catch (e: RcmdParseException) {
            RcmdResult.error(e.message)
        }
    }

    fun dispatch(source: RcmdSource, rawInput: String): RcmdDispatchResult {
        return try {
            val tokens = RcmdParser.tokenize(stripPrefix(rawInput))
            if (tokens.isEmpty()) {
                return RcmdDispatchResult.found(RcmdResult.error("rcmd命令为空"))
            }
            val match = findMatch(tokens) ?: return RcmdDispatchResult.notFound()
            RcmdDispatchResult.found(match.spec.command.execute(RcmdContext(source, rawInput, match.spec, tokens, match.arguments)))
        } catch (e: RcmdParseException) {
            RcmdDispatchResult.found(RcmdResult.error(e.message))
        }
    }

    @Throws(RcmdParseException::class)
    private fun findMatch(tokens: List<String>): MatchResult? {
        val candidates = resolveCandidates(tokens)
        if (candidates.isEmpty()) {
            return null
        }
        var bestParseException: RcmdParseException? = null
        for (spec in candidates) {
            try {
                return MatchResult(spec, parseArguments(spec, tokens, spec.path.size))
            } catch (e: RcmdParseException) {
                bestParseException = RcmdParseException("${e.message}。用法：${spec.usage()}")
            }
        }
        bestParseException?.let { throw it }
        return null
    }

    @Throws(RcmdParseException::class)
    private fun resolveCandidates(tokens: List<String>): List<RcmdCommandSpec> {
        val exactCandidates = mutableListOf<RcmdCommandSpec>()
        val prefixCandidates = mutableListOf<RcmdCommandSpec>()
        val incompleteCandidates = mutableListOf<RcmdCommandSpec>()

        for (spec in commands) {
            when (matchPath(tokens, spec.path)) {
                PathMatchStatus.EXACT -> exactCandidates += spec
                PathMatchStatus.PREFIX -> prefixCandidates += spec
                PathMatchStatus.INCOMPLETE -> incompleteCandidates += spec
                PathMatchStatus.NONE -> Unit
            }
        }

        if (exactCandidates.isNotEmpty()) {
            return exactCandidates
        }
        if (prefixCandidates.size == 1) {
            return prefixCandidates
        }
        if (prefixCandidates.size > 1) {
            throw RcmdParseException("rcmd命令前缀不明确：${commandPrefix(tokens, prefixCandidates)}，可匹配：${usages(prefixCandidates)}")
        }
        if (incompleteCandidates.size == 1) {
            throw RcmdParseException("rcmd命令不完整：${commandPrefix(tokens, incompleteCandidates)}。用法：${incompleteCandidates.first().usage()}")
        }
        if (incompleteCandidates.size > 1) {
            throw RcmdParseException("rcmd命令前缀不明确：${commandPrefix(tokens, incompleteCandidates)}，可匹配：${usages(incompleteCandidates)}")
        }
        return emptyList()
    }

    private fun matchPath(tokens: List<String>, path: List<String>): PathMatchStatus {
        val checkedParts = min(tokens.size, path.size)
        var exact = tokens.size >= path.size
        for (i in 0..<checkedParts) {
            val token = tokens[i].lowercase()
            val pathPart = path[i].lowercase()
            if (!pathPart.startsWith(token)) {
                return PathMatchStatus.NONE
            }
            if (pathPart != token) {
                exact = false
            }
        }
        return when {
            tokens.size < path.size -> PathMatchStatus.INCOMPLETE
            exact -> PathMatchStatus.EXACT
            else -> PathMatchStatus.PREFIX
        }
    }

    private fun commandPrefix(tokens: List<String>, candidates: List<RcmdCommandSpec>): String {
        val commandPartCount = candidates.minOfOrNull { it.path.size } ?: tokens.size
        return tokens.take(min(tokens.size, commandPartCount)).joinToString(" ")
    }

    private fun usages(specs: List<RcmdCommandSpec>) = specs.joinToString("、") { it.usage() }

    @Throws(RcmdParseException::class)
    private fun parseArguments(
        spec: RcmdCommandSpec,
        tokens: List<String>,
        tokenIndex: Int
    ): Map<String, Any> {
        val values = linkedMapOf<String, Any>()
        var currentIndex = tokenIndex
        for (argument in spec.arguments) {
            val result = argument.type.parse(tokens, currentIndex)
            values[argument.name] = result.value as Any
            currentIndex = result.nextTokenIndex
        }
        if (currentIndex != tokens.size) {
            throw RcmdParseException("参数过多，无法识别：${tokens[currentIndex]}")
        }
        return values
    }

    private data class MatchResult(val spec: RcmdCommandSpec, val arguments: Map<String, Any>)

    private enum class PathMatchStatus {
        NONE,
        INCOMPLETE,
        PREFIX,
        EXACT
    }
}
