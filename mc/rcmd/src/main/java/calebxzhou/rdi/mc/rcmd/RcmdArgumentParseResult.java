package calebxzhou.rdi.mc.rcmd;

public final class RcmdArgumentParseResult<T> {
    private final T value;
    private final int nextTokenIndex;

    public RcmdArgumentParseResult(T value, int nextTokenIndex) {
        this.value = value;
        this.nextTokenIndex = nextTokenIndex;
    }

    public T getValue() {
        return value;
    }

    public int getNextTokenIndex() {
        return nextTokenIndex;
    }
}
