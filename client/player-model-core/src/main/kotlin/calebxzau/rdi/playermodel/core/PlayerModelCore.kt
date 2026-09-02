package calebxzau.rdi.playermodel.core

import org.joml.Vector3f

/** CPU-side skin bytes shared by the standalone player and background renderers. */
data class PlayerSkinTexture(
    val width: Int,
    val height: Int,
    val rgba: ByteArray,
    val slim: Boolean = detectSlimSkin(width, height, rgba)
) {
    init {
        require(width > 0 && height > 0) { "Skin dimensions must be positive" }
        require(rgba.size == width * height * 4) { "Skin RGBA data has the wrong size" }
    }
}

fun detectSlimSkin(width: Int, height: Int, rgba: ByteArray): Boolean {
    if (width < 64 || height < 64 || rgba.size < width * height * 4) return false
    var transparent = 0
    var total = 0
    fun checkAlpha(x: Int, y: Int) {
        if (x !in 0 until width || y !in 0 until height) return
        total++
        if ((rgba[(y * width + x) * 4 + 3].toInt() and 0xff) < 16) transparent++
    }
    for (y in 20..31) checkAlpha(54, y)
    for (y in 52..63) checkAlpha(46, y)
    return total > 0 && transparent >= total * 0.75f
}

enum class PlayerModelPart { HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, CAPE }
enum class PlayerModelTexture { SKIN, CAPE }

data class PlayerMeshData(
    val vertices: FloatArray,
    val armPivotX: Float,
    val skinVertexCount: Int,
    val capeVertexCount: Int
)

private data class UvRect(
    val u: Float,
    val v: Float,
    val width: Float,
    val height: Float,
    val flipX: Boolean = false
)

private data class CuboidUv(
    val top: UvRect,
    val bottom: UvRect,
    val left: UvRect,
    val front: UvRect,
    val right: UvRect,
    val back: UvRect
)
private data class MeshFace(
    val v0: Vector3f, val v1: Vector3f, val v2: Vector3f, val v3: Vector3f,
    val normal: Vector3f, val uv: UvRect, val shade: Float,
    val part: PlayerModelPart, val texture: PlayerModelTexture = PlayerModelTexture.SKIN,
    val uvOrder: IntArray = DEFAULT_UV_ORDER
)

private val DEFAULT_UV_ORDER = intArrayOf(0, 1, 2, 3)
private val REVERSED_WINDING_UV_ORDER = intArrayOf(0, 3, 2, 1)

/** Minecraft 64x64/64x32 player cuboids, emitted once per mesh variant. */
fun buildPlayerMeshData(
    slim: Boolean,
    legacy: Boolean,
    showOuterLayer: Boolean,
    hasCape: Boolean
): PlayerMeshData {
    val faces = ArrayList<MeshFace>(96)
    val armWidth = if (slim) 3 else 4
    val armPivotX = if (slim) 5.5f else 6f
    val armWidthF = armWidth.toFloat()
    addCuboid(faces, Vector3f(0f, 28f, 0f), Vector3f(8f, 8f, 8f), cubeUv(0, 0, 8, 8, 8), PlayerModelPart.HEAD)
    addCuboid(faces, Vector3f(0f, 18f, 0f), Vector3f(8f, 12f, 4f), cubeUv(16, 16, 8, 12, 4), PlayerModelPart.BODY)
    addCuboid(faces, Vector3f(-armPivotX, 18f, 0f), Vector3f(armWidthF, 12f, 4f), cubeUv(40, 16, armWidth, 12, 4), PlayerModelPart.LEFT_ARM)
    addCuboid(
        faces,
        Vector3f(armPivotX, 18f, 0f),
        Vector3f(armWidthF, 12f, 4f),
        if (legacy) cubeUv(40, 16, armWidth, 12, 4) else cubeUv(32, 48, armWidth, 12, 4),
        PlayerModelPart.RIGHT_ARM
    )
    addCuboid(
        faces,
        Vector3f(-1.9f, 6f, -0.1f),
        Vector3f(4f, 12f, 4f),
        cubeUv(0, 16, 4, 12, 4),
        PlayerModelPart.LEFT_LEG
    )
    addCuboid(
        faces,
        Vector3f(1.9f, 6f, -0.1f),
        Vector3f(4f, 12f, 4f),
        if (legacy) cubeUv(0, 16, 4, 12, 4) else cubeUv(16, 48, 4, 12, 4),
        PlayerModelPart.RIGHT_LEG
    )
    if (showOuterLayer) {
        addCuboid(faces, Vector3f(0f, 28f, 0f), Vector3f(8f, 8f, 8f), cubeUv(32, 0, 8, 8, 8), PlayerModelPart.HEAD, 0.5f)
        if (!legacy) {
            addCuboid(faces, Vector3f(0f, 18f, 0f), Vector3f(8f, 12f, 4f), cubeUv(16, 32, 8, 12, 4), PlayerModelPart.BODY, 0.25f)
            addCuboid(faces, Vector3f(-armPivotX, 18f, 0f), Vector3f(armWidthF, 12f, 4f), cubeUv(40, 32, armWidth, 12, 4), PlayerModelPart.LEFT_ARM, 0.25f)
            addCuboid(faces, Vector3f(armPivotX, 18f, 0f), Vector3f(armWidthF, 12f, 4f), cubeUv(48, 48, armWidth, 12, 4), PlayerModelPart.RIGHT_ARM, 0.25f)
            addCuboid(faces, Vector3f(-1.9f, 6f, -0.1f), Vector3f(4f, 12f, 4f), cubeUv(0, 32, 4, 12, 4), PlayerModelPart.LEFT_LEG, 0.25f)
            addCuboid(faces, Vector3f(1.9f, 6f, -0.1f), Vector3f(4f, 12f, 4f), cubeUv(0, 48, 4, 12, 4), PlayerModelPart.RIGHT_LEG, 0.25f)
        }
    }
    if (hasCape) {
        addCuboid(
            faces,
            Vector3f(0f, 16f, -2.6f),
            Vector3f(10f, 16f, 1f),
            cubeUv(0, 0, 10, 16, 1),
            PlayerModelPart.CAPE,
            texture = PlayerModelTexture.CAPE
        )
    }
    val vertices = ArrayList<Float>(faces.size * 6 * VERTEX_FLOATS)
    faces.forEach { appendFace(vertices, it) }
    return PlayerMeshData(
        vertices = vertices.toFloatArray(),
        armPivotX = armPivotX,
        skinVertexCount = faces.count { it.texture == PlayerModelTexture.SKIN } * 6,
        capeVertexCount = faces.count { it.texture == PlayerModelTexture.CAPE } * 6
    )
}

