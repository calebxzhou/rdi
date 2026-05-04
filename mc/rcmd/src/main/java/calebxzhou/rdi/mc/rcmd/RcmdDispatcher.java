package calebxzhou.rdi.mc.rcmd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RcmdDispatcher {
    private final List<RcmdCommandSpec> commands = new ArrayList<>();

    public void register(RcmdCommandSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("命令定义不能为空");
        }
        commands.add(spec);
    }

    public List<RcmdCommandSpec> getCommands() {
        return List.copyOf(commands);
    }

    public RcmdResult execute(RcmdSource source, String rawInput) {
        RcmdDispatchResult dispatchResult = dispatch(source, rawInput);
        if (dispatchResult.found()) {
            return dispatchResult.result();
        }
        try {
            List<String> tokens = RcmdParser.tokenize(Rcmd.stripPrefix(rawInput));
            return tokens.isEmpty()
                    ? RcmdResult.error("rcmd命令为空")
                    : RcmdResult.error("未知rcmd命令：" + tokens.getFirst());
        } catch (RcmdParseException e) {
            return RcmdResult.error(e.getMessage());
        }
    }

    public RcmdDispatchResult dispatch(RcmdSource source, String rawInput) {
        if (source == null) {
            throw new IllegalArgumentException("命令来源不能为空");
        }
        try {
            List<String> tokens = RcmdParser.tokenize(Rcmd.stripPrefix(rawInput));
            if (tokens.isEmpty()) {
                return RcmdDispatchResult.found(RcmdResult.error("rcmd命令为空"));
            }
            MatchResult match = findMatch(tokens);
            if (match == null) {
                return RcmdDispatchResult.notFound();
            }
            var context = new RcmdContext(source, rawInput, match.spec(), tokens, match.arguments());
            var result = match.spec().command().execute(context);
            return RcmdDispatchResult.found(result);
        } catch (RcmdParseException e) {
            return RcmdDispatchResult.found(RcmdResult.error(e.getMessage()));
        }
    }

    private MatchResult findMatch(List<String> tokens) throws RcmdParseException {
        List<RcmdCommandSpec> candidates = resolveCandidates(tokens);
        if (candidates.isEmpty()) {
            return null;
        }
        RcmdParseException bestParseException = null;
        for (var spec : candidates) {
            try {
                Map<String, Object> arguments = parseArguments(spec, tokens, spec.path().size());
                return new MatchResult(spec, arguments);
            } catch (RcmdParseException e) {
                bestParseException = new RcmdParseException(e.getMessage() + "。用法：" + spec.usage());
            }
        }
        if (bestParseException != null) {
            throw bestParseException;
        }
        return null;
    }

    private List<RcmdCommandSpec> resolveCandidates(List<String> tokens) throws RcmdParseException {
        var exactCandidates = new ArrayList<RcmdCommandSpec>();
        var prefixCandidates = new ArrayList<RcmdCommandSpec>();
        var incompleteCandidates = new ArrayList<RcmdCommandSpec>();
        for (var spec : commands) {
            var path = spec.path();
            var status = matchPath(tokens, path);
            switch (status) {
                case EXACT -> exactCandidates.add(spec);
                case PREFIX -> prefixCandidates.add(spec);
                case INCOMPLETE -> incompleteCandidates.add(spec);
                case NONE -> {
                }
            }
        }
        if (!exactCandidates.isEmpty()) {
            return exactCandidates;
        }
        if (prefixCandidates.size() == 1) {
            return prefixCandidates;
        }
        if (prefixCandidates.size() > 1) {
            throw new RcmdParseException("rcmd命令前缀不明确：" + commandPrefix(tokens, prefixCandidates) + "，可匹配：" + usages(prefixCandidates));
        }
        if (incompleteCandidates.size() == 1) {
            throw new RcmdParseException("rcmd命令不完整：" + commandPrefix(tokens, incompleteCandidates) + "。用法：" + incompleteCandidates.getFirst().usage());
        }
        if (incompleteCandidates.size() > 1) {
            throw new RcmdParseException("rcmd命令前缀不明确：" + commandPrefix(tokens, incompleteCandidates) + "，可匹配：" + usages(incompleteCandidates));
        }
        return List.of();
    }

    private PathMatchStatus matchPath(List<String> tokens, List<String> path) {
        int checkedParts = Math.min(tokens.size(), path.size());
        boolean exact = tokens.size() >= path.size();
        for (int i = 0; i < checkedParts; i++) {
            String token = tokens.get(i).toLowerCase(Locale.ROOT);
            String pathPart = path.get(i).toLowerCase(Locale.ROOT);
            if (!pathPart.startsWith(token)) {
                return PathMatchStatus.NONE;
            }
            if (!pathPart.equals(token)) {
                exact = false;
            }
        }
        if (tokens.size() < path.size()) {
            return PathMatchStatus.INCOMPLETE;
        }
        return exact ? PathMatchStatus.EXACT : PathMatchStatus.PREFIX;
    }

    private String commandPrefix(List<String> tokens, List<RcmdCommandSpec> candidates) {
        int commandPartCount = candidates.stream()
                .mapToInt(spec -> spec.path().size())
                .min()
                .orElse(tokens.size());
        return String.join(" ", tokens.subList(0, Math.min(tokens.size(), commandPartCount)));
    }

    private String usages(List<RcmdCommandSpec> specs) {
        var builder = new StringBuilder();
        for (int i = 0; i < specs.size(); i++) {
            if (i > 0) {
                builder.append('、');
            }
            builder.append(specs.get(i).usage());
        }
        return builder.toString();
    }

    private Map<String, Object> parseArguments(RcmdCommandSpec spec, List<String> tokens, int tokenIndex) throws RcmdParseException {
        var values = new LinkedHashMap<String, Object>();
        int currentIndex = tokenIndex;
        for (var argument : spec.arguments()) {
            var result = argument.type().parse(tokens, currentIndex);
            values.put(argument.name(), result.value());
            currentIndex = result.nextTokenIndex();
        }
        if (currentIndex != tokens.size()) {
            throw new RcmdParseException("参数过多，无法识别：" + tokens.get(currentIndex));
        }
        return values;
    }

    private record MatchResult(RcmdCommandSpec spec, Map<String, Object> arguments) {
    }

    private enum PathMatchStatus {
        NONE,
        INCOMPLETE,
        PREFIX,
        EXACT
    }
}
