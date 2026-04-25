package calebxzhou.rdi.mc.rcmd;

import java.util.ArrayList;
import java.util.List;

public record RcmdCommandSpec(
        List<String> path,
        List<RcmdArgument<?>> arguments,
        RcmdCommand command,
        String description
) {
    private RcmdCommandSpec(Builder builder) {
        this(builder.path, builder.arguments, builder.command, builder.description);
    }

    public RcmdCommandSpec {
        if (path == null) {
            throw new IllegalArgumentException("命令路径不能为空");
        }
        if (arguments == null) {
            throw new IllegalArgumentException("命令参数列表不能为空");
        }
        for (var part : path) {
            if (part == null || part.isBlank()) {
                throw new IllegalArgumentException("命令路径不能包含空段");
            }
        }
        path = List.copyOf(path);
        arguments = List.copyOf(arguments);
        description = description == null ? "" : description;
        if (path.isEmpty()) {
            throw new IllegalArgumentException("命令路径不能为空");
        }
        if (command == null) {
            throw new IllegalArgumentException("命令处理器不能为空");
        }
    }

    public String usage() {
        var usage = new StringBuilder(String.join(" ", path));
        for (var argument : arguments) {
            usage.append(" <").append(argument.name()).append(':').append(argument.type().name()).append('>');
        }
        return usage.toString();
    }

    public static Builder builder(String... path) {
        return new Builder(path);
    }

    public static final class Builder {
        private final List<String> path = new ArrayList<>();
        private final List<RcmdArgument<?>> arguments = new ArrayList<>();
        private RcmdCommand command;
        private String description = "";

        private Builder(String... path) {
            if (path == null || path.length == 0) {
                throw new IllegalArgumentException("命令路径不能为空");
            }
            for (var part : path) {
                if (part == null || part.isBlank()) {
                    throw new IllegalArgumentException("命令路径不能包含空段");
                }
                this.path.add(part);
            }
        }

        public <T> Builder argument(String name, RcmdArgumentType<T> type) {
            arguments.add(new RcmdArgument<>(name, type));
            return this;
        }

        public Builder description(String description) {
            this.description = description == null ? "" : description;
            return this;
        }

        public Builder command(RcmdCommand command) {
            this.command = command;
            return this;
        }

        public RcmdCommandSpec build() {
            if (command == null) {
                throw new IllegalArgumentException("命令处理器不能为空");
            }
            return new RcmdCommandSpec(this);
        }
    }
}
