package calebxzhou.rdi.mc.common2.mcp;

public class RMcpEndpointException extends RuntimeException {
    private final String code;

    public RMcpEndpointException(String code) {
        super(code);
        this.code = code;
    }

    public RMcpEndpointException(RErrorCode code) {
        this(code.id());
    }

    public String code() {
        return code;
    }
}
