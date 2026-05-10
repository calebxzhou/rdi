package calebxzhou.rdi.mc.common2.mcp;

/**
 * calebxzhou @ 2026-05-10 12:26
 */
public class RMError extends Exception {
    private final RErrorCode errorCode;

    public RMError(RErrorCode errorCode) {
        super(errorCode.id());
        this.errorCode = errorCode;
    }

    public RMError(RErrorCode errorCode, Throwable cause) {
        super(errorCode.id(), cause);
        this.errorCode = errorCode;
    }

    public RErrorCode errorCode() {
        return errorCode;
    }

    public String code() {
        return errorCode.id();
    }
}
