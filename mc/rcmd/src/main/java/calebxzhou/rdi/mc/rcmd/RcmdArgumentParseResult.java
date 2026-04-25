package calebxzhou.rdi.mc.rcmd;

public record RcmdArgumentParseResult<T>(T value, int nextTokenIndex) {
}
