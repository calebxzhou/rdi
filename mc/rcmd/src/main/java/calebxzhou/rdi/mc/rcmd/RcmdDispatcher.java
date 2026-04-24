package calebxzhou.rdi.mc.rcmd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RcmdDispatcher {
    private final List<RcmdCommandSpec> commands = new ArrayList<RcmdCommandSpec>();

    public void register(RcmdCommandSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("命令定义不能为空");
        }
        commands.add(spec);
    }

    public List<RcmdCommandSpec> getCommands() {
        return new ArrayList<RcmdCommandSpec>(commands);
    }

    public RcmdResult execute(RcmdSource source, String rawInput) {
        if (source == null) {
            throw new IllegalArgumentException("命令来源不能为空");
        }
        try {
            List<String> tokens = RcmdParser.tokenize(Rcmd.stripPrefix(rawInput));
            if (tokens.isEmpty()) {
                return RcmdResult.error("rcmd命令为空");
            }
            MatchResult match = findMatch(tokens);
            if (match == null) {
                return RcmdResult.error("未知rcmd命令：" + tokens.get(0));
            }
            RcmdContext context = new RcmdContext(source, rawInput, match.spec, tokens, match.arguments);
            RcmdResult result = match.spec.getCommand().execute(context);
            return result == null ? RcmdResult.ok() : result;
        } catch (RcmdParseException e) {
            return RcmdResult.error(e.getMessage());
        }
    }

    private MatchResult findMatch(List<String> tokens) throws RcmdParseException {
        RcmdParseException bestParseException = null;
        for (RcmdCommandSpec spec : commands) {
            List<String> path = spec.getPath();
            if (!matchesPath(tokens, path)) {
                continue;
            }
            try {
                Map<String, Object> arguments = parseArguments(spec, tokens, path.size());
                return new MatchResult(spec, arguments);
            } catch (RcmdParseException e) {
                bestParseException = new RcmdParseException(e.getMessage() + "。用法：" + spec.getUsage());
            }
        }
        if (bestParseException != null) {
            throw bestParseException;
        }
        return null;
    }

    private boolean matchesPath(List<String> tokens, List<String> path) {
        if (tokens.size() < path.size()) {
            return false;
        }
        for (int i = 0; i < path.size(); i++) {
            if (!path.get(i).toLowerCase(Locale.ROOT).equals(tokens.get(i).toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> parseArguments(RcmdCommandSpec spec, List<String> tokens, int tokenIndex) throws RcmdParseException {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        int currentIndex = tokenIndex;
        for (RcmdArgument<?> argument : spec.getArguments()) {
            RcmdArgumentParseResult<?> result = argument.getType().parse(tokens, currentIndex);
            values.put(argument.getName(), result.getValue());
            currentIndex = result.getNextTokenIndex();
        }
        if (currentIndex != tokens.size()) {
            throw new RcmdParseException("参数过多，无法识别：" + tokens.get(currentIndex));
        }
        return values;
    }

    private static final class MatchResult {
        private final RcmdCommandSpec spec;
        private final Map<String, Object> arguments;

        private MatchResult(RcmdCommandSpec spec, Map<String, Object> arguments) {
            this.spec = spec;
            this.arguments = arguments;
        }
    }
}
