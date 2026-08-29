# bg-renderer Agent Notes

## Purpose

This module renders the launcher's lightweight Minecraft-style animated background. It does not start Minecraft or reuse Minecraft client rendering code. It bundles a deliberately minimal, approved subset of Minecraft 1.21.1 vanilla PNG resources for its own standalone OpenGL materials; the packaged renderer has no Minecraft runtime or JAR dependency. The current sky/cloud behavior was modeled after the local Minecraft 1.21.1 `LevelRenderer` sky and cloud passes.

The Gradle project name is `:bg-renderer`. The consuming Gradle root is `client/ui`; `client/ui/settings.gradle.kts` maps the project to `../bg-renderer`.

## Module Boundary

- `:bg-renderer` owns the fixed scene, shaders, render thread, off-screen framebuffer, frame conversion, animation timing, and performance detection.
- `:render-core` owns the process-wide GLFW lease and synchronized hidden-window creation/destruction.
- `:ui` owns Compose lifecycle, navigation/window activation policy, and the static `assets/bg.avif` fallback.
- `:player-model` is separate, but it uses the same `:render-core` GLFW lifecycle.
- Do not make `:bg-renderer` depend on the main `:ui` project or on `:player-model`.
- Do not call `glfwInit`, `glfwTerminate`, `glfwCreateWindow`, or `glfwDestroyWindow` directly from this module. Use `GlfwRuntime`.

## Current Implementation

Main files:

- `BackgroundScene.kt`: one-shot seeded dune/cactus data, deterministic irregular `HarborBlock`/`HarborGroup` layout with harbor architecture details, bounded camera/boat animation, fixed noon `SunState`, water level, Minecraft 1.21.1 Fancy cloud coordinates, resolution, and FPS constants.
- `BackgroundRenderer.kt`: public session API, render lifecycle, OpenGL scene passes, 4xMSAA framebuffer/resolve, readback, shaders, and performance monitor.
- `BackgroundSceneTest.kt`: GPU-independent scene, geometry, fixed-sky/sun, cloud, and performance-monitor tests.

`BackgroundRenderer.openSession()` returns a `BackgroundRenderSession` exposing:

- `frame: StateFlow<ImageBitmap?>`
- `failure: StateFlow<String?>`
- `lowPerformance: StateFlow<Boolean>`
- `setActive(Boolean)`
- `close()`

The renderer uses a hidden OpenGL 4.5 core context on the daemon thread `rdi-background-renderer`. It renders a fixed `1280x720` frame at `20FPS`, synchronously reads BGRA pixels, vertically flips them, and publishes a Compose `ImageBitmap`.

The scene currently contains:

- A deterministic irregular three-depth harbor made from `HarborBlock` descriptors: `Foreground`, `Midground`, and `Background` terraces with per-layer drift/zigzag offsets, left/right docks and detailed shelters, `LeftPavilion`, `CenterMarket`, and the shifted `RightLighthouse` at x≈18; the layout spans approximately `x=-60..60`
- Harbor structures include shelter walls/rails/crates, pavilion wall panels/rails/crates, market stall walls/side walls/counters/crates, pier rails/crates, and a lighthouse supported by four continuous sand terraces plus a shaft/observation rails.
- One-shot seeded, bounded far dunes and fixed cacti groups (24 dunes and 16 cacti)
- A small oak-plank boat whose local kind-2 mesh is transformed by the animated pose centered at `BOAT_CENTER_X=-14`
- One bounded-size `WATER_EXTENT` water quad that reaches the far-plane horizon, with distance fog blending only water into the sky horizon color
- An animated Complementary-inspired water plane using a stable shader/material base tint and opacity, cloud-water normal map, and noise texture
- Moving textured clouds from the bundled vanilla `environment/clouds.png`
- A smooth east-to-west-to-east camera and bounded boat slide/rock motion

