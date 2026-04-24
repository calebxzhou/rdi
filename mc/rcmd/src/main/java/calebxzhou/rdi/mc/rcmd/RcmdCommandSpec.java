package calebxzhou.rdi.mc.rcmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RcmdCommandSpec {
    private final List<String> path;
    private final List<RcmdArgument<?>> arguments;
    private final RcmdCommand command;
    private final String description;

    private RcmdCommandSpec(Builder builder) {
        this.path = Collections.unmodifiableList(new ArrayList<String>(builder.path));
        this.arguments = Collections.unmodifiableList(new ArrayList<RcmdArgument<?>>(builder.arguments));
        this.command = builder.command;
        this.description = builder.description;
    }

    public List<String> getPath() {
        return path;
    }

    public List<RcmdArgument<?>> getArguments() {
        return arguments;
    }

    public RcmdCommand getCommand() {
        return command;
    }

    public String getDescription() {
        return description;
    }

    public String getUsage() {
        StringBuilder usage = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                usage.append(' ');
            }
            usage.append(path.get(i));
        }
        for (RcmdArgument<?> argument : arguments) {
            usage.append(" <").append(argument.getName()).append(':').append(argument.getType().getName()).append('>');
        }
        return usage.toString();
    }

    public static Builder builder(String... path) {
        return new Builder(path);
    }

    public static final class Builder {
        private final List<String> path = new ArrayList<String>();
        private final List<RcmdArgument<?>> arguments = new ArrayList<RcmdArgument<?>>();
        private RcmdCommand command;
        private String description = "";

        private Builder(String... path) {
            if (path == null || path.length == 0) {
                throw new IllegalArgumentException("命令路径不能为空");
            }
            for (String part : path) {
                if (part == null || part.trim().isEmpty()) {
                    throw new IllegalArgumentException("命令路径不能包含空段");
                }
                this.path.add(part);
            }
        }

        public <T> Builder argument(String name, RcmdArgumentType<T> type) {
            arguments.add(new RcmdArgument<T>(name, type));
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
