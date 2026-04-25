package calebxzhou.rdi.mc.rcmd;

import java.util.List;

public sealed interface RcmdArgumentType<T>
        permits RcmdArgumentTypes.BoolType,
        RcmdArgumentTypes.IntType,
        RcmdArgumentTypes.LongType,
        RcmdArgumentTypes.DoubleType,
        RcmdArgumentTypes.StringType,
        RcmdArgumentTypes.MessageType,
        RcmdArgumentTypes.EnumType {
    String name();

    RcmdArgumentParseResult<T> parse(List<String> tokens, int tokenIndex) throws RcmdParseException;
}
