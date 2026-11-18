package com.bussid.autopilot

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Road estimate in source-frame pixel coordinates. Confidence is a geometric/edge heuristic,
 * not a probability and not a substitute for a trained semantic segmentation model. */
data class RoadState(
    val detected: Boolean,
    val centerOffset: Float,
    val curvature: Float,
    val direction: String,
    val confidence: Float,
    val leftBoundary: Int,
    val rightBoundary: Int,
    val centerX: Float = -1f
)

/**
 * CPU-only vision pipeline: resize, grayscale, Sobel edge extraction, trapezoid ROI and
 * constrained Hough voting for the two road-edge families. It deliberately fails closed
 * when the pair geometry or support is weak. BUSSID HUD/vehicles can still confuse it.
 */
data class RoadRoiProfile(
    val topY: Float = .36f,
    val bottomY: Float = .79f,
    val topHalfWidth: Float = .10f,
    val bottomHalfWidth: Float = .35f,
    val centerX: Float = .50f,
    val edgeThreshold: Int = 150
) {
    init {
        require(topY in 0f..0.9f && bottomY in .1f..1f && bottomY > topY)
        require(topHalfWidth in .02f..0.45f && bottomHalfWidth in .1f..0.49f)
        require(centerX in .35f.. .65f && edgeThreshold in 40..500)
    }
}

class RoadDetector(
    private val minConfidence: Float = .43f,
    private val roi: RoadRoiProfile = RoadRoiProfile()
) {
    fun analyze(source: Bitmap): RoadState {
        val w = 192
        val h = 108
        if (source.width < 80 || source.height < 80) return empty()
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        try {
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            val gray = IntArray(pixels.size) { i ->
                val c = pixels[i]
                (((c shr 16 and 255) * 299 + (c shr 8 and 255) * 587 + (c and 255) * 114) / 1000)
            }
            val edges = ArrayList<EdgePoint>(1400)
            val top = (roi.topY * (h - 1)).toInt().coerceIn(1, h - 3)
            val bottom = (roi.bottomY * (h - 1)).toInt().coerceIn(top + 2, h - 2)
            // BUSSID cockpit profile: ignore sky above the windshield horizon, side HUD/mirrors,
            // and the dashboard/steering wheel below the windshield. Perspective ROI widens down.
            for (y in top + 1 until bottom) {
                val yn = (y - top).toFloat() / (bottom - top)
                val half = roi.topHalfWidth + (roi.bottomHalfWidth - roi.topHalfWidth) * yn
                val xMin = ((roi.centerX - half) * w).toInt().coerceAtLeast(1)
                val xMax = ((roi.centerX + half) * w).toInt().coerceAtMost(w - 2)
                for (x in xMin..xMax) {
                    val i = y * w + x
                    val gx = -gray[i - w - 1] - 2 * gray[i - 1] - gray[i + w - 1] +
                        gray[i - w + 1] + 2 * gray[i + 1] + gray[i + w + 1]
                    val gy = -gray[i - w - 1] - 2 * gray[i - w] - gray[i - w + 1] +
                        gray[i + w - 1] + 2 * gray[i + w] + gray[i + w + 1]
                    val magnitude = abs(gx) + abs(gy)
                    if (magnitude >= roi.edgeThreshold) edges.add(EdgePoint(x.toFloat() / w, yn, magnitude))
                }
            }
            if (edges.size < 35) return empty()

            val left = findBoundary(edges, w, leftSide = true) ?: return empty()
            val right = findBoundary(edges, w, leftSide = false) ?: return empty()
            val leftNear = left.xAt(1f)
            val rightNear = right.xAt(1f)
            val leftFar = left.xAt(.18f)
            val rightFar = right.xAt(.18f)
            val nearWidth = rightNear - leftNear
            val farWidth = rightFar - leftFar
            if (leftNear !in .015f.. .48f || rightNear !in .52f.. .99f ||
                nearWidth !in .20f.. .94f || farWidth !in .06f.. .85f) return empty()

            val nearCenter = (leftNear + rightNear) * .5f
            val farCenter = (leftFar + rightFar) * .5f
            val offset = ((nearCenter - roi.centerX) * 2f).coerceIn(-1f, 1f)
            // Positive feed-forward means the projected road center bends to screen-right.
            val curve = ((farCenter - nearCenter) * 1.7f).coerceIn(-1f, 1f)
            val pairQuality = (1f - abs(nearWidth - farWidth) / max(.35f, nearWidth)).coerceIn(.15f, 1f)
            val support = min(left.votes, right.votes) / 34f
            val confidence = (support.coerceIn(0f, 1f) * pairQuality *
                min(left.quality, right.quality)).coerceIn(0f, 1f)
            if (confidence < minConfidence) return empty()
            val direction = when {
                curve > .055f -> "RIGHT CURVE"
                curve < -.055f -> "LEFT CURVE"
                else -> "STRAIGHT"
            }
            return RoadState(true, offset, curve, direction, confidence,
                (leftNear * source.width).toInt(), (rightNear * source.width).toInt(),
                nearCenter * source.width)
        } finally {
            if (!small.isRecycled && small !== source) small.recycle()
        }
    }

    private data class EdgePoint(val x: Float, val y: Float, val strength: Int)
    private data class Boundary(val slope: Float, val intercept: Float, val votes: Int, val quality: Float) {
        fun xAt(y: Float) = slope * y + intercept
    }

    private fun findBoundary(points: List<EdgePoint>, width: Int, leftSide: Boolean): Boundary? {
        val slopeCount = 31
        val minSlope = if (leftSide) -.90f else .10f
        val maxSlope = if (leftSide) -.10f else .90f
        val accumulator = Array(slopeCount) { IntArray(width) }
        for (p in points) {
            for (s in 0 until slopeCount) {
                val slope = minSlope + (maxSlope - minSlope) * s / (slopeCount - 1)
                val b = p.x - slope * p.y
                val bi = (b * width).toInt()
                if (bi in 0 until width) accumulator[s][bi]++
            }
        }
        var bestVotes = 0
        var bestSlope = 0f
        var bestIntercept = 0f
        for (s in 0 until slopeCount) {
            val slope = minSlope + (maxSlope - minSlope) * s / (slopeCount - 1)
            for (b in 0 until width) {
                val bottom = b.toFloat() / width + slope
                val plausible = if (leftSide) b.toFloat() / width in .30f.. .86f && bottom in .015f.. .48f
                    else b.toFloat() / width in .14f.. .70f && bottom in .52f.. .99f
                val votes = accumulator[s][b]
                if (plausible && votes > bestVotes) {
                    bestVotes = votes; bestSlope = slope; bestIntercept = b.toFloat() / width
                }
            }
        }
        if (bestVotes < 9) return null
        // A normalized slope near vertical is expected; reward coherent line support.
        val quality = (bestVotes / 42f).coerceIn(.2f, 1f)
        return Boundary(bestSlope, bestIntercept, bestVotes, quality)
    }

    private fun empty() = RoadState(false, 0f, 0f, "UNKNOWN", 0f, -1, -1, -1f)
}
