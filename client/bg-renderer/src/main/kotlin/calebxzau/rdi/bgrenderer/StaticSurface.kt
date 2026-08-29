package calebxzau.rdi.bgrenderer

import kotlin.math.abs

/** The six directions use the same order as the legacy cube emitter. */
internal enum class StaticFaceDirection(
    val normalX: Float,
    val normalY: Float,
    val normalZ: Float,
    private val fixedAxis: Axis,
    private val uAxis: Axis,
    private val vAxis: Axis,
    private val uSign: Int,
    private val vSign: Int
) {
    PositiveZ(0f, 0f, 1f, Axis.Z, Axis.X, Axis.Y, 1, 1),
    NegativeZ(0f, 0f, -1f, Axis.Z, Axis.X, Axis.Y, -1, 1),
    NegativeX(-1f, 0f, 0f, Axis.X, Axis.Z, Axis.Y, 1, 1),
    PositiveX(1f, 0f, 0f, Axis.X, Axis.Z, Axis.Y, -1, 1),
    PositiveY(0f, 1f, 0f, Axis.Y, Axis.X, Axis.Z, 1, -1),
    NegativeY(0f, -1f, 0f, Axis.Y, Axis.X, Axis.Z, 1, 1);

    fun plane(box: StaticAabb): Float = fixedAxis.value(
        box,
        minimum = normalX < -0.5f || normalY < -0.5f || normalZ < -0.5f
    )

    fun rect(box: StaticAabb): SurfaceRect = SurfaceRect(
        uAxis.value(box, minimum = true),
        uAxis.value(box, minimum = false),
        vAxis.value(box, minimum = true),
        vAxis.value(box, minimum = false)
    )

    fun corners(box: StaticAabb, rect: SurfaceRect): FloatArray {
        val fixed = plane(box)
        val firstU = if (uSign > 0) rect.minU else rect.maxU
        val secondU = if (uSign > 0) rect.maxU else rect.minU
        val firstV = if (vSign > 0) rect.minV else rect.maxV
        val secondV = if (vSign > 0) rect.maxV else rect.minV
        val first = point(fixed, firstU, firstV)
        val second = point(fixed, secondU, firstV)
        val third = point(fixed, secondU, secondV)
        val fourth = point(fixed, firstU, secondV)
        return floatArrayOf(
            first[0], first[1], first[2],
            second[0], second[1], second[2],
            third[0], third[1], third[2],
            fourth[0], fourth[1], fourth[2]
        )
    }

    private fun point(fixed: Float, u: Float, v: Float): FloatArray {
        val point = FloatArray(3)
        fixedAxis.put(point, fixed)
        uAxis.put(point, u)
        vAxis.put(point, v)
        return point
    }

    private enum class Axis {
        X {
            override fun value(box: StaticAabb): Float = box.minX
            override fun value(box: StaticAabb, minimum: Boolean): Float = if (minimum) box.minX else box.maxX
            override fun put(point: FloatArray, value: Float) { point[0] = value }
        },
        Y {
            override fun value(box: StaticAabb): Float = box.minY
            override fun value(box: StaticAabb, minimum: Boolean): Float = if (minimum) box.minY else box.maxY
            override fun put(point: FloatArray, value: Float) { point[1] = value }
        },
        Z {
            override fun value(box: StaticAabb): Float = box.minZ
            override fun value(box: StaticAabb, minimum: Boolean): Float = if (minimum) box.minZ else box.maxZ
            override fun put(point: FloatArray, value: Float) { point[2] = value }
        };

        abstract fun value(box: StaticAabb): Float
        abstract fun value(box: StaticAabb, minimum: Boolean): Float
        abstract fun put(point: FloatArray, value: Float)
    }
}

internal data class SurfaceRect(
    val minU: Float,
    val maxU: Float,
    val minV: Float,
    val maxV: Float
) {
    val area: Float get() = (maxU - minU) * (maxV - minV)
}

internal data class StaticFaceQuad(
    val direction: StaticFaceDirection,
    val points: FloatArray
)

private const val STATIC_SURFACE_EPSILON = 1.0e-4f

/** Returns only the exposed, non-overlapping quads for one static AABB. */
internal fun exposedStaticFaces(
    sourceIndex: Int,
    staticAabbs: List<StaticAabb>
): List<StaticFaceQuad> {
    require(sourceIndex in staticAabbs.indices) { "Invalid static AABB index: $sourceIndex" }
    val source = staticAabbs[sourceIndex]
    return StaticFaceDirection.entries.flatMap { direction ->
        var remaining = listOf(direction.rect(source))
        val plane = direction.plane(source)
        staticAabbs.forEachIndexed { candidateIndex, candidate ->
            if (candidateIndex == sourceIndex) return@forEachIndexed
            val candidateRect = direction.rect(candidate)
            if (!hasPositiveOverlap(remaining, candidateRect)) return@forEachIndexed
            if (!direction.occludes(plane, candidate, candidateIndex, sourceIndex)) return@forEachIndexed
            remaining = remaining.flatMap { subtract(it, candidateRect) }
        }
        mergeAdjacent(remaining).sortedWith(compareBy<SurfaceRect> { it.minU }.thenBy { it.minV }.thenBy { it.maxU }.thenBy { it.maxV })
            .map { rect -> StaticFaceQuad(direction, direction.corners(source, rect)) }
    }
}

