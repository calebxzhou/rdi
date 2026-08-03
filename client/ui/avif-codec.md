# AVIF codec handoff

## Goal

Continue the design and eventual implementation of transparent AVIF processing for uploaded modpacks.

The launcher currently converts eligible PNG files to JPEG bytes while keeping their `.png` filenames. The desired replacement is:

- encode eligible PNG files as AVIF without changing their filenames;
- preserve alpha instead of flattening transparent pixels;
- ship the required FFmpeg runtime with the Windows x64 client;
- add the runtime JARs to Minecraft's launch classpath;
- decode disguised AVIF files in supported Minecraft clients through version-appropriate adapters/mixins.

The implementation is now authorized and the first vertical slice is in place: `mediaproc` owns AVIF detection/encode/decode, and the 1.21.1 NeoForge client has a `NativeImage.read()` adapter. Production-classloader and visual resource-pack validation remain separate verification steps.

## Repository and collaboration constraints

- Repository root: `/mnt/c/users/calebxzhou/documents/coding/rdi5`.
- Reply in Mandarin, while retaining English technical terminology.
- Do not add spaces between Chinese and letters/numbers.
- Do not run Git, Gradle or Javac commands unless the user explicitly authorizes them.
- Do not change code without explicit user approval; show the plan first.
- Do not delete project files. Move removals to `/DEL` if removal is requested.
- Use Kotlin with 4-space indentation and `Result<T>` for operations that can fail.
- Callers must log or throw failures explicitly; avoid silent `getOrNull` behavior.
- Do not use `File.deleteRecursively()`; use `deleteRecursivelyNoSymlink()` for recursive deletion.
- Target platform is Windows x64 only.
- JavaCPP native libraries should extract to the user's `.javacpp/cache` directory.

## Existing media module

The Gradle submodule is located at:

- `client/ui/mediaproc`

Its package prefix is:

- `calebxzau.rdi.mediaproc`

Current dependencies in `client/ui/mediaproc/build.gradle.kts` include:

- JavaCV `1.5.13`;
- FFmpeg preset `8.0.1-1.5.13`;
- Windows x86_64 FFmpeg runtime;
- coroutines.

This module currently owns the OGG transcoder. Preserve the existing OGG contract:

- processing concurrency is 8;
- if one OGG cannot be decoded, log the error and substitute `client/ui/assets/src/main/resources/assets/empty.ogg`;
- media module damage is logged only; do not show the user a UI warning.

The AVIF codec should also live in this module, but should not alter the OGG behavior.

## Current PNG processing

Relevant file:

- `client/ui/src/main/kotlin/calebxzhou/rdi/client/service/PngCompression.kt`

Current behavior:

- files at or below 50KiB remain unchanged;
- larger PNGs are decoded with `ImageIO`;
- images taller than 720px are resized to 720px while preserving aspect ratio;
- transparent pixels are flattened onto white;
- output is JPEG at quality `0.5`;
- returned JPEG bytes retain the original `.png` path;
- all exceptions are currently swallowed and the original PNG is returned.

Upload call sites are in:

- `client/ui/src/main/kotlin/calebxzhou/rdi/client/service/ModpackUploadService.kt`

PNG processing is applied to normal entries, nested ZIP/JAR entries and resource-pack entries. `preprocessAssetInputsInParallel()` currently shares a semaphore of 8 between PNG and OGG work.

For AVIF:

- retain the 50KiB threshold unless the user changes it;
- retain the 720px maximum height unless the user changes it;
- preserve alpha instead of drawing onto white;
- on encode failure, log the complete exception and return the original PNG bytes;
- do not write incomplete or corrupt AVIF bytes;
- use a separate AVIF semaphore, initially proposed at 2 because AV1 encoding is CPU-heavy. This concurrency value has not yet been confirmed by the user.

## Alpha-preserving AVIF design

Do not use `FFmpegFrameRecorder` for alpha AVIF. JavaCV's recorder models only one video stream, while FFmpeg's AVIF muxer requires two video streams when alpha is present.

Implement a dedicated low-level JavaCPP FFmpeg codec behind a small interface.

Proposed public model:

```kotlin
data class DecodedRgbaImage(
    val width: Int,
    val height: Int,
    val pixels: ByteArray,
)

interface AvifCodec {
    fun encodePng(input: ByteArray): Result<ByteArray>
    fun decode(input: ByteArray): Result<DecodedRgbaImage>
}
```

The concrete Implementation may be named `FfmpegAvifCodec` and should hide all JavaCPP types from callers.

### Encode pipeline

```text
PNG RGBA
   |-- RGB -> YUV444P -> AV1 color stream
   `-- A   -> GRAY8   -> AV1 alpha stream
                              |
                         AVIF muxer
