package calebxzhou.rdi.earlydisplay;

public interface RdiEarlyDisplaySession extends AutoCloseable {
    int PROTOCOL_VERSION = 1;

    int protocolVersion();
    int progressTextureId();
    int videoTextureId();
    int videoWidth();
    int videoHeight();
    int logicalWidth();
    int logicalHeight();
    int framebufferScale();
    boolean hasVideoFrame();
    void renderProgress(int alpha);
    @Override void close();
}