/** Convenience form useful to geometry tests and callers holding the source box. */
internal fun exposedStaticFaces(
    source: StaticAabb,
    staticAabbs: List<StaticAabb>
): List<StaticFaceQuad> = exposedStaticFaces(staticAabbs.indexOfFirst { it === source }.also {
    require(it >= 0) { "Source AABB must be an element of staticAabbs" }
}, staticAabbs)

private fun StaticFaceDirection.occludes(
    plane: Float,
    candidate: StaticAabb,
    candidateIndex: Int,
    sourceIndex: Int
): Boolean {
    val axis = when {
        abs(normalX) > 0.5f -> 0
        abs(normalY) > 0.5f -> 1
        else -> 2
    }
    val candidateMin = when (axis) {
        0 -> candidate.minX
        1 -> candidate.minY
        else -> candidate.minZ
    }
    val candidateMax = when (axis) {
        0 -> candidate.maxX
        1 -> candidate.maxY
        else -> candidate.maxZ
    }
    val positive = normalX > 0.5f || normalY > 0.5f || normalZ > 0.5f
    val extendsOutward = if (positive) candidateMax > plane + STATIC_SURFACE_EPSILON
    else candidateMin < plane - STATIC_SURFACE_EPSILON
    val reachesPlane = if (positive) candidateMin <= plane + STATIC_SURFACE_EPSILON
    else candidateMax >= plane - STATIC_SURFACE_EPSILON
    if (extendsOutward && reachesPlane) return true

    // A coplanar duplicate is owned by the first descriptor. Only the later
    // descriptor is cut, so a box wholly behind this plane cannot hide it.
    val coplanar = if (positive) {
        abs(candidateMax - plane) <= STATIC_SURFACE_EPSILON && candidateMin < plane - STATIC_SURFACE_EPSILON
    } else {
        abs(candidateMin - plane) <= STATIC_SURFACE_EPSILON && candidateMax > plane + STATIC_SURFACE_EPSILON
    }
    return coplanar && candidateIndex < sourceIndex
}

private fun hasPositiveOverlap(rects: List<SurfaceRect>, candidate: SurfaceRect): Boolean =
    rects.any {
        minOf(it.maxU, candidate.maxU) - maxOf(it.minU, candidate.minU) > STATIC_SURFACE_EPSILON &&
            minOf(it.maxV, candidate.maxV) - maxOf(it.minV, candidate.minV) > STATIC_SURFACE_EPSILON
    }

private fun subtract(source: SurfaceRect, occluder: SurfaceRect): List<SurfaceRect> {
    val left = maxOf(source.minU, occluder.minU)
    val right = minOf(source.maxU, occluder.maxU)
    val bottom = maxOf(source.minV, occluder.minV)
    val top = minOf(source.maxV, occluder.maxV)
    if (right - left <= STATIC_SURFACE_EPSILON || top - bottom <= STATIC_SURFACE_EPSILON) return listOf(source)

    return buildList(4) {
        if (left - source.minU > STATIC_SURFACE_EPSILON) add(SurfaceRect(source.minU, left, source.minV, source.maxV))
        if (source.maxU - right > STATIC_SURFACE_EPSILON) add(SurfaceRect(right, source.maxU, source.minV, source.maxV))
        if (bottom - source.minV > STATIC_SURFACE_EPSILON) add(SurfaceRect(left, right, source.minV, bottom))
        if (source.maxV - top > STATIC_SURFACE_EPSILON) add(SurfaceRect(left, right, top, source.maxV))
    }
}

/*
 * Subtraction can leave two rectangles that are simply the two halves of a
 * single exposed strip. Joining those same-source pieces does not alter the
 * surface union, but avoids needlessly duplicating vertices at a cut line.
 * The fixed scan order makes this normalization deterministic.
 */
private fun mergeAdjacent(input: List<SurfaceRect>): List<SurfaceRect> {
    val rectangles = input.toMutableList()
    while (true) {
        var merged = false
        outer@ for (firstIndex in rectangles.indices) {
            for (secondIndex in firstIndex + 1 until rectangles.size) {
                val first = rectangles[firstIndex]
                val second = rectangles[secondIndex]
                val joined = when {
                    abs(first.minV - second.minV) <= STATIC_SURFACE_EPSILON &&
                        abs(first.maxV - second.maxV) <= STATIC_SURFACE_EPSILON &&
                        (abs(first.maxU - second.minU) <= STATIC_SURFACE_EPSILON || abs(second.maxU - first.minU) <= STATIC_SURFACE_EPSILON) ->
                        SurfaceRect(minOf(first.minU, second.minU), maxOf(first.maxU, second.maxU), first.minV, first.maxV)
                    abs(first.minU - second.minU) <= STATIC_SURFACE_EPSILON &&
                        abs(first.maxU - second.maxU) <= STATIC_SURFACE_EPSILON &&
                        (abs(first.maxV - second.minV) <= STATIC_SURFACE_EPSILON || abs(second.maxV - first.minV) <= STATIC_SURFACE_EPSILON) ->
                        SurfaceRect(first.minU, first.maxU, minOf(first.minV, second.minV), maxOf(first.maxV, second.maxV))
                    else -> null
                }
                if (joined != null) {
                    rectangles[firstIndex] = joined
                    rectangles.removeAt(secondIndex)
                    merged = true
                    break@outer
                }
            }
        }
        if (!merged) return rectangles
    }
}
