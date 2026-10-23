package com.pocket.xiaoli.ui.map3d

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 轨道相机：绕 [target] 旋转，yaw 为水平角（0 = 从南向北），pitch 为俯仰角。
 */
data class Campus3DCamera(
    val yawDegrees: Float = DEFAULT_YAW,
    val pitchDegrees: Float = DEFAULT_PITCH,
    val distance: Float,
    val target: Vec3 = Vec3(0f, 8f, 0f),
    val fovYDegrees: Float = DEFAULT_FOV,
) {
    fun eye(): Vec3 {
        val yaw = Math.toRadians(yawDegrees.toDouble())
        val pitch = Math.toRadians(pitchDegrees.toDouble())
        return Vec3(
            x = target.x + (distance * cos(pitch) * sin(yaw)).toFloat(),
            y = target.y + (distance * sin(pitch)).toFloat(),
            z = target.z + (distance * cos(pitch) * cos(yaw)).toFloat(),
        )
    }

    val azimuthRadians: Float get() = Math.toRadians(yawDegrees.toDouble()).toFloat()
    val elevationRadians: Float get() = Math.toRadians(pitchDegrees.toDouble()).toFloat()

    companion object {
        const val DEFAULT_YAW = 16f
        const val DEFAULT_PITCH = 38f
        const val DEFAULT_FOV = 46f
        const val MIN_PITCH = 18f
        const val MAX_PITCH = 72f
        const val MIN_YAW = -180f
        const val MAX_YAW = 180f
    }
}

/** 固定视口的相机投影，一帧只构建一次。 */
class Campus3DProjection(
    camera: Campus3DCamera,
    val width: Float,
    val height: Float,
) {
    val eye: Vec3 = camera.eye()
    val forward: Vec3
    val right: Vec3
    val up: Vec3
    val tanHalfFovY: Float
    private val aspect: Float

    init {
        val target = camera.target
        forward = (target - eye).normalized()
        right = forward.cross(Vec3(0f, 1f, 0f)).normalized()
        up = right.cross(forward).normalized()
        tanHalfFovY = tan(Math.toRadians(camera.fovYDegrees.toDouble() / 2.0)).toFloat()
        aspect = if (height > 0f) width / height else 1f
    }

    fun depthOf(point: Vec3): Float = (point - eye).dot(forward)

    fun project(point: Vec3): ScreenPoint? {
        val relative = point - eye
        val depth = relative.dot(forward)
        if (depth <= 1f) return null
        val tanHalfFovX = tanHalfFovY * aspect
        val ndcX = relative.dot(right) / (depth * tanHalfFovX)
        val ndcY = relative.dot(up) / (depth * tanHalfFovY)
        return ScreenPoint(
            x = (ndcX * 0.5f + 0.5f) * width,
            y = (0.5f - ndcY * 0.5f) * height,
            depth = depth,
        )
    }

    fun projectFlat(point: Vec2, altitude: Float = 0f): ScreenPoint? =
        project(Vec3(point.x, altitude, point.z))

    fun pixelsPerMeter(depth: Float): Float =
        if (depth <= 1f) 0f else height / (2f * depth * tanHalfFovY)
}

/** 相机插值：yaw 走最短弧，避免 179° → -179° 时绕远路；fraction 0 为起点，1 为终点。 */
internal fun interpolateCamera(
    start: Campus3DCamera,
    end: Campus3DCamera,
    fraction: Float,
): Campus3DCamera {
    val t = fraction.coerceIn(0f, 1f)
    return Campus3DCamera(
        yawDegrees = normalizeYawDegrees(
            start.yawDegrees + shortestYawDelta(start.yawDegrees, end.yawDegrees) * t
        ),
        pitchDegrees = lerpFloat(start.pitchDegrees, end.pitchDegrees, t),
        distance = lerpFloat(start.distance, end.distance, t),
        target = Vec3(
            x = lerpFloat(start.target.x, end.target.x, t),
            y = lerpFloat(start.target.y, end.target.y, t),
            z = lerpFloat(start.target.z, end.target.z, t),
        ),
        fovYDegrees = lerpFloat(start.fovYDegrees, end.fovYDegrees, t),
    )
}

internal fun normalizeYawDegrees(value: Float): Float {
    var yaw = value % 360f
    if (yaw > 180f) yaw -= 360f
    if (yaw < -180f) yaw += 360f
    return yaw
}

private fun shortestYawDelta(start: Float, end: Float): Float =
    normalizeYawDegrees(end - start)

private fun lerpFloat(start: Float, end: Float, t: Float): Float =
    start + (end - start) * t

/** 让整个校区进入画面的距离；竖屏时允许左右裁掉一点，保证楼栋可辨认。 */
fun fitCameraDistance(
    radius: Float,
    aspect: Float,
    fovYDegrees: Float = Campus3DCamera.DEFAULT_FOV,
): Float {
    val tanHalfY = tan(Math.toRadians(fovYDegrees.toDouble() / 2.0)).toFloat()
    val tanHalfX = tanHalfY * aspect.coerceAtLeast(0.35f)
    val preferred = if (aspect >= 0.85f) {
        radius / tanHalfX * 1.05f
    } else {
        // 竖屏优先铺满纵向，横向允许裁切，避免整体缩得太小。
        radius * 1.9f
    }
    return preferred.coerceIn(radius * 1.3f, radius * 5.5f)
}
