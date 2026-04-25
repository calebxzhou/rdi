package calebxzhou.rdi.mc.rcmd;

import java.util.List;
import java.util.Map;

public record RcmdContext(
        RcmdSource source,
        String rawInput,
        RcmdCommandSpec spec,
        List<String> tokens,
        Map<String, Object> arguments
) {
    public RcmdContext {
        tokens = List.copyOf(tokens);
        arguments = Map.copyOf(arguments);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String name) {
        return (T) arguments.get(name);
    }

    public String getString(String name) {
        return get(name);
    }

    public boolean getBool(String name) {
        return get(name);
    }

    public int getInt(String name) {
        return get(name);
    }

    public long getLong(String name) {
        return get(name);
    }

    public double getDouble(String name) {
        return get(name);
    }
}