The sky is fixed at noon with `NOON_SKY_COLOR=(0.14, 0.40, 0.78)`; there is no day/night cycle, moon, or stars. `SunState` is the shared truth source for the visible sun and terrain light direction; its fixed billboard position is `(0, 26.7, -6)`, and its direction is derived from that position toward the camera target `(0, 5, -25)`, producing an approximately `48.8°` lighting elevation. The shared `SUN_BILLBOARD_HALF_SIZE=4.45` constant defines the world quad and normalized `0..1` vertex UV mapping. The sun is a single camera-facing billboard with a textured disc and an HDR halo. The render order is main MSAA HDR sky, sun, non-water terrain, player, clouds, pre-water HDR color/depth capture, a half-resolution HDR planar reflection scene, main MSAA rebind, water composite, raw HDR resolve, linearization/mipmap generation, fixed seven-level Bloom atlas, modified Lottes Tonemap, BSL saturation/vibrance, and final RGBA8 readback. The reflection pass uses a 640x360 `GL_R11F_G11F_B10F` color texture and DEPTH_COMPONENT24 renderbuffer without MSAA, mipmaps, PBOs, or per-frame allocations; these objects are owned and released by `OffscreenFramebuffer` on the render thread. `BackgroundPostProcessor` owns its mipmapped HDR linear texture, RGBA8 atlas/final targets, fullscreen geometry, and three programs on that same thread. The Bloom kernel derives its pixel offsets from the base-level size of that linear HDR texture.

Clouds use a Minecraft 1.21.1 Fancy-derived path: `0.6*time` drift in 8-unit horizontal coordinates, a 2048-coordinate wrap, camera-relative `+0.33` offsets, height38, thickness3, and a `-6..6` tile grid. The generated mesh uses `POSITION_TEX_COLOR_NORMAL` attributes, vanilla UVs, neutral RGB face colors, and face normals. The bundled cloud texture uses one nearest-filtered sample like Minecraft's `TextureStateShard(false, false)`; there is no neighbor expansion. Cloud fog uses the separate `NOON_CLOUD_FOG_COLOR=(0.86, 0.90, 0.96)` and range `300..400`, rather than the water fog range. The shader applies the shared `SunState` direction with cool ambient `(0.82, 0.86, 0.92)`, warm direct `(1.0, 0.84, 0.66)`, `ambient=0.94+0.12*wrapped`, `direct=0.50*max(ndotl,0)`, wrapped diffuse `(ndotl+0.55)/1.55`, and 0.18 transmission; lighting is applied before fixed-noon linear fog using view-space distance and the scene fog range. Main and planar reflection cloud passes use the same world sun direction. Back-face culling is disabled for both cloud passes; clouds use a depth-only prepass followed by a color+depth pass with straight alpha.

The camera uses a fixed distance of `50`, base height `8`, height swing `0.25`, target `(0, 5, -25)`, FOV `60`, and yaw sweep `0.045` radians. The boat uses `BOAT_VISUAL_SCALE=1.25` and the player uses `PLAYER_VISUAL_SCALE=0.08`, while the boat remains centered at `BOAT_CENTER_X=-14`. The static scene mesh is initialized once into one VBO with contiguous material batches; the main and reflection passes reuse it. The pre-water scene is captured before the water pass, and only the boat's local geometry uses kind `2` model transformation.

This is procedural Minecraft-style scenery, not an exact Minecraft renderer and not a real world/chunk loader.

The scene deliberately bundles a minimal vanilla texture set extracted from the local Minecraft 1.21.1 NeoForge resource JAR
`client/mc/1.21.1-neoforge/build/moddev/artifacts/neoforge-21.1.233-client-extra-aka-minecraft-resources.jar`:
`sand`, `oak_planks`, `oak_log`, `oak_log_top`, the three cactus faces, first-frame `lantern`, and `environment/sun`.
The user explicitly accepted the responsibility for redistributing these resources. They are copied into this module's resources,
loaded from the classpath, and the packaged background renderer remains independent of Minecraft runtime code and JARs.
The water shader does not upload or sample a Minecraft water texture. Its deep-blue stable base tint `(0.08, 0.30, 0.58)` and base opacity `0.65` come from the `WATER` material color and shader constants, while the Complementary water resources `assets/complementary/textures/cloud-water.png` (256x256) and `noise.png` (128x128) provide the animated normal and noise layers. Water absorption uses `exp(-vec3(0.22, 0.10, 0.035) * thickness)`, and the shader receives the shared noon sky color for fallback reflection. They use repeat wrapping, linear filtering, and generated mipmaps.
Each texture is decoded and uploaded once on the render thread; every handle is released there during renderer shutdown.
Water geometry retains its vertex wave and normal-layer animation without vanilla texture-frame animation.
The water quad remains a single six-vertex draw within the fixed water material batch. Its shader samples captured pre-water scene color/depth for depth-rejected refraction, Beer-Lambert water absorption, and shallow shoreline foam; it combines that transmission with the half-resolution planar reflection using Schlick water Fresnel (F0=0.02) and a restrained GGX sun highlight scaled by0.10 and capped at2.0, then applies xz-distance horizon fog and outputs an already-composited opaque pixel (`alpha=1`); other materials are not fogged. The reflection contains the sky, fixed sun, non-water scene, player, and cached cloud mesh. Reflection geometry keeps the normal `GL_CCW` front-face winding: the ordinary `lookAt` reflection view has no negative-scale determinant. It still uses a water clip plane, and state is restored to CCW with clipping disabled before the main water pass. Cloud geometry is generated once from the main camera coordinates and reused with a reflection-eye height compensation.

