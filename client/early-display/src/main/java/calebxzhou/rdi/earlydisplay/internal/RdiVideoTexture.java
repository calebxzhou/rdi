package calebxzhou.rdi.earlydisplay.internal;

import static org.lwjgl.opengl.GL32C.*;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class RdiVideoTexture implements AutoCloseable {
    static final int WIDTH = 720;
    static final int HEIGHT = 360;
    static final int VIDEO_FPS = 20;
    static final long FRAME_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1) / VIDEO_FPS;
    private static final String STARTUP_VIDEO_RESOURCE = "/startup.mp4";
    private static final long HARDWARE_STARTUP_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final long FRAME_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(3);

    private final VideoFrameQueue frames = new VideoFrameQueue(WIDTH * HEIGHT * 4);
    private volatile long lastCompleteFrameNanos = System.nanoTime();
    private volatile boolean running;
    private volatile boolean disabled;
    private volatile boolean hardwareDecoder;
    private volatile boolean receivedFrame;
    private volatile long decoderStartedNanos;
    private volatile Process process;
    private Path ffmpegPath;
    private Path videoPath;
    private int texture;
    private long nextUploadNanos;

    void start() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) return;
        String ffmpegProperty = System.getProperty("rdi.earlyDisplay.ffmpeg");
        String videoProperty = System.getProperty("rdi.earlyDisplay.video");
        if (ffmpegProperty == null) return;
        Path ffmpeg = Path.of(ffmpegProperty);
        Path video = resolveVideo(videoProperty);
        if (!Files.isRegularFile(ffmpeg) || video == null) return;
        ffmpegPath = ffmpeg;
        videoPath = video;
        if (!startDecoder(true)) startDecoder(false);
    }

    private static Path resolveVideo(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            try {
                Path configured = Path.of(configuredPath);
                if (Files.isRegularFile(configured)) return configured;
            } catch (RuntimeException ignored) {
            }
        }
        return extractBundledVideo();
    }

    private static Path extractBundledVideo() {
        Path temporary = null;
        try {
            Path directory = Path.of(System.getProperty("java.io.tmpdir"), ".rdi", "early-display");
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, "startup-", ".tmp");
            try (InputStream input = RdiVideoTexture.class.getResourceAsStream(STARTUP_VIDEO_RESOURCE)) {
                if (input == null) return null;
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }

            Path target = directory.resolve("startup.mp4");
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private synchronized boolean startDecoder(boolean hardware) {
        if (disabled) return false;
        try {
            Process decoder = new ProcessBuilder(decoderCommand(ffmpegPath, videoPath, hardware))
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            process = decoder;
            hardwareDecoder = hardware;
            receivedFrame = false;
            decoderStartedNanos = System.nanoTime();
            lastCompleteFrameNanos = decoderStartedNanos;
            running = true;
            Thread readerThread = new Thread(() -> readFrames(decoder, hardware), "RDI early video decoder");
            readerThread.setDaemon(true);
            readerThread.start();
            return true;
        } catch (Exception ignored) {
            running = false;
            return false;
        }
    }

    static List<String> decoderCommand(Path ffmpeg, Path video, boolean hardware) {
        var command = new ArrayList<String>();
        command.add(ffmpeg.toString());
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        if (hardware) {
            command.add("-hwaccel");
            command.add("d3d11va");
            command.add("-hwaccel_output_format");
            command.add("d3d11");
        }
        command.add("-re");
        command.add("-stream_loop");
        command.add("-1");
        command.add("-i");
        command.add(video.toString());
        command.add("-an");
        command.add("-vf");
        command.add(hardware
                ? "scale_d3d11=width=720:height=360:format=nv12,hwdownload,format=nv12,fps=" + VIDEO_FPS
                : "scale=720:360:flags=bilinear,fps=" + VIDEO_FPS);
        command.add("-pix_fmt");
        command.add("rgba");
        command.add("-f");
        command.add("rawvideo");
        command.add("pipe:1");
        return command;
    }

    private void readFrames(Process decoder, boolean hardware) {
        try (InputStream input = decoder.getInputStream()) {
            byte[] chunk = new byte[64 * 1024];
            while (running && process == decoder) {
                ByteBuffer frame = frames.writingBuffer();
                while (frame.hasRemaining()) {
                    int read = input.read(chunk, 0, Math.min(chunk.length, frame.remaining()));
                    if (read == -1) return;
                    frame.put(chunk, 0, read);
                }
                frames.publish();
                receivedFrame = true;
                lastCompleteFrameNanos = System.nanoTime();
            }
        } catch (Exception ignored) {
        } finally {
            if (process == decoder) {
                running = false;
                if (hardware && !disabled) startDecoder(false);
            }
        }
    }

    void uploadLatestFrame(long nowNanos) {
        if (disabled) return;
        if (hardwareDecoder && !receivedFrame && nowNanos - decoderStartedNanos >= HARDWARE_STARTUP_TIMEOUT_NANOS) {
            stopDecoder();
            startDecoder(false);
        }
        if (nowNanos - lastCompleteFrameNanos >= FRAME_TIMEOUT_NANOS) {
            close();
            return;
        }
        if (nextUploadNanos == 0) nextUploadNanos = nowNanos;
        if (nowNanos < nextUploadNanos) return;
        ByteBuffer frame = frames.latestFrame();
        if (frame == null) return;
        do {
            nextUploadNanos += FRAME_INTERVAL_NANOS;
        } while (nextUploadNanos <= nowNanos);
        if (texture == 0) {
            texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, frame);
        } else {
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, frame);
        }
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    int texture() { return texture; }

    private synchronized void stopDecoder() {
        running = false;
        Process decoder = process;
        process = null;
        if (decoder != null) decoder.destroy();
    }

    @Override
    public void close() {
        disabled = true;
        stopDecoder();
        if (texture != 0) {
            glDeleteTextures(texture);
            texture = 0;
        }
    }
}
