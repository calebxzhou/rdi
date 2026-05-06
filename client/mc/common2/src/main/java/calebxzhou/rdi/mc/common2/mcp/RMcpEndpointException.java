package calebxzhou.rdi.mc.common2.mcp;

public class RMcpEndpointException extends RuntimeException {
    private final String code;

    public RMcpEndpointException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