Terrain uses AO-modulated cool ambient light `(0.46, 0.48, 0.52)` with AO mix `0.55`, plus warm direct light `(1.10, 0.90, 0.68)` from the shared `SunState` direction. Static shadowed direct light retains a `0.22` floor through `directShadow=mix(0.22,1.0,shadowFactor)`. Static harbor geometry receives a single 1024x1024 orthographic direction-light shadow map baked during renderer initialization; its depth texture uses linear border-lit comparison and stable 3x3 PCF, while the animated boat can receive but never casts that shadow. Static cube corners carry deterministic Minecraft-style AO in the scene VBO, and lantern texels add warm HDR emission after alpha cutout. This renderer intentionally does not implement screen-space reflections (SSR), multiple G-buffers, history-frame sampling, voxel/world-space ray reflections, or full-screen underwater post-processing. The planar reflection is the supported reflection path for the fixed water plane.

## Required Invariants

- Water vertices must remain counter-clockwise when viewed from above. Back-face culling is enabled, so reversed winding makes the water disappear.
- The sun billboard basis must be derived from the eye-to-sun vector (`eye - SunState offset`) and remain camera-facing around the camera orbit.
- `SunState` must remain the only source for both sun billboard placement and terrain lighting direction; do not add a second sun position or animate it independently. The sun remains one billboard pass with half-size `SUN_BILLBOARD_HALF_SIZE=4.45`, normalized `0..1` vertex UVs, and the textured disc/HDR halo. RGB/luminance coverage, rather than the source texture alpha, determines the disc coverage so opaque black texels do not create a dark square. The sun draw uses premultiplied RGB blending and restores the default straight-alpha blend function afterward. Existing camera-track tests verify the procedural disc's 50–70px projected diameter.
- Cloud vertex normals must be passed through to the fragment shader unchanged as world-axis normals. `uSunDirection` must be uploaded before both cloud draws from the shared `SunState`; cloud lighting must precede fog, preserve straight-alpha output, and use identical world direction in the main and planar reflection passes.
- Cloud rendering must use the independent cloud fog range and color; it must not reuse `WATER_HORIZON_FOG_START`/`WATER_HORIZON_FOG_END`.
- Water must retain the deep-blue material tint, `BASE_WATER_OPACITY=0.65`, separate water horizon color, and shared `uSkyColor` fallback reflection uniform.
- The celestial pass disables depth testing and depth writes while drawing the sky/sun, then restores the renderer's expected state (depth testing, depth writes, and blending enabled).
- Cloud geometry must use the Fancy-derived 3-unit layers at height38 and 8-unit horizontal scale, with `uCloudHeight` carrying the camera-relative fractional layer offset. Preserve the 2048-coordinate wrap and all `-6..6` tile copies so coverage reaches beyond the cloud fog end.
- The cloud pass must disable culling, perform the depth-only draw with color writes disabled, perform the color+depth draw with color writes enabled, and restore culling, color writes, and depth writes afterward.
- The harbor layout must remain deterministic descriptor data in `HarborBlock`/`HarborGroup`, with irregular per-layer terrace offsets, `Foreground`, `Midground`, `Background`, left/right docks and detailed shelters, `LeftPavilion`, `CenterMarket`, and `RightLighthouse` (centered at x≈18) spanning approximately `x=-60..60`; seeded far dunes and fixed cacti remain separate data sources.
- The static scene mesh must be initialized once into one VBO and material batches reused by both the main and reflection passes. Keep the scene vertex count below `SCENE_VERTEX_BUDGET=14_000`; the boat is the only kind-2 geometry and is transformed from its local mesh by the animated boat pose centered at `BOAT_CENTER_X=-14`.
- The scene VBO layout is exactly `position3, normal3, color3, kind1, uv2, ao1` (`SCENE_VERTEX_FLOATS=13`); cloud and player strides remain unchanged. Static AABBs are collected during mesh construction for deterministic corner AO and shadow framing. The one static shadow map is owned by `SceneGlRenderer`, baked once in `initialize()` from `center - sun.direction * distance`, excludes kind-2 boat and water, and is sampled by both main and reflection terrain passes. Shadow state and framebuffer bindings must be restored after the bake, and shadow resources are deleted on the render thread during `close()`.
- Harbor structure detail counts are fixed: each dock has 21 blocks, each shelter 16, the pavilion 23, the three market stalls 39 total, and the supported lighthouse 22; the boat/player visual scale is 1.25/0.08 without changing the camera contract.
- The fixed camera contract is distance `50`, base height `8`, height swing `0.25`, target `(0, 5, -25)`, FOV `60`, and yaw sweep `0.045` radians. Keep the UI-safe central crop around the boat/player focal point.
- Each frame must follow `main HDR MSAA framebuffer bind -> pre-water scene render -> HDR color/depth capture into single-sample scene textures -> 640x360 HDR planar reflection pass -> main MSAA framebuffer rebind -> independent water render -> raw HDR resolve -> linearize -> generate mipmaps -> Bloom atlas -> Bloom/Tonemap/saturation -> final RGBA8 READ framebuffer -> BGRA glReadPixels`. Call `pixels.clear()` immediately before every `glReadPixels`.
- The MSAA framebuffer has 4x `GL_R11F_G11F_B10F` color/depth renderbuffers. The pre-water scene, reflection, and raw resolve use the same alpha-less HDR color format; depth remains DEPTH_COMPONENT24. The final post-process target is `GL_RGBA8`. Release all OpenGL framebuffers, renderbuffers, textures, programs, VAO, and VBO on the render thread.
- Close every temporary Skia `Image` in `finally` after `toComposeImageBitmap()`. Leaving it to finalization leaks native memory at frame rate.
- `active=false` must park the render thread without clearing the last published frame.
- A terminal renderer failure must be logged with its exception and must leave the UI on `bg.avif`.
- A low-performance decision is terminal for that session; subsequent `setActive(true)` calls must not restart rendering.
- OpenGL resources, the hidden window, and the GLFW lease must be released when the render loop exits.

