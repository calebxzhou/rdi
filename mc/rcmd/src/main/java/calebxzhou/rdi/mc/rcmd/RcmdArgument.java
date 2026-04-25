package calebxzhou.rdi.mc.rcmd;

public record RcmdArgument<T>(String name, RcmdArgumentType<T> type) {
    public RcmdArgument {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("参数名不能为空");
        }
        if (type == null) {
            throw new IllegalArgumentException("参数类型不能为空");
        }
    }
}