```

Requirements:

1. Decode PNG to straight, non-premultiplied RGBA.
2. Resize without flattening alpha. If AWT is retained, use an alpha-capable image and `AlphaComposite.Src`.
3. Detect whether every alpha sample is 255.
4. For an opaque image, emit only the color stream.
5. For an image containing transparency:
   - stream 0 is the AV1-encoded color image;
   - stream 1 is a one-plane monochrome AV1 alpha mask;
   - both streams have identical dimensions and timestamps;
   - alpha should be encoded losslessly to avoid halos on partially transparent edges.
6. Prefer `YUV444P` for the color stream. `YUV420P` can visibly damage small Minecraft textures and transparent edges.
7. Emit one still-image frame, not an animated AVIF.
8. Close every `AVFrame`, `AVPacket`, `AVCodecContext`, `SwsContext`, `AVFormatContext` and IO allocation deterministically.
9. Return the resulting bytes through `Result<ByteArray>`.

FFmpeg's official AVIF muxer contract states that it accepts one or two video streams and that the second stream, when present, is a single-plane alpha mask:

- https://ffmpeg.org/ffmpeg-formats.html#avif
- https://ffmpeg.org/pipermail/ffmpeg-devel/2022-June/297507.html

Do not silently fall back from alpha AVIF to white-background AVIF. The safe fallback is the untouched original PNG.

### Decode pipeline

```text
AVIF bytes
   |-- stream 0 -> AV1 decode -> color
   `-- stream 1 -> AV1 decode -> alpha mask
                                      |
                                 RGBA merge
```

Requirements:

1. Recognize AVIF by its ISO BMFF `ftyp` brands, including `avif` and `avis`; never use the `.png` suffix as the format check.
2. Decode the color stream to the byte order expected by the game adapter.
3. If no alpha stream exists, fill alpha with 255.
4. If an alpha stream exists, require it to be monochrome and the same dimensions as the color stream.
5. Merge color and alpha into one tightly packed RGBA buffer.
6. Reject malformed streams, dimension mismatches and unreasonable dimensions with explicit failures.
7. Use a separate decoder context per call; texture reloads may decode images concurrently.

## Minecraft adapters

### Minecraft 1.21.1 NeoForge

Current JPEG workaround:

- `client/mc/1.21.1-neoforge/src/main/java/calebxzhou/rdi/mc/client/mixin/mJpgImage.java`

It cancels `PngInfo.validateHeader()` unconditionally. This only permits STB to inspect disguised JPEG bytes. STB cannot decode AVIF, so this mixin alone cannot support AVIF.

The clean AVIF Seam is:

```text
NativeImage.read(@Nullable Format, ByteBuffer)
```

The generated NeoForge source is at:

- `client/mc/1.21.1-neoforge/build/moddev/artifacts/neoforge-21.1.233-sources/com/mojang/blaze3d/platform/NativeImage.java`

Inject at `HEAD`:

1. Inspect the buffer without changing its externally visible position/order.
2. If it is not AVIF, return without cancelling and preserve the vanilla path.
3. If it is AVIF, decode through the AVIF runtime adapter.
4. Allocate a `NativeImage` and bulk-copy the decoded pixels in the exact byte order expected by `NativeImage`.
5. Set the callback return value.

The implemented adapter is:

- `client/mc/1.21.1-neoforge/src/main/kotlin/calebxzhou/rdi/mc/client/texture/AvifNativeImageAdapter.kt`;
- `client/mc/1.21.1-neoforge/src/main/java/calebxzhou/rdi/mc/client/mixin/mNativeImage.java`;
- `client/mc/1.21.1-neoforge/src/main/kotlin/calebxzhou/rdi/mc/client/texture/RdiNativeImagePixels.kt`.

It recognizes AVIF from the `ftyp` brands, preserves the caller's `ByteBuffer` state, accepts RGBA output, bounds input to64MiB, decodes through `mediaproc`, allocates a native RGBA image and bulk-copies the pixel buffer. Avoid calling `setPixelRGBA()` once per pixel; per-pixel JNI/native access can make resource reloads slow.

`PngInfo.fromBytes()` is separately used for multiplayer server icons. The current source search showed no ordinary resource texture use outside `NativeImage`, so the `NativeImage.read()` adapter is the primary 1.21.1 texture Seam. The adapter is compiled against the exact NeoForge21.1.233 source set.

### Minecraft 1.20.1 Forge

The same `NativeImage.read(Format, ByteBuffer)` Seam exists in the generated 1.20.1 Forge source. It does not have the same `PngInfo.validateHeader()` call, but the AVIF `HEAD` short-circuit design remains applicable.

### Minecraft 1.7.10 and 1.12.2

Legacy Minecraft uses `ImageIO.read()` across many independent paths such as atlas textures, simple textures, fonts, pack icons, skins and server icons. Do not create dozens of redirect mixins.

Preferred legacy Adapter:

