package calebxzhou.rdi.mc.rcmd;

public record RcmdResult(boolean success, String message) {
    public RcmdResult {
        message = message == null ? "" : message;
    }

    public static RcmdResult ok() {
        return new RcmdResult(true, "");
    }

    public static RcmdResult ok(String message) {
        return new RcmdResult(true, message);
    }

    public static RcmdResult error(String message) {
        return new RcmdResult(false, message);
    }
}
