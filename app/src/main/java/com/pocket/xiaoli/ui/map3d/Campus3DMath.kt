package com.pocket.xiaoli.ui.map3d

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** 水平面坐标：x 向东，z 向南，单位米。 */
data class Vec2(val x: Float, val z: Float) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, z + other.z)
    operator fun minus(other: Vec2) = Vec2(x - other.x, z - other.z)
    operator fun times(scale: Float) = Vec2(x * scale, z * scale)

    fun dot(other: Vec2): Float = x * other.x + z * other.z
    fun length(): Float = sqrt(x * x + z * z)
    fun distanceTo(other: Vec2): Float = (this - other).length()

    fun normalized(): Vec2 {
        val len = length()
        return if (len < 1e-5f) Vec2(0f, 0f) else Vec2(x / len, z / len)
    }
}

/** 三维坐标：x 向东，y 向上，z 向南，单位米。 */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Float) = Vec3(x * scale, y * scale, z * scale)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z
    fun cross(other: Vec3): Vec3 = Vec3(
        x = y * other.z - z * other.y,
        y = z * other.x - x * other.z,
        z = x * other.y - y * other.x,
    )
    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalized(): Vec3 {
        val len = length()
        return if (len < 1e-5f) Vec3(0f, 0f, 0f) else times(1f / len)
    }
}

data class ScreenPoint(val x: Float, val y: Float, val depth: Float)

data class Rect2D(val minX: Float, val minZ: Float, val maxX: Float, val maxZ: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxZ - minZ
    val center: Vec2 get() = Vec2((minX + maxX) * 0.5f, (minZ + maxZ) * 0.5f)

    fun contains(point: Vec2): Boolean =
        point.x in minX..maxX && point.z in minZ..maxZ

    fun expanded(meters: Float) = Rect2D(minX - meters, minZ - meters, maxX + meters, maxZ + meters)

    companion object {
        fun of(points: List<Vec2>): Rect2D {
            if (points.isEmpty()) return Rect2D(0f, 0f, 0f, 0f)
            var minX = points.first().x
            var maxX = minX
            var minZ = points.first().z
            var maxZ = minZ
            points.forEach { point ->
                minX = min(minX, point.x)
                maxX = max(maxX, point.x)
                minZ = min(minZ, point.z)
                maxZ = max(maxZ, point.z)
            }
            return Rect2D(minX, minZ, maxX, maxZ)
        }
    }
}

fun cleanRing(points: List<Vec2>): List<Vec2> {
    if (points.size < 3) return emptyList()
    val result = ArrayList<Vec2>(points.size)
    points.forEach { point ->
        val last = result.lastOrNull()
        if (last == null || last.distanceTo(point) > 0.05f) result += point
    }
    while (result.size > 1 && result.first().distanceTo(result.last()) <= 0.05f) {
        result.removeAt(result.lastIndex)
    }
    return if (result.size >= 3) result else emptyList()
}

fun signedArea(ring: List<Vec2>): Float {
    if (ring.size < 3) return 0f
    var sum = 0f
    for (index in ring.indices) {
        val current = ring[index]
        val next = ring[(index + 1) % ring.size]
        sum += current.x * next.z - next.x * current.z
    }
    return sum * 0.5f
}

fun outerRing(points: List<Vec2>): List<Vec2> {
    val cleaned = cleanRing(points)
    return if (signedArea(cleaned) < 0f) cleaned.reversed() else cleaned
}

fun holeRing(points: List<Vec2>): List<Vec2> {
    val cleaned = cleanRing(points)
    return if (signedArea(cleaned) > 0f) cleaned.reversed() else cleaned
}

fun polygonCentroid(ring: List<Vec2>): Vec2 {
    if (ring.isEmpty()) return Vec2(0f, 0f)
    var areaSum = 0f
    var xSum = 0f
    var zSum = 0f
    for (index in ring.indices) {
        val current = ring[index]
        val next = ring[(index + 1) % ring.size]
        val cross = current.x * next.z - next.x * current.z
        areaSum += cross
        xSum += (current.x + next.x) * cross
        zSum += (current.z + next.z) * cross
    }
    if (abs(areaSum) < 1e-4f) {
        return Vec2(ring.sumOf { it.x.toDouble() }.toFloat() / ring.size,
            ring.sumOf { it.z.toDouble() }.toFloat() / ring.size)
    }
    val factor = 1f / (3f * areaSum)
    return Vec2(xSum * factor, zSum * factor)
}

fun polygonContains(ring: List<Vec2>, point: Vec2): Boolean {
    if (ring.size < 3) return false
    var inside = false
    var previous = ring.last()
    ring.forEach { current ->
        val crosses = (current.z > point.z) != (previous.z > point.z)
        if (crosses) {
            val xAtZ = (previous.x - current.x) * (point.z - current.z) /
                (previous.z - current.z) + current.x
            if (point.x < xAtZ) inside = !inside
        }
        previous = current
    }
    return inside
}

fun pointInConvexQuad(quad: List<ScreenPoint>, x: Float, y: Float): Boolean {
    if (quad.size != 4) return false
    var positive = false
    var negative = false
    for (index in quad.indices) {
        val a = quad[index]
        val b = quad[(index + 1) % quad.size]
        val cross = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x)
        if (cross > 0.001f) positive = true
        if (cross < -0.001f) negative = true
        if (positive && negative) return false
    }
    return true
}

fun scaleTowards(ring: List<Vec2>, center: Vec2, factor: Float): List<Vec2> =
    ring.map { point -> center + (point - center) * factor }

fun scatterInside(
    ring: List<Vec2>,
    count: Int,
    seed: Int,
    minSpacing: Float = 9f,
    avoid: (Vec2) -> Boolean = { false },
): List<Vec2> {
    if (count <= 0 || ring.size < 3) return emptyList()
    val bounds = Rect2D.of(ring)
    val random = Random(seed)
    val result = ArrayList<Vec2>(count)
    var attempts = 0
    val maxAttempts = count * 60
    while (result.size < count && attempts < maxAttempts) {
        attempts++
        val point = Vec2(
            x = bounds.minX + random.nextFloat() * bounds.width,
            z = bounds.minZ + random.nextFloat() * bounds.height,
        )
        if (!polygonContains(ring, point)) continue
        if (avoid(point)) continue
        if (result.any { it.distanceTo(point) < minSpacing }) continue
        result += point
    }
    return result
}
