package calebxzhou.rdi.earlydisplay.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class VideoFrameQueueTest {
    @Test
    void keepsOnlyTheLatestCompletedFrame() {
        VideoFrameQueue queue = new VideoFrameQueue(1);
        queue.writingBuffer().put((byte) 1);
        queue.publish();
        queue.writingBuffer().put((byte) 2);
        queue.publish();

        assertEquals(2, queue.latestFrame().get(0));
        assertNull(queue.latestFrame());
    }

    @Test
    void rotatesAllThreeSlotsWithoutOverwritingTheUploadingFrame() {
        VideoFrameQueue queue = new VideoFrameQueue(1);
        queue.writingBuffer().put((byte) 1);
        queue.publish();
        assertEquals(1, queue.latestFrame().get(0));

        queue.writingBuffer().put((byte) 2);
        queue.publish();
        queue.writingBuffer().put((byte) 3);
        queue.publish();

        assertEquals(3, queue.latestFrame().get(0));
    }
}
