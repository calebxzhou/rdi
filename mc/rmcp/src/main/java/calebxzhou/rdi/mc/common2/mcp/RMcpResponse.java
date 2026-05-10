package calebxzhou.rdi.mc.common2.mcp;

public record RMcpResponse<T>(String code, T data) {
    public static <T> RMcpResponse<T> ok(T data) {
        return new RMcpResponse<>("ok", data);
    }

    public static RMcpResponse<Void> error(String code) {
        return new RMcpResponse<>(code, null);
    }
    public static RMcpResponse<String> error(String code,String data) {
        return new RMcpResponse<>(code, data);
    }
}