private fun cubeUv(u: Int, v: Int, width: Int, height: Int, depth: Int) = CuboidUv(
    UvRect((u + depth).toFloat(), v.toFloat(), width.toFloat(), depth.toFloat()),
    UvRect((u + width + depth).toFloat(), v.toFloat(), width.toFloat(), depth.toFloat()),
    UvRect(u.toFloat(), (v + depth).toFloat(), depth.toFloat(), height.toFloat()),
    UvRect((u + depth).toFloat(), (v + depth).toFloat(), width.toFloat(), height.toFloat()),
    UvRect((u + width + depth).toFloat(), (v + depth).toFloat(), depth.toFloat(), height.toFloat()),
    UvRect((u + width + depth * 2).toFloat(), (v + depth).toFloat(), width.toFloat(), height.toFloat(), true)
)

private fun addCuboid(
    faces: MutableList<MeshFace>,
    center: Vector3f,
    size: Vector3f,
    uv: CuboidUv,
    part: PlayerModelPart,
    inflate: Float = 0f,
    texture: PlayerModelTexture = PlayerModelTexture.SKIN
) {
    val hx = size.x * 0.5f + inflate
    val hy = size.y * 0.5f + inflate
    val hz = size.z * 0.5f + inflate
    val l = center.x - hx
    val r = center.x + hx
    val d = center.y - hy
    val u = center.y + hy
    val b = center.z - hz
    val f = center.z + hz
    faces += MeshFace(Vector3f(l, d, f), Vector3f(r, d, f), Vector3f(r, u, f), Vector3f(l, u, f), Vector3f(0f, 0f, 1f), uv.front, 1f, part, texture)
    faces += MeshFace(Vector3f(r, d, b), Vector3f(l, d, b), Vector3f(l, u, b), Vector3f(r, u, b), Vector3f(0f, 0f, -1f), uv.back, 0.82f, part, texture)
    faces += MeshFace(Vector3f(l, d, b), Vector3f(l, d, f), Vector3f(l, u, f), Vector3f(l, u, b), Vector3f(-1f, 0f, 0f), uv.left, 0.9f, part, texture)
    faces += MeshFace(Vector3f(r, d, f), Vector3f(r, d, b), Vector3f(r, u, b), Vector3f(r, u, f), Vector3f(1f, 0f, 0f), uv.right, 0.78f, part, texture)
    faces += MeshFace(
        Vector3f(l, u, b), Vector3f(l, u, f), Vector3f(r, u, f), Vector3f(r, u, b),
        Vector3f(0f, 1f, 0f), uv.top, 1.08f, part, texture, REVERSED_WINDING_UV_ORDER
    )
    faces += MeshFace(
        Vector3f(l, d, f), Vector3f(l, d, b), Vector3f(r, d, b), Vector3f(r, d, f),
        Vector3f(0f, -1f, 0f), uv.bottom, 0.62f, part, texture, REVERSED_WINDING_UV_ORDER
    )
}

private fun appendFace(out: MutableList<Float>, face: MeshFace) {
    val u0 = if (face.uv.flipX) face.uv.u + face.uv.width else face.uv.u
    val u1 = if (face.uv.flipX) face.uv.u else face.uv.u + face.uv.width
    val v0 = face.uv.v
    val v1 = face.uv.v + face.uv.height
    val vertices = arrayOf(face.v0, face.v1, face.v2, face.v3)
    val uvCorners = arrayOf(floatArrayOf(u0, v1), floatArrayOf(u1, v1), floatArrayOf(u1, v0), floatArrayOf(u0, v0))
    fun appendCorner(index: Int) {
        val corner = uvCorners[face.uvOrder[index]]
        appendVertex(out, vertices[index], corner[0], corner[1], face)
    }
    appendCorner(0)
    appendCorner(1)
    appendCorner(2)
    appendCorner(0)
    appendCorner(2)
    appendCorner(3)
}

private fun appendVertex(out: MutableList<Float>, p: Vector3f, u: Float, v: Float, face: MeshFace) {
    out += p.x
    out += p.y
    out += p.z
    out += u
    out += v
    out += face.normal.x
    out += face.normal.y
    out += face.normal.z
    out += face.part.ordinal.toFloat()
    out += face.shade
    out += face.texture.ordinal.toFloat()
}

private const val VERTEX_FLOATS = 11
