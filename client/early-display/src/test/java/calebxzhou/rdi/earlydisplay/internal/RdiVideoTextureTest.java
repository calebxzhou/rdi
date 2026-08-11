package calebxzhou.rdi.earlydisplay.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RdiVideoTextureTest {
    @Test
    void bundlesStartupVideoResource() throws Exception {
        try (InputStream resource = RdiVideoTexture.class.getResourceAsStream("/startup.mp4")) {
            assertNotNull(resource);
            assertTrue(resource.read() >= 0);
        }
    }

    @Test
    void buildsD3d11Nv12CommandForHardwareDecode() {
        List<String> command = RdiVideoTexture.decoderCommand(Path.of("ffmpeg.exe"), Path.of("startup.mp4"), true);

        assertEquals("d3d11va", command.get(command.indexOf("-hwaccel") + 1));
        assertEquals("d3d11", command.get(command.indexOf("-hwaccel_output_format") + 1));
        assertTrue(command.get(command.indexOf("-vf") + 1).startsWith("scale_d3d11="));
        assertTrue(command.get(command.indexOf("-vf") + 1).contains("format=nv12,hwdownload,format=nv12"));
        assertTrue(command.contains("-re"));
    }

    @Test
    void softwareCommandDoesNotRequireHardwareFilters() {
        List<String> command = RdiVideoTexture.decoderCommand(Path.of("ffmpeg.exe"), Path.of("startup.mp4"), false);

        assertFalse(command.contains("-hwaccel"));
        assertFalse(command.contains("d3d11"));
        assertTrue(command.get(command.indexOf("-vf") + 1).startsWith("scale=720:360"));
    }
}
