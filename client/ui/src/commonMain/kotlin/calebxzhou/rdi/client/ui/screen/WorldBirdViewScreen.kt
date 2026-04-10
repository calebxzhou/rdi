package calebxzhou.rdi.client.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Tab
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import calebxzhou.rdi.client.service.WorldBirdViewSourceSpec
import calebxzhou.rdi.client.service.createWorldBirdViewDataSource
import calebxzhou.rdi.client.ui.*
import calebxzhou.rdi.client.ui.comp.WorldMap
import calebxzhou.rdi.common.model.BlockColors
import calebxzhou.rdi.common.model.World
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldBirdViewScreen(
    sourceSpec: WorldBirdViewSourceSpec,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val source = remember(sourceSpec.sourceKey) { createWorldBirdViewDataSource(sourceSpec) }
    val sourceKey = source?.sourceKey ?: sourceSpec.sourceKey
    var scale by remember { mutableStateOf(World.Scale.L2) }
    var zoomLevel by remember { mutableStateOf(World.Scale.L2.level.toFloat()) }
    var centerChunkXFloat by remember { mutableStateOf(0f) }
    var centerChunkZFloat by remember { mutableStateOf(0f) }
    var centerChunkX by remember { mutableStateOf(0) }
    var centerChunkZ by remember { mutableStateOf(0) } 
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var dimensions by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedDimension by remember { mutableStateOf("minecraft:overworld") }
    var error by remember { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val loadingChunkRanges = remember { mutableStateListOf<ChunkRange>() }
    val loadingRegionRanges = remember { mutableStateListOf<RegionRange>() }
    val loadingJobs = remember { hashMapOf<String, Job>() }
    var activeLoadCount by remember { mutableStateOf(0) }
    var loadEpoch by remember { mutableStateOf(0) }
    var cacheVersion by remember { mutableStateOf(0) }
    var sweepPhase by remember { mutableStateOf(0) }
    var hoverChunk by remember { mutableStateOf<ChunkCoord?>(null) }
    val latestZoomLevel by rememberUpdatedState(zoomLevel)

    val chunkCacheByScale = remember { hashMapOf<Int, HashMap<Long, SurfaceChunkRender>>() }
    val regionCacheByScale = remember { hashMapOf<Int, HashMap<Long, SurfaceRegionRender>>() }
    val knownMissingByScale = remember { hashMapOf<Int, HashSet<Long>>() }
    val knownMissingRegionsByScale = remember { hashMapOf<Int, HashSet<Long>>() }
    val blockColorCache = remember { mutableMapOf<String, Int>() }

    fun chunkCacheOf(level: Int): HashMap<Long, SurfaceChunkRender> =
        chunkCacheByScale.getOrPut(level) { hashMapOf() }
    fun regionCacheOf(level: Int): HashMap<Long, SurfaceRegionRender> =
        regionCacheByScale.getOrPut(level) { hashMapOf() }
    fun knownMissingOf(level: Int): HashSet<Long> =
        knownMissingByScale.getOrPut(level) { hashSetOf() }
    fun knownMissingRegionsOf(level: Int): HashSet<Long> =
        knownMissingRegionsByScale.getOrPut(level) { hashSetOf() }

    fun refreshActiveLoadCount() {
        activeLoadCount = loadingJobs.values.count { it.isActive }
    }

    fun cancelAllLoads() {
        loadingJobs.values.toList().forEach { it.cancel() }
        loadingJobs.clear()
        loadingChunkRanges.clear()
        loadingRegionRanges.clear()
        refreshActiveLoadCount()
    }

    fun setZoomLevel(newZoomLevel: Float) {
        val clamped = newZoomLevel.coerceIn(World.Scale.L0.level.toFloat(), World.Scale.L7.level.toFloat())
        zoomLevel = clamped
        val targetScale = World.Scale.fromLevel(clamped.roundToInt())
        if (targetScale != scale) {
            cancelAllLoads()
            scale = targetScale
        }
    }

    fun setCenter(chunkX: Float, chunkZ: Float) {
        val clampedX = clampChunkCoordFloat(chunkX)
        val clampedZ = clampChunkCoordFloat(chunkZ)
        centerChunkXFloat = clampedX
        centerChunkZFloat = clampedZ
        centerChunkX = clampedX.roundToInt()
        centerChunkZ = clampedZ.roundToInt()
    }

    fun moveCenter(dx: Int, dz: Int) {
        setCenter(centerChunkXFloat + dx, centerChunkZFloat + dz)
    }

    fun panByPixels(delta: Offset) {
        val metrics = computeViewportMetrics(
            scale = scale,
            viewportSize = viewportSize,
            centerChunkX = centerChunkXFloat,
            centerChunkZ = centerChunkZFloat,
            zoomLevel = zoomLevel
        ) ?: return
        val deltaChunkX = (delta.x / metrics.pixelsPerBlock) / CHUNK_SIDE_BLOCKS
        val deltaChunkZ = (delta.y / metrics.pixelsPerBlock) / CHUNK_SIDE_BLOCKS
        setCenter(centerChunkXFloat - deltaChunkX, centerChunkZFloat - deltaChunkZ)
    }

    fun clearSurfaceCaches() {
        chunkCacheByScale.clear()
        regionCacheByScale.clear()
        knownMissingByScale.clear()
        knownMissingRegionsByScale.clear()
        cancelAllLoads()
        hoverChunk = null
        loadEpoch++
        cacheVersion++
        sweepPhase = 0
    }

    fun updateHoverChunk(pointerPosition: Offset) {
        val metrics = computeViewportMetrics(
            scale = scale,
            viewportSize = viewportSize,
            centerChunkX = centerChunkXFloat,
            centerChunkZ = centerChunkZFloat,
            zoomLevel = zoomLevel
        ) ?: run {
            hoverChunk = null
            return
        }
        val blockX = metrics.viewportLeftBlockX + (pointerPosition.x / metrics.pixelsPerBlock)
        val blockZ = metrics.viewportTopBlockZ + (pointerPosition.y / metrics.pixelsPerBlock)
        hoverChunk = ChunkCoord(
            x = floor(blockX / CHUNK_SIDE_BLOCKS).toInt(),
            z = floor(blockZ / CHUNK_SIDE_BLOCKS).toInt()
        )
    }

    fun loadDimensions() {
        val activeSource = source ?: run {
            dimensions = emptyList()
            error = "当前平台不支持打开本地存档"
            return
        }
        scope.launch {
            try {
                val loaded = activeSource.listDimensions().distinct()
                dimensions = loaded
                if (loaded.isEmpty()) {
                    error = "该房间没有可用维度数据"
                    return@launch
                }
                if (selectedDimension !in loaded) {
                    selectedDimension = loaded.first()
                }
                error = null
            } catch (t: Throwable) {
                dimensions = emptyList()
                error = "加载维度失败: ${t.message}"
            }
        }
    }

    fun requestBuildAllSurfaceCaches() {
        val activeSource = source ?: run {
            error = "当前平台不支持打开本地存档"
            return
        }
        scope.launch {
            try {
                val message = activeSource.buildAllSurfaceCaches()
                clearSurfaceCaches()
                error = message
            } catch (t: Throwable) {
                error = "提交房间缓存构建失败: ${t.message ?: "请求失败"}"
            }
        }
    }

    fun requestChunkRange(range: ChunkRange): Boolean {
        if (loadingChunkRanges.any { it == range }) return false

        val activeSource = source ?: run {
            error = "当前平台不支持打开本地存档"
            return false
        }
        val requestScaleLevel = scale.level
        val chunkCache = chunkCacheOf(requestScaleLevel)
        val knownMissing = knownMissingOf(requestScaleLevel)
        val requestKey = buildString {
            append("chunk:")
            append(selectedDimension)
            append(':')
            append(requestScaleLevel)
            append(':')
            append(range.minX)
            append(':')
            append(range.maxX)
            append(':')
            append(range.minZ)
            append(':')
            append(range.maxZ)
        }
        if (loadingJobs.containsKey(requestKey)) return false
        loadingChunkRanges += range
        val job = scope.launch {
            try {
                val dto = activeSource.querySurface(
                    dimension = selectedDimension,
                    scale = World.Scale.fromLevel(requestScaleLevel),
                    chunksRaw = "${range.minX}..${range.maxX}/${range.minZ}..${range.maxZ}"
                )
                val paletteColors = IntArray(dto.palette.size) { paletteIndex ->
                    val blockId = dto.palette.getOrElse(paletteIndex) { "minecraft:air" }
                    blockColorCache.getOrPut(blockId) {
                        parseMapHexColorInt(BlockColors.toColorHex(blockId))
                    }
                }

                var cacheChanged = false
                val returned = HashSet<Long>(dto.chunks.size)
                dto.chunks.forEach { chunkDto ->
                    val chunkX = chunkDto.chunkX
                    val chunkZ = chunkDto.chunkZ
                    val key = chunkCoordKey(chunkX, chunkZ)
                    val colors = IntArray(chunkDto.data.size) { idx ->
                        val paletteIndex = chunkDto.data.getOrElse(idx) { 0 }
                        if (paletteIndex in paletteColors.indices) paletteColors[paletteIndex] else 0x00000000
                    }
                    chunkCache[key] = SurfaceChunkRender(side = chunkDto.side.coerceAtLeast(1), colors = colors)
                    cacheChanged = true
                    if (knownMissing.remove(key)) {
                        cacheChanged = true
                    }
                    returned += key
                }

                val missingFromServer = HashSet<Long>(dto.missing.size)
                dto.missing.forEach { coord ->
                    val key = chunkCoordKey(coord.chunkX, coord.chunkZ)
                    missingFromServer += key
                    if (!chunkCache.containsKey(key) && knownMissing.add(key)) {
                        cacheChanged = true
                    }
                }

                for (z in range.minZ..range.maxZ) {
                    for (x in range.minX..range.maxX) {
                        val key = chunkCoordKey(x, z)
                        if (!returned.contains(key) && !chunkCache.containsKey(key) && !missingFromServer.contains(key)) {
                            if (knownMissing.add(key)) {
                                cacheChanged = true
                            }
                        }
                    }
                }
                if (cacheChanged) {
                    cacheVersion++
                }
                error = null
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) return@launch
                error = "加载俯视图失败: ${throwable.message ?: "请求失败"}"
            } finally {
                loadingChunkRanges.remove(range)
                loadingJobs.remove(requestKey)
                refreshActiveLoadCount()
                loadEpoch++
                sweepPhase++
            }
        }
        loadingJobs[requestKey] = job
        refreshActiveLoadCount()
        return true
    }

    fun requestRegionRange(range: RegionRange): Boolean {
        if (loadingRegionRanges.any { it == range }) return false

        val activeSource = source ?: run {
            error = "当前平台不支持打开本地存档"
            return false
        }
        val requestScaleLevel = scale.level
        val regionCache = regionCacheOf(requestScaleLevel)
        val knownMissingRegions = knownMissingRegionsOf(requestScaleLevel)
        val requestKey = buildString {
            append("region:")
            append(selectedDimension)
            append(':')
            append(requestScaleLevel)
            append(':')
            append(range.minX)
            append(':')
            append(range.maxX)
            append(':')
            append(range.minZ)
            append(':')
            append(range.maxZ)
        }
        if (loadingJobs.containsKey(requestKey)) return false
        loadingRegionRanges += range
        val job = scope.launch {
            try {
                val dto = activeSource.querySurface(
                    dimension = selectedDimension,
                    scale = World.Scale.fromLevel(requestScaleLevel),
                    regionsRaw = "${range.minX}..${range.maxX}/${range.minZ}..${range.maxZ}"
                )
                val paletteColors = IntArray(dto.palette.size) { paletteIndex ->
                    val blockId = dto.palette.getOrElse(paletteIndex) { "minecraft:air" }
                    blockColorCache.getOrPut(blockId) {
                        parseMapHexColorInt(BlockColors.toColorHex(blockId))
                    }
                }

                var cacheChanged = false
                val returned = HashSet<Long>(dto.regions.size)
                dto.regions.forEach { regionDto ->
                    val regionX = regionDto.regionX
                    val regionZ = regionDto.regionZ
                    val key = regionCoordKey(regionX, regionZ)
                    val colors = IntArray(regionDto.data.size) { idx ->
                        val paletteIndex = regionDto.data.getOrElse(idx) { 0 }
                        if (paletteIndex in paletteColors.indices) paletteColors[paletteIndex] else 0x00000000
                    }
                    regionCache[key] = SurfaceRegionRender(
                        side = regionDto.side.coerceAtLeast(1),
                        colors = colors
                    )
                    cacheChanged = true
                    if (knownMissingRegions.remove(key)) {
                        cacheChanged = true
                    }
                    returned += key
                }

                val missingFromServer = HashSet<Long>(dto.missingRegions.size)
                dto.missingRegions.forEach { region ->
                    val key = regionCoordKey(region.regionX, region.regionZ)
                    missingFromServer += key
                    if (!regionCache.containsKey(key) && knownMissingRegions.add(key)) {
                        cacheChanged = true
                    }
                }

                for (regionZ in range.minZ..range.maxZ) {
                    for (regionX in range.minX..range.maxX) {
                        val key = regionCoordKey(regionX, regionZ)
                        if (!returned.contains(key) && !regionCache.containsKey(key) && !missingFromServer.contains(key)) {
                            if (knownMissingRegions.add(key)) {
                                cacheChanged = true
                            }
                        }
                    }
                }
                if (cacheChanged) {
                    cacheVersion++
                }
                error = null
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) return@launch
                error = "加载俯视图失败: ${throwable.message ?: "请求失败"}"
            } finally {
                loadingRegionRanges.remove(range)
                loadingJobs.remove(requestKey)
                refreshActiveLoadCount()
                loadEpoch++
                sweepPhase++
            }
        }
        loadingJobs[requestKey] = job
        refreshActiveLoadCount()
        return true
    }

    LaunchedEffect(sourceKey) {
        clearSurfaceCaches()
        error = null
        setCenter(0f, 0f)
        setZoomLevel(World.Scale.L2.level.toFloat())
        loadDimensions()
    }

    LaunchedEffect(selectedDimension) {
        clearSurfaceCaches()
    }

    DisposableEffect(Unit) {
        onDispose {
            cancelAllLoads()
        }
    }

    LaunchedEffect(dragging) {
        if (dragging) {
            cancelAllLoads()
        }
    }

    LaunchedEffect(sourceKey, selectedDimension, scale, zoomLevel, centerChunkXFloat, centerChunkZFloat, viewportSize, dimensions, dragging, loadEpoch) {
        if (dimensions.isEmpty()) return@LaunchedEffect
        if (dragging) return@LaunchedEffect
        val visibleRange = computeVisibleChunkRange(
            scale = scale,
            viewportSize = viewportSize,
            centerChunkX = centerChunkXFloat,
            centerChunkZ = centerChunkZFloat,
            zoomLevel = zoomLevel,
            preloadChunks = 0
        ) ?: return@LaunchedEffect

        if (scale.level >= World.Scale.L5.level) {
            val parallelSlots = (MAX_PARALLEL_REGION_REQUESTS - loadingRegionRanges.size).coerceAtLeast(0)
            if (parallelSlots <= 0) return@LaunchedEffect
            val regionCache = regionCacheOf(scale.level)
            val knownMissingRegions = knownMissingRegionsOf(scale.level)
            val visibleRegionRange = visibleRange.toRegionRange()
            repeat(parallelSlots) {
                val missingRegions = collectMissingRegions(
                    range = visibleRegionRange,
                    cache = regionCache,
                    knownMissing = knownMissingRegions,
                    currentLoadingRanges = loadingRegionRanges.toList()
                )
                if (missingRegions.isEmpty()) return@repeat
                val requestRange = pickRegionRequestRange(
                    visibleRange = visibleRegionRange,
                    missingCoords = missingRegions,
                    preferHorizontal = (sweepPhase % 2 == 0)
                ) ?: return@repeat
                if (requestRegionRange(requestRange)) {
                    sweepPhase++
                }
            }
        } else {
            val parallelSlots = (MAX_PARALLEL_CHUNK_REQUESTS - loadingChunkRanges.size).coerceAtLeast(0)
            if (parallelSlots <= 0) return@LaunchedEffect
            val chunkCache = chunkCacheOf(scale.level)
            val knownMissing = knownMissingOf(scale.level)
            repeat(parallelSlots) {
                val missingCoords = collectMissingChunks(
                    range = visibleRange,
                    scale = scale,
                    cache = chunkCache,
                    knownMissing = knownMissing,
                    currentLoadingRanges = loadingChunkRanges.toList()
                )
                if (missingCoords.isEmpty()) return@repeat
                val requestRange = pickRequestRange(
                    visibleRange = visibleRange,
                    missingCoords = missingCoords,
                    scale = scale,
                    preferHorizontal = (sweepPhase % 2 == 0)
                ) ?: return@repeat
                if (requestChunkRange(requestRange)) {
                    sweepPhase++
                }
            }
        }
    }

    MainBox {
        MainColumn {
            TitleRow("区块俯视图", onBack = onBack) {
                error?.let { ErrorText(it) }
                Space8w()
                val hoverChunkText = hoverChunk
                Text(
                    if (hoverChunkText == null) "区块 (--, --)"
                    else "区块(${hoverChunkText.x},${hoverChunkText.z})"
                )
                Space8w()
                val blocksPerPixel = ((2f.pow(zoomLevel) * 100f).roundToInt() / 100f)
                Text("比例尺 1:${blocksPerPixel}")
                Space8w()
                if (source?.supportsBuildAllSurfaceCaches == true) {
                    CircleIconButton("\uDB83\uDCBD", "构建房间缓存") { requestBuildAllSurfaceCaches() }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (dimensions.isNotEmpty()) {
                val selectedTabIndex = dimensions.indexOf(selectedDimension).coerceAtLeast(0)
                ScrollableTabRow(selectedTabIndex = selectedTabIndex) {
                    dimensions.forEachIndexed { index, dimension ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedDimension = dimension },
                            text = { Text(dimensionLabel(dimension)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .border(1.dp, Color(0xFFCCCCCC))
                    .background(Color(0xFFEFEFEF))
                    .clipToBounds()
                    .onSizeChanged { viewportSize = it }
            ) {
                BirdSurfaceMap(
                    scale = scale,
                    zoomLevel = zoomLevel,
                    centerChunkX = centerChunkXFloat,
                    centerChunkZ = centerChunkZFloat,
                    viewportSize = viewportSize,
                    chunkCache = chunkCacheOf(scale.level),
                    regionCache = regionCacheOf(scale.level),
                    cacheVersion = cacheVersion,
                    hoverChunk = hoverChunk,
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(sourceKey, selectedDimension) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: continue
                                    val wheel = change.scrollDelta.y
                                    if (wheel != 0f) {
                                        val zoomDelta = wheel * 0.1f
                                        setZoomLevel(latestZoomLevel + zoomDelta)
                                    }
                                }
                            }
                        }
                        .pointerInput(sourceKey, selectedDimension) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                if (pan != Offset.Zero) {
                                    panByPixels(pan)
                                }
                                if (zoom > 0f && zoom != 1f && zoom.isFinite()) {
                                    val zoomDelta = log2(zoom) * PINCH_ZOOM_SENSITIVITY
                                    setZoomLevel(latestZoomLevel - zoomDelta)
                                }
                                updateHoverChunk(centroid)
                            }
                        }
                        .pointerInput(sourceKey, selectedDimension) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: continue
                                    dragging = event.changes.any { it.pressed }
                                    updateHoverChunk(change.position)
                                }
                            }
                        }
                )

                /*if (activeLoadCount > 0) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        androidx.compose.material.CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("并行加载 $activeLoadCount")
                        loadingChunkRanges.firstOrNull()?.let {
                            Text("区块 ${it.minX}..${it.maxX} / ${it.minZ}..${it.maxZ}")
                        }
                        loadingRegionRanges.firstOrNull()?.let {
                            Text("区域 ${it.minX}..${it.maxX} / ${it.minZ}..${it.maxZ}")
                        }
                    }
                }*/
            }
        }
    }
}

