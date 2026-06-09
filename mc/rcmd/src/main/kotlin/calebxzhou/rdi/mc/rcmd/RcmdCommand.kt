package calebxzhou.rdi.mc.rcmd

fun interface RcmdCommand {
    fun execute(context: RcmdContext): RcmdResult
}

class RcmdCommandSpec private constructor(
    path: List<String>,
    arguments: List<RcmdArgument<*>>,
    val command: RcmdCommand,
    description: String?
) {
    val path: List<String> = path.toList()
    val arguments: List<RcmdArgument<*>> = arguments.toList()
    val description: String = description.orEmpty()

    init {
        require(this.path.isNotEmpty()) { "命令路径不能为空" }
        require(this.path.all { it.isNotBlank() }) { "命令路径不能包含空段" }
    }

    fun usage(): String = buildString {
        append(path.joinToString(" "))
        for (argument in arguments) {
            append(" <")
                .append(argument.name)
                .append(':')
                .append(argument.type.name())
                .append('>')
        }
    }

    class Builder(vararg path: String) {
        private val path = path.toMutableList()
        private val arguments = mutableListOf<RcmdArgument<*>>()
        private var command: RcmdCommand? = null
        private var description = ""

        init {
            require(this.path.isNotEmpty()) { "命令路径不能为空" }
            require(this.path.all { it.isNotBlank() }) { "命令路径不能包含空段" }
        }

        fun <T> argument(name: String, type: RcmdArgumentType<T>) = apply {
            arguments += RcmdArgument(name, type)
        }

        fun description(description: String?) = apply {
            this.description = description.orEmpty()
        }

        fun command(command: RcmdCommand) = apply {
            this.command = command
        }

        fun build() = RcmdCommandSpec(
            path = path,
            arguments = arguments,
            command = requireNotNull(command) { "命令处理器不能为空" },
            description = description
        )
    }

    companion object {
        @JvmStatic
        fun builder(vararg path: String) = RcmdCommandSpec.Builder(*path)
    }
}
