package calebxzhou.rdi.mc.rcmd;

public record RcmdDispatchResult(boolean found, RcmdResult result) {
    public static RcmdDispatchResult found(RcmdResult result) {
        return new RcmdDispatchResult(true, result == null ? RcmdResult.ok() : result);
    }

    public static RcmdDispatchResult notFound() {
        return new RcmdDispatchResult(false, RcmdResult.ok());
    }
}