1. Implement a `javax.imageio.spi.ImageReaderSpi` that recognizes AVIF content and delegates to the shared decoder.
2. Register it once during client bootstrap through a small version-specific mixin.
3. Return a `BufferedImage` containing the decoded RGBA data.

Verify the exact generated 1.12.2 Minecraft sources before implementation. The 1.7.10 source already confirms broad `ImageIO.read()` usage.

## Shipping FFmpeg to the game

Launcher classpath construction is in:

- `client/ui/src/main/kotlin/calebxzhou/rdi/client/service/GameInstallerBootstrapper.kt`
- function: `buildLaunchClasspath()`

The launcher already substitutes `${classpath}` or adds `-cp` when the manifest does not declare one. This is the launcher-side Seam for the media runtime.

Install only the minimal game runtime into a stable launcher-managed libraries directory:

- `javacpp-1.5.13.jar`;
- `ffmpeg-8.0.1-1.5.13.jar`;
- `ffmpeg-8.0.1-1.5.13-windows-x86_64-gpl.jar`;
- `kotlinx-coroutines-core-jvm-1.11.0.jar` because the shared media API already uses coroutine synchronization for bounded work;
- the small RDI AVIF runtime/adapter JAR needed by the game;
- `javacv-1.5.13.jar` only if game-side code truly uses JavaCV. Prefer direct FFmpeg presets so the game does not inherit JavaCV's unrelated transitive wrappers.

Do not reference Gradle cache paths or developer-machine paths. The launcher must install, validate and repair these files like other launch libraries.

The launcher now checks both Opus and AVIF decode readiness before adding the existing single media classpath. The 1.21.1 dev launch proved that the transformed mod classloader can resolve JavaCPP, FFmpeg and coroutines; the production-installed launcher path still needs its release-library validation. If ordinary `-cp` visibility fails there, adapt the modern module-path/library wiring rather than duplicating FFmpeg inside every mod JAR.

## Compatibility consequences

An `.png` entry containing AVIF bytes is intentionally non-standard:

- Vanilla Minecraft cannot load it.
- Clients without the RDI decoder cannot load it.
- Image tools and resource-pack validators may report it as corrupt.
- Exported/shared modpacks only work correctly when the receiving client includes the matching RDI game support.

Do not describe this as generally compatible PNG compression.

## Recommended implementation order

Completed or in progress after approval:

1. `mediaproc` AVIF signature detection, RGBA split/merge and alpha round-trip tests are present.
2. Low-level FFmpeg AVIF encode/decode is present, including the two-stream alpha form.
3. PNG preprocessing and the launcher media runtime remain backward-compatible; encode failure still returns the original PNG.
4. The 1.21.1 `NativeImage.read()` adapter and bulk RGBA Mixin are present. A real NeoForge client launch resolved JavaCPP, FFmpeg and coroutines; the Mixin transformed `NativeImage.read()` successfully.
5. A temporary resource pack containing AVIF bytes under a `.png` texture path loaded through the production resource reload without a texture/decode error. Expand the fixture to cover opaque, binary-alpha, gradient-alpha and malformed AVIF cases.
6. Port the modern adapter to 1.20.1.
7. Add the legacy `ImageReaderSpi` adapter for 1.7.10 and 1.12.2.

The smallest useful vertical slice is `mediaproc` plus launcher runtime plus Minecraft 1.21.1. Do not begin with every Minecraft version simultaneously.

## Validation cases

- PNG below 50KiB remains unchanged.
- Opaque PNG above 50KiB becomes single-stream AVIF.
- Fully transparent pixels remain alpha 0.
- Gradient alpha round-trips without visible banding.
- Transparent colored edges do not develop white or black halos.
- Resizing preserves aspect ratio and alpha.
- Corrupt PNG logs an error and remains unchanged.
- Corrupt AVIF reports a decode failure without native memory leaks.
- Existing disguised JPEG still loads if backward compatibility remains required.
- Repeated resource reloads do not leak native memory.
- Parallel upload processing remains bounded and does not saturate the machine indefinitely.
- Minecraft 1.21.1 can resolve the JavaCPP/FFmpeg classes through its production classloader.

## Decisions still required

Ask these one at a time before implementation if the user has not already answered them:

1. Confirm the initial AVIF encode concurrency, proposed as 2.
2. Confirm the desired color quality/size target after a small representative benchmark; do not guess a final CRF solely from the old JPEG quality `0.5`.
3. Confirm whether existing disguised JPEG support must remain indefinitely.
4. Confirm the first supported Minecraft version is 1.21.1 before porting to the other versions.

## Suggested skills

- `codebase-design`: keep the public AVIF codec interface small and hide the low-level FFmpeg implementation.
- `tdd`: implement signature parsing, RGBA split/merge and failure behavior test-first after authorization.
- `diagnosing-bugs`: use if JavaCPP native loading, NeoForge classloader visibility or pixel-format corruption fails during the vertical slice.
- `implement`: use only after the user approves the implementation plan.
