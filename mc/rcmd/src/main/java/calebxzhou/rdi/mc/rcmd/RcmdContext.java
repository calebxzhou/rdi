package calebxzhou.rdi.mc.rcmd;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RcmdContext {
    private final RcmdSource source;
    private final String rawInput;
    private final RcmdCommandSpec spec;
    private final List<String> tokens;
    private final Map<String, Object> arguments;

    RcmdContext(RcmdSource source, String rawInput, RcmdCommandSpec spec, List<String> tokens, Map<String, Object> arguments) {
        this.source = source;
        this.rawInput = rawInput;
        this.spec = spec;
        this.tokens = tokens;
        this.arguments = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(arguments));
    }

    public RcmdSource getSource() {
        return source;
    }

    public String getRawInput() {
        return rawInput;
    }

    public RcmdCommandSpec getSpec() {
        return spec;
    }

    public List<String> getTokens() {
        return tokens;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String name) {
        return (T) arguments.get(name);
    }
}
