package calebxzhou.rdi.mc.common2.mcp;

public record RMcpResponse<T>(String code, String msg, T data) {
    public static <T> RMcpResponse<T> ok(T data) {
        return new RMcpResponse<>("ok", "", data);
    }

    public static RMcpResponse<Void> error(String code, String msg) {
        return new RMcpResponse<>(code, msg, null);
    }
}
