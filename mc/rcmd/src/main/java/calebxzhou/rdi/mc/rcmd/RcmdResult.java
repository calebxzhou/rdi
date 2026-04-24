package calebxzhou.rdi.mc.rcmd;

public final class RcmdResult {
    private final boolean success;
    private final String message;

    private RcmdResult(boolean success, String message) {
        this.success = success;
        this.message = message == null ? "" : message;
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

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
