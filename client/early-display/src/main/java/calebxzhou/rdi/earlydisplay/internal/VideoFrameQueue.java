package calebxzhou.rdi.earlydisplay.internal;

import java.nio.ByteBuffer;

final class VideoFrameQueue {
    private final ByteBuffer[] buffers;
    private int writing;
    private int ready = -1;
    private int uploading = -1;

    VideoFrameQueue(int frameBytes) {
        buffers = new ByteBuffer[] {
                ByteBuffer.allocateDirect(frameBytes),
                ByteBuffer.allocateDirect(frameBytes),
                ByteBuffer.allocateDirect(frameBytes)
        };
    }

    synchronized ByteBuffer writingBuffer() {
        ByteBuffer buffer = buffers[writing];
        buffer.clear();
        return buffer;
    }

    synchronized void publish() {
        ready = writing;
        writing = freeSlot();
    }

    synchronized ByteBuffer latestFrame() {
        if (ready == -1) return null;
        uploading = ready;
        ready = -1;
        ByteBuffer buffer = buffers[uploading].duplicate();
        buffer.clear();
        return buffer;
    }

    private int freeSlot() {
        for (int i = 0; i < buffers.length; i++) {
            if (i != ready && i != uploading) return i;
        }
        throw new IllegalStateException("No free video frame slot");
    }
}
