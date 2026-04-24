package calebxzhou.rdi.mc.rcmd;

public final class RcmdArgument<T> {
    private final String name;
    private final RcmdArgumentType<T> type;

    public RcmdArgument(String name, RcmdArgumentType<T> type) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("参数名不能为空");
        }
        if (type == null) {
            throw new IllegalArgumentException("参数类型不能为空");
        }
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public RcmdArgumentType<T> getType() {
        return type;
    }
}
