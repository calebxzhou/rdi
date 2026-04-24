package calebxzhou.rdi.mc.rcmd;

import java.util.List;

public interface RcmdArgumentType<T> {
    String getName();

    RcmdArgumentParseResult<T> parse(List<String> tokens, int tokenIndex) throws RcmdParseException;
}