@Composable
private fun BirdSurfaceMap(
    scale: World.Scale,
    zoomLevel: Float,
    centerChunkX: Float,
    centerChunkZ: Float,
    viewportSize: IntSize,
    chunkCache: Map<Long, SurfaceChunkRender>,
    regionCache: Map<Long, SurfaceRegionRender>,
    cacheVersion: Int,
    hoverChunk: ChunkCoord?,
    modifier: Modifier = Modifier
) {
    val viewportMetrics = remember(scale, zoomLevel, centerChunkX, centerChunkZ, viewportSize) {
        computeViewportMetrics(
            scale = scale,
            viewportSize = viewportSize,
            centerChunkX = centerChunkX,
            centerChunkZ = centerChunkZ,
            zoomLevel = zoomLevel
        )
    }
    val visibleRange = remember(scale, zoomLevel, centerChunkX, centerChunkZ, viewportSize) {
        computeVisibleChunkRange(
            scale = scale,
            viewportSize = viewportSize,
            centerChunkX = centerChunkX,
            centerChunkZ = centerChunkZ,
            zoomLevel = zoomLevel,
            preloadChunks = 0
        )
    }
    val cellsPerChunk = remember(scale) {
        (16 / (1 shl scale.level)).coerceAtLeast(1)
    }
    val raster = remember(cacheVersion, visibleRange, cellsPerChunk) {
        visibleRange?.let { range ->
            buildVisibleMapRaster(
                range = range,
                cellsPerChunk = cellsPerChunk,
                chunkCache = chunkCache,
                regionCache = regionCache,
                scale = scale
            )
        }
    }
    val resolvedRange = visibleRange
    val resolvedRaster = raster
    if (resolvedRange == null || resolvedRaster == null || resolvedRaster.width <= 0 || resolvedRaster.height <= 0) {
        Box(modifier = modifier.background(Color(0x00000000)))
        return
    }
    val mapShift = viewportMetrics?.let { metrics ->
        val rasterLeftBlock = resolvedRange.minX * CHUNK_SIDE_BLOCKS
        val rasterTopBlock = resolvedRange.minZ * CHUNK_SIDE_BLOCKS
        Offset(
            x = (rasterLeftBlock - metrics.viewportLeftBlockX) * metrics.pixelsPerBlock,
            y = (rasterTopBlock - metrics.viewportTopBlockZ) * metrics.pixelsPerBlock
        )
    } ?: Offset.Zero

    // Render the visible map as a bitmap-backed WorldMap (single drawImage call).
    Box(modifier = modifier) {
        WorldMap(
            pixels = resolvedRaster.pixels,
            mapWidth = resolvedRaster.width,
            mapHeight = resolvedRaster.height,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = mapShift.x
                    translationY = mapShift.y
                },
            pixelsVersion = cacheVersion xor resolvedRaster.versionSeed,
            minZoom = 1f,
            maxZoom = 1f,
            backgroundColor = Color(0x00000000),
            gesturesEnabled = false
        )
        if (hoverChunk != null && hoverChunk.x in resolvedRange.minX..resolvedRange.maxX && hoverChunk.z in resolvedRange.minZ..resolvedRange.maxZ) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        translationX = mapShift.x
                        translationY = mapShift.y
                    }
            ) {
                val mapWidth = resolvedRaster.width.toFloat()
                val mapHeight = resolvedRaster.height.toFloat()
                if (mapWidth <= 0f || mapHeight <= 0f) return@Canvas

                val fit = min(size.width / mapWidth, size.height / mapHeight)
                if (fit <= 0f) return@Canvas

                val drawW = (mapWidth * fit).roundToInt().coerceAtLeast(1).toFloat()
                val drawH = (mapHeight * fit).roundToInt().coerceAtLeast(1).toFloat()
                val drawX = ((size.width - drawW) / 2f).roundToInt().toFloat()
                val drawY = ((size.height - drawH) / 2f).roundToInt().toFloat()

                val cellPxX = drawW / mapWidth
                val cellPxY = drawH / mapHeight
                val chunkSpanX = cellsPerChunk * cellPxX
                val chunkSpanY = cellsPerChunk * cellPxY

                val regionMinChunkX = Math.floorDiv(hoverChunk.x, CHUNKS_PER_REGION_SIDE) * CHUNKS_PER_REGION_SIDE
                val regionMinChunkZ = Math.floorDiv(hoverChunk.z, CHUNKS_PER_REGION_SIDE) * CHUNKS_PER_REGION_SIDE
                val regionOffsetX = regionMinChunkX - resolvedRange.minX
                val regionOffsetZ = regionMinChunkZ - resolvedRange.minZ
                val regionLeft = drawX + regionOffsetX * chunkSpanX
                val regionTop = drawY + regionOffsetZ * chunkSpanY
                val regionWidth = CHUNKS_PER_REGION_SIDE * chunkSpanX
                val regionHeight = CHUNKS_PER_REGION_SIDE * chunkSpanY

                drawRect(
                    color = Color(0xFF81D4FA),
                    topLeft = Offset(regionLeft, regionTop),
                    size = androidx.compose.ui.geometry.Size(regionWidth, regionHeight),
                    style = Stroke(width = 1.5f)
                )

                val chunkOffsetX = hoverChunk.x - resolvedRange.minX
                val chunkOffsetZ = hoverChunk.z - resolvedRange.minZ
                val left = drawX + chunkOffsetX * chunkSpanX
                val top = drawY + chunkOffsetZ * chunkSpanY

                drawRect(
                    color = Color(0xFFFF3D00),
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(chunkSpanX, chunkSpanY),
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

private fun buildVisibleMapRaster(
    range: ChunkRange,
    cellsPerChunk: Int,
    chunkCache: Map<Long, SurfaceChunkRender>,
    regionCache: Map<Long, SurfaceRegionRender>,
    scale: World.Scale
): VisibleMapRaster {
    val chunkCountX = (range.maxX - range.minX + 1).coerceAtLeast(1)
    val chunkCountZ = (range.maxZ - range.minZ + 1).coerceAtLeast(1)
    val width = (chunkCountX * cellsPerChunk).coerceAtLeast(1)
    val height = (chunkCountZ * cellsPerChunk).coerceAtLeast(1)
    val pixels = IntArray(width * height)

    if (scale.level >= World.Scale.L5.level) {
        blitRegionsToRaster(
            range = range,
            scale = scale,
            cellsPerChunk = cellsPerChunk,
            regionCache = regionCache,
            dst = pixels,
            dstWidth = width,
            dstHeight = height
        )
        return VisibleMapRaster(
            width = width,
            height = height,
            pixels = pixels,
            versionSeed = range.hashCode() xor (cellsPerChunk shl 24) xor (scale.level shl 28)
        )
    }

    for (chunkZ in range.minZ..range.maxZ) {
        for (chunkX in range.minX..range.maxX) {
            val chunk = chunkCache[chunkCoordKey(chunkX, chunkZ)] ?: continue
            val baseX = (chunkX - range.minX) * cellsPerChunk
            val baseZ = (chunkZ - range.minZ) * cellsPerChunk
            blitChunkToRaster(
                chunk = chunk,
                dst = pixels,
                dstWidth = width,
                dstX = baseX,
                dstY = baseZ,
                dstSide = cellsPerChunk
            )
        }
    }

    return VisibleMapRaster(
        width = width,
        height = height,
        pixels = pixels,
        versionSeed = range.hashCode() xor (cellsPerChunk shl 24)
    )
}

private fun blitRegionsToRaster(
    range: ChunkRange,
    scale: World.Scale,
    cellsPerChunk: Int,
    regionCache: Map<Long, SurfaceRegionRender>,
    dst: IntArray,
    dstWidth: Int,
    dstHeight: Int
) {
    val chunkGroup = chunkGroupSizeForWideScale(scale)
    val expectedSide = (CHUNKS_PER_REGION_SIDE / chunkGroup).coerceAtLeast(1)

    val visibleMinRegionX = Math.floorDiv(range.minX, CHUNKS_PER_REGION_SIDE)
    val visibleMaxRegionX = Math.floorDiv(range.maxX, CHUNKS_PER_REGION_SIDE)
    val visibleMinRegionZ = Math.floorDiv(range.minZ, CHUNKS_PER_REGION_SIDE)
    val visibleMaxRegionZ = Math.floorDiv(range.maxZ, CHUNKS_PER_REGION_SIDE)

    for (regionZ in visibleMinRegionZ..visibleMaxRegionZ) {
        for (regionX in visibleMinRegionX..visibleMaxRegionX) {
            val regionRender = regionCache[regionCoordKey(regionX, regionZ)] ?: continue
            val regionBaseChunkX = regionX * CHUNKS_PER_REGION_SIDE
            val regionBaseChunkZ = regionZ * CHUNKS_PER_REGION_SIDE
            val regionEndChunkX = regionBaseChunkX + CHUNKS_PER_REGION_SIDE - 1
            val regionEndChunkZ = regionBaseChunkZ + CHUNKS_PER_REGION_SIDE - 1
            if (regionEndChunkX < range.minX || regionBaseChunkX > range.maxX ||
                regionEndChunkZ < range.minZ || regionBaseChunkZ > range.maxZ
            ) {
                continue
            }

            val side = regionRender.side.coerceAtLeast(1)
            val colors = regionRender.colors
            val safeSide = min(side, expectedSide)
            if (colors.size < safeSide * safeSide) continue
            val blockSpanChunks = max(1, CHUNKS_PER_REGION_SIDE / safeSide)
            val blockSpanPixels = blockSpanChunks * cellsPerChunk

            for (localZ in 0 until safeSide) {
                val worldChunkStartZ = regionBaseChunkZ + localZ * blockSpanChunks
                val dstStartZ = (worldChunkStartZ - range.minZ) * cellsPerChunk
                if (dstStartZ >= dstHeight || dstStartZ + blockSpanPixels <= 0) continue
                for (localX in 0 until safeSide) {
                    val color = colors[localZ * safeSide + localX]
                    if (color ushr 24 == 0) continue

                    val worldChunkStartX = regionBaseChunkX + localX * blockSpanChunks
                    val dstStartX = (worldChunkStartX - range.minX) * cellsPerChunk
                    if (dstStartX >= dstWidth || dstStartX + blockSpanPixels <= 0) continue

                    val x0 = dstStartX.coerceAtLeast(0)
                    val y0 = dstStartZ.coerceAtLeast(0)
                    val x1 = (dstStartX + blockSpanPixels).coerceAtMost(dstWidth)
                    val y1 = (dstStartZ + blockSpanPixels).coerceAtMost(dstHeight)
                    for (py in y0 until y1) {
                        val row = py * dstWidth
                        for (px in x0 until x1) {
                            dst[row + px] = color
                        }
                    }
                }
            }
        }
    }
}

private fun blitChunkToRaster(
    chunk: SurfaceChunkRender,
    dst: IntArray,
    dstWidth: Int,
    dstX: Int,
    dstY: Int,
    dstSide: Int
) {
    val srcSide = chunk.side.coerceAtLeast(1)
    val srcColors = chunk.colors
    if (srcColors.size < srcSide * srcSide) return

    if (srcSide == dstSide) {
        for (row in 0 until dstSide) {
            val srcRowStart = row * srcSide
            val dstRowStart = (dstY + row) * dstWidth + dstX
            for (col in 0 until dstSide) {
                dst[dstRowStart + col] = srcColors[srcRowStart + col]
            }
        }
        return
    }

    for (row in 0 until dstSide) {
        val srcRow = ((row.toLong() * srcSide) / dstSide).toInt().coerceIn(0, srcSide - 1)
        val srcRowStart = srcRow * srcSide
        val dstRowStart = (dstY + row) * dstWidth + dstX
        for (col in 0 until dstSide) {
            val srcCol = ((col.toLong() * srcSide) / dstSide).toInt().coerceIn(0, srcSide - 1)
            dst[dstRowStart + col] = srcColors[srcRowStart + srcCol]
        }
    }
}

private fun computeVisibleChunkRange(
    scale: World.Scale,
    viewportSize: IntSize,
    centerChunkX: Float,
    centerChunkZ: Float,
    zoomLevel: Float,
    preloadChunks: Int
): ChunkRange? {
    val metrics = computeViewportMetrics(
        scale = scale,
        viewportSize = viewportSize,
        centerChunkX = centerChunkX,
        centerChunkZ = centerChunkZ,
        zoomLevel = zoomLevel
    ) ?: return null

    val baseMinX = floor(metrics.viewportLeftBlockX / CHUNK_SIDE_BLOCKS).toInt() - preloadChunks
    val baseMinZ = floor(metrics.viewportTopBlockZ / CHUNK_SIDE_BLOCKS).toInt() - preloadChunks
    val visibleChunkCountX = ceil(metrics.visibleBlocksX / CHUNK_SIDE_BLOCKS).toInt()
        .coerceAtLeast(1) + preloadChunks * 2 + 1
    val visibleChunkCountZ = ceil(metrics.visibleBlocksZ / CHUNK_SIDE_BLOCKS).toInt()
        .coerceAtLeast(1) + preloadChunks * 2 + 1
    val boundedX = makeBoundedRange(baseMinX, visibleChunkCountX, Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    val boundedZ = makeBoundedRange(baseMinZ, visibleChunkCountZ, Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

    return ChunkRange(
        minX = boundedX.first,
        maxX = boundedX.last,
        minZ = boundedZ.first,
        maxZ = boundedZ.last
    )
}

private fun makeBoundedRange(start: Int, size: Int, minBound: Int, maxBound: Int): IntRange {
    val safeSize = size.coerceAtLeast(1)
    var minValue = start
    var maxValue = minValue + safeSize - 1
    if (minValue < minBound) {
        minValue = minBound
        maxValue = minValue + safeSize - 1
    }
    if (maxValue > maxBound) {
        maxValue = maxBound
        minValue = (maxValue - safeSize + 1).coerceAtLeast(minBound)
    }
    return minValue..maxValue
}

private fun ChunkRange.toRegionRange(): RegionRange {
    return RegionRange(
        minX = Math.floorDiv(minX, CHUNKS_PER_REGION_SIDE),
        maxX = Math.floorDiv(maxX, CHUNKS_PER_REGION_SIDE),
        minZ = Math.floorDiv(minZ, CHUNKS_PER_REGION_SIDE),
        maxZ = Math.floorDiv(maxZ, CHUNKS_PER_REGION_SIDE)
    )
}

private fun collectMissingRegions(
    range: RegionRange,
    cache: Map<Long, SurfaceRegionRender>,
    knownMissing: Set<Long>,
    currentLoadingRanges: List<RegionRange>
): List<RegionCoord> {
    val missing = ArrayList<RegionCoord>()
    for (regionZ in range.minZ..range.maxZ) {
        for (regionX in range.minX..range.maxX) {
            if (currentLoadingRanges.any { it.contains(regionX, regionZ) }) continue
            val key = regionCoordKey(regionX, regionZ)
            if (cache.containsKey(key)) continue
            if (key in knownMissing) continue
            missing += RegionCoord(regionX, regionZ)
            if (missing.size >= MAX_MISSING_REGION_SCAN_BATCH) return missing
        }
    }
    return missing
}

private fun pickRegionRequestRange(
    visibleRange: RegionRange,
    missingCoords: List<RegionCoord>,
    preferHorizontal: Boolean
): RegionRange? {
    if (missingCoords.isEmpty()) return null
    val centerX = (visibleRange.minX + visibleRange.maxX) / 2
    val centerZ = (visibleRange.minZ + visibleRange.maxZ) / 2
    val anchor = missingCoords.minByOrNull { abs(it.x - centerX) + abs(it.z - centerZ) } ?: return null

    val visibleWidth = (visibleRange.maxX - visibleRange.minX + 1).coerceAtLeast(1)
    val visibleHeight = (visibleRange.maxZ - visibleRange.minZ + 1).coerceAtLeast(1)
    val maxStripWidth = max(1, MAX_REQUEST_REGIONS_PER_BATCH / visibleHeight.coerceAtLeast(1))
    val maxStripHeight = max(1, MAX_REQUEST_REGIONS_PER_BATCH / visibleWidth.coerceAtLeast(1))

    return if (preferHorizontal) {
        val half = maxStripHeight / 2
        var minZ = (anchor.z - half).coerceIn(visibleRange.minZ, visibleRange.maxZ)
        var maxZ = (minZ + maxStripHeight - 1).coerceAtMost(visibleRange.maxZ)
        minZ = (maxZ - maxStripHeight + 1).coerceAtLeast(visibleRange.minZ)
        RegionRange(
            minX = visibleRange.minX,
            maxX = visibleRange.maxX,
            minZ = minZ,
            maxZ = maxZ
        )
    } else {
        val half = maxStripWidth / 2
        var minX = (anchor.x - half).coerceIn(visibleRange.minX, visibleRange.maxX)
        var maxX = (minX + maxStripWidth - 1).coerceAtMost(visibleRange.maxX)
        minX = (maxX - maxStripWidth + 1).coerceAtLeast(visibleRange.minX)
        RegionRange(
            minX = minX,
            maxX = maxX,
            minZ = visibleRange.minZ,
            maxZ = visibleRange.maxZ
        )
    }
}

private fun collectMissingChunks(
    range: ChunkRange,
    scale: World.Scale,
    cache: Map<Long, SurfaceChunkRender>,
    knownMissing: Set<Long>,
    currentLoadingRanges: List<ChunkRange>
): List<ChunkCoord> {
    val missing = ArrayList<ChunkCoord>()
    val rangeWidth = (range.maxX - range.minX + 1).coerceAtLeast(1)
    val rangeHeight = (range.maxZ - range.minZ + 1).coerceAtLeast(1)
    val targetSamplesPerAxis = when {
        scale.level >= 6 -> 64
        scale.level == 5 -> 72
        scale.level == 4 -> 96
        else -> 128
    }
    val stride = max(
        1,
        max(rangeWidth / targetSamplesPerAxis, rangeHeight / targetSamplesPerAxis)
    )

    for (z in range.minZ..range.maxZ step stride) {
        for (x in range.minX..range.maxX step stride) {
            if (currentLoadingRanges.any { it.contains(x, z) }) continue
            val key = chunkCoordKey(x, z)
            if (cache.containsKey(key)) continue
            if (key in knownMissing) continue
            missing += ChunkCoord(x, z)
            if (missing.size >= MAX_MISSING_SCAN_BATCH) return missing
        }
    }
    if (missing.isEmpty() && stride > 1) {
        val centerX = (range.minX + range.maxX) / 2
        val centerZ = (range.minZ + range.maxZ) / 2
        if (currentLoadingRanges.none { it.contains(centerX, centerZ) }) {
            val centerKey = chunkCoordKey(centerX, centerZ)
            if (!cache.containsKey(centerKey) && centerKey !in knownMissing) {
                missing += ChunkCoord(centerX, centerZ)
            }
        }
    }
    return missing
}

private fun pickRequestRange(
    visibleRange: ChunkRange,
    missingCoords: List<ChunkCoord>,
    scale: World.Scale,
    preferHorizontal: Boolean
): ChunkRange? {
    if (missingCoords.isEmpty()) return null
    val stripThickness = when (scale) {
        World.Scale.L6 -> 48
        World.Scale.L5 -> 56
        World.Scale.L4 -> 64
        World.Scale.L3 -> 72
        else -> 80
    }
    val centerX = (visibleRange.minX + visibleRange.maxX) / 2
    val centerZ = (visibleRange.minZ + visibleRange.maxZ) / 2
    val anchor = missingCoords.minByOrNull { abs(it.x - centerX) + abs(it.z - centerZ) } ?: return null

    val visibleWidth = (visibleRange.maxX - visibleRange.minX + 1).coerceAtLeast(1)
    val visibleHeight = (visibleRange.maxZ - visibleRange.minZ + 1).coerceAtLeast(1)
    val horizontalThickness = max(
        1,
        min(stripThickness, MAX_REQUEST_CHUNKS_PER_BATCH / visibleWidth.coerceAtLeast(1))
    )
    val verticalThickness = max(
        1,
        min(stripThickness, MAX_REQUEST_CHUNKS_PER_BATCH / visibleHeight.coerceAtLeast(1))
    )

    return if (preferHorizontal) {
        val half = horizontalThickness / 2
        var minZ = (anchor.z - half).coerceIn(visibleRange.minZ, visibleRange.maxZ)
        var maxZ = (minZ + horizontalThickness - 1).coerceAtMost(visibleRange.maxZ)
        minZ = (maxZ - horizontalThickness + 1).coerceAtLeast(visibleRange.minZ)
        ChunkRange(
            minX = visibleRange.minX,
            maxX = visibleRange.maxX,
            minZ = minZ,
            maxZ = maxZ
        )
    } else {
        val half = verticalThickness / 2
        var minX = (anchor.x - half).coerceIn(visibleRange.minX, visibleRange.maxX)
        var maxX = (minX + verticalThickness - 1).coerceAtMost(visibleRange.maxX)
        minX = (maxX - verticalThickness + 1).coerceAtLeast(visibleRange.minX)
        ChunkRange(
            minX = minX,
            maxX = maxX,
            minZ = visibleRange.minZ,
            maxZ = visibleRange.maxZ
        )
    }
}

private fun computeViewportMetrics(
    scale: World.Scale,
    viewportSize: IntSize,
    centerChunkX: Float,
    centerChunkZ: Float,
    zoomLevel: Float = scale.level.toFloat()
): SurfaceViewportMetrics? {
    if (viewportSize.width <= 0 || viewportSize.height <= 0) return null
    val viewportWidthPx = viewportSize.width.toFloat()
    val viewportHeightPx = viewportSize.height.toFloat()
    val baseVisibleBlocks = 128f * 2f.pow(zoomLevel)
    if (baseVisibleBlocks <= 0f) return null

    val pixelsPerBlock = min(viewportWidthPx, viewportHeightPx) / baseVisibleBlocks
    if (pixelsPerBlock <= 0f) return null

    val visibleBlocksX = viewportWidthPx / pixelsPerBlock
    val visibleBlocksZ = viewportHeightPx / pixelsPerBlock
    val centerBlockX = centerChunkX * CHUNK_SIDE_BLOCKS + CHUNK_CENTER_BLOCK_OFFSET
    val centerBlockZ = centerChunkZ * CHUNK_SIDE_BLOCKS + CHUNK_CENTER_BLOCK_OFFSET
    val viewportLeftBlockX = centerBlockX - visibleBlocksX / 2f
    val viewportTopBlockZ = centerBlockZ - visibleBlocksZ / 2f

    return SurfaceViewportMetrics(
        pixelsPerBlock = pixelsPerBlock,
        visibleBlocksX = visibleBlocksX,
        visibleBlocksZ = visibleBlocksZ,
        viewportLeftBlockX = viewportLeftBlockX,
        viewportTopBlockZ = viewportTopBlockZ
    )
}

private fun parseMapHexColorInt(hex: String): Int {
    val cleaned = hex.removePrefix("#")
    return runCatching {
        when (cleaned.length) {
            6 -> (0xFF000000 or cleaned.toLong(16)).toInt()
            8 -> cleaned.toLong(16).toInt()
            else -> 0x00000000
        }
    }.getOrDefault(0x00000000)
}

private fun clampChunkCoordFloat(v: Float): Float =
    v.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())

private fun dimensionLabel(code: String): String = when (code) {
    "minecraft:overworld" -> "主世界"
    "minecraft:the_nether" -> "下界"
    "minecraft:the_end" -> "末地"
    else -> code
}

private fun chunkCoordKey(chunkX: Int, chunkZ: Int): Long =
    (chunkX.toLong() shl 32) xor (chunkZ.toLong() and 0xFFFF_FFFFL)

private fun chunkKeyX(key: Long): Int = (key shr 32).toInt()

private fun chunkKeyZ(key: Long): Int = key.toInt()

private fun regionCoordKey(regionX: Int, regionZ: Int): Long =
    (regionX.toLong() shl 32) xor (regionZ.toLong() and 0xFFFF_FFFFL)

private fun regionKeyX(key: Long): Int = (key shr 32).toInt()

private fun regionKeyZ(key: Long): Int = key.toInt()

private fun chunkGroupSizeForWideScale(scale: World.Scale): Int = when (scale) {
    World.Scale.L5 -> 1
    World.Scale.L6 -> 2
    World.Scale.L7 -> 4
    else -> 1
}

private data class SurfaceChunkRender(
    val side: Int,
    val colors: IntArray
)

private data class SurfaceRegionRender(
    val side: Int,
    val colors: IntArray
)

private data class VisibleMapRaster(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val versionSeed: Int
)

private data class ChunkCoord(
    val x: Int,
    val z: Int
)

private data class RegionCoord(
    val x: Int,
    val z: Int
)

private data class ChunkRange(
    val minX: Int,
    val maxX: Int,
    val minZ: Int,
    val maxZ: Int
) {
    fun contains(x: Int, z: Int): Boolean = x in minX..maxX && z in minZ..maxZ
}

private data class RegionRange(
    val minX: Int,
    val maxX: Int,
    val minZ: Int,
    val maxZ: Int
) {
    fun contains(x: Int, z: Int): Boolean = x in minX..maxX && z in minZ..maxZ
}

private data class SurfaceViewportMetrics(
    val pixelsPerBlock: Float,
    val visibleBlocksX: Float,
    val visibleBlocksZ: Float,
    val viewportLeftBlockX: Float,
    val viewportTopBlockZ: Float
)

private const val CHUNK_SIDE_BLOCKS = 16f
private const val CHUNKS_PER_REGION_SIDE = 32
private const val CHUNK_CENTER_BLOCK_OFFSET = 8f
private const val MAX_MISSING_SCAN_BATCH = 8192
private const val MAX_MISSING_REGION_SCAN_BATCH = 2048
private const val MAX_REQUEST_CHUNKS_PER_BATCH = 32768
private const val MAX_REQUEST_REGIONS_PER_BATCH = 384
private const val MAX_PARALLEL_CHUNK_REQUESTS = 3
private const val MAX_PARALLEL_REGION_REQUESTS = 2
private const val PINCH_ZOOM_SENSITIVITY = 3f