## Activation and Fallback

The UI integration is outside this module:

- `client/ui/src/main/kotlin/calebxzau/rdi/client/ui/AppBackground.kt` opens one renderer session for the application window.
- It shows `bg.avif` before the first frame, after a renderer failure, or after low-performance fallback.
- A paused renderer keeps displaying its last successful frame.
- `client/ui/src/main/kotlin/calebxzau/rdi/client/Main.kt` activates rendering only when the window is not minimized and the current route is `Menu` or `Login`.

Do not move route, window, Compose theme, or fallback-resource policy into `:bg-renderer`.

## Low-Performance Policy

`FramePerformanceMonitor` measures the complete frame processing path: OpenGL render, MSAA resolve, synchronous readback, pixel conversion, and frame publication:

- Warm-up: `30` frames
- Sample window: `60` frames
- Fallback threshold: average frame processing time greater than `45ms`

When the threshold is reached, the renderer sets `lowPerformance=true`, logs one warning, exits the render loop, and releases its OpenGL resources. The UI then displays `bg.avif`.

Keep this decision resistant to isolated GC or driver spikes. If the policy changes, retain pure-logic tests for fast frames, isolated spikes, and sustained slow frames.

## Shared GLFW Lifecycle

`client/render-core/src/main/kotlin/calebxzau/rdi/render/GlfwRuntime.kt` coordinates GLFW across:

- `:bg-renderer`
- `:player-model`
- UI display-mode discovery

Window creation and destruction are synchronized because GLFW window hints are process-global. A renderer must acquire a lease before creating a hidden window, destroy the window before closing the lease, and release its context on its own render thread.

Be careful when changing this lifecycle: the home screen can run the background and player-model renderers at the same time.

## Validation

Run Gradle through Windows PowerShell from WSL. Do not use the WSL Gradle environment.

```powershell
Set-Location 'C:\Users\calebxzhou\Documents\coding\rdi5\client\ui'
.\gradlew.bat :render-core:test :bg-renderer:test :player-model:test :compileKotlin --no-daemon
```

Last verified on 2026-08-29:

- `pwsh.exe -NoLogo -NoProfile -Command "Set-Location 'C:\\Users\\calebxzhou\\Documents\\coding\\rdi5\\client\\ui'; .\\gradlew.bat :bg-renderer:test :bg-renderer:compileKotlin --rerun-tasks --no-daemon"`: passed with 38 tests and zero failures/errors, including the Phase1.1 water/sky/cloud/sun/terrain visual tuning contracts, HDR/Bloom/Tonemap implementation, shader-only water material checks, Phase2 harbor layout/mesh contracts, Phase2.1 irregular terrace/detail/scale assertions, and the lighthouse support continuity assertions. The static Phase2.1 scene mesh contains exactly 13,182 vertices, below the 14,000-vertex budget. Independent review identified and fixed an opaque dark sun square from alpha coverage and sun frustum clipping; tests cover RGB coverage, normalized sun UVs, premultiplied blend ordering/restoration, all camera-track billboard corners, and the 50–70px projected disc diameter. Cloud lighting uses the brighter independent cloud fog range and ambient/direct/transmission model. Phase2.1 preserves the sun disc within the camera frustum, controls the water GGX peak, verifies deterministic irregular harbor descriptors, detail counts, and lighthouse support continuity, and locks the player scale source.

- Phase1 static lighting verification: `bg-renderer:test` and `bg-renderer:compileKotlin` passed with 42 tests. Phase1.1 lighting tuning uses the approximately `48.8°` shared sun elevation, ambient `(0.46, 0.48, 0.52)` with AO mix `0.55`, and a `0.22` direct-shadow floor. The scene mesh now uses 13-float vertices with deterministic static-cube AO; its static AABBs frame one 1024x1024 depth-only shadow map baked once during `SceneGlRenderer.initialize()`. Main and planar reflection terrain passes share the map and matrix, while boat/water remain excluded from casting and dynamic animation contracts are unchanged. CPU-only tests cover isolated/side/double-side AO, AO ranges and dynamic AO=1, production shadow-frame corner containment, shadow shader alpha cutouts/kind exclusion, PCF/direct-only sampling, lantern HDR emission, and complete shadow-bake GL state snapshot/restore ordering.

The depth/refraction, planar-reflection, Fresnel/GGX, clipping, cache-reuse, HDR format, Bloom source, and Tonemap ordering contracts are covered by GPU-independent shader-source and scene-math tests. The current automated validation does not create a real OpenGL context; actual GPU FBO completeness, shader compilation, visual Bloom/Tonemap quality, and complete pass execution/performance remain unverified until the client is launched on a desktop GPU.

No real desktop GPU visual or performance validation has been completed. The next rendering change should launch the actual client and verify water visibility, fixed-noon sun/cloud composition behind the home UI, pause/resume, fallback behavior, 4xMSAA load, and long-running native memory stability.

## Known Tradeoffs

- Readback is currently synchronous `glReadPixels`, not PBO-based.
- 4xMSAA is fixed and has a color/depth resolve cost on every frame.
- Clouds are a standalone Fancy-derived geometry port using Mojang's bundled texture, not a real world/cloud renderer; 169 protection tiles increase geometry work.
- Geometry emits all cube faces, including hidden internal faces; the scene is intentionally small enough for now.
- The framebuffer and output are fixed at `1280x720`.
- Low-performance fallback is terminal for the session and has no user-facing toggle; renderer failure likewise leaves the session on the static fallback.
- The static fallback remains `client/ui/assets/src/main/resources/assets/bg.avif`.
