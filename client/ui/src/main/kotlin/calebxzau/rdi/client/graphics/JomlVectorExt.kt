package calebxzau.rdi.client.graphics

import org.joml.Vector3f

operator fun Vector3f.plus(other: Vector3f): Vector3f = Vector3f(this).add(other)

operator fun Vector3f.minus(other: Vector3f): Vector3f = Vector3f(this).sub(other)

operator fun Vector3f.times(scale: Float): Vector3f = Vector3f(this).mul(scale)

fun Vector3f.crossed(other: Vector3f): Vector3f = this.cross(other, Vector3f())

/**
 * Immutable-style normalize helper for renderer math.
 */
fun Vector3f.normalized(): Vector3f {
    if (lengthSquared() < 1e-12f) return Vector3f(this)
    return Vector3f(this).normalize()
}
