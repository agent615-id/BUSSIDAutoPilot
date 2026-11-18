package com.bussid.autopilot

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Lightweight real-frame road-boundary heuristic. It samples three horizontal scanlines
 * in the lower game view and selects the strongest luminance edge on each half.
 * This is not semantic segmentation and must be treated as an uncertain estimate.
 */
data class RoadState(
    val detected: Boolean,
    val centerOffset: Float,
    val curvature: Float,
    val direction: String,
    val confidence: Float,
    val leftBoundary: Int,
    val rightBoundary: Int
)

class RoadDetector {
    fun analyze(source: Bitmap): RoadState {
        val w = source.width
        val h = source.height
        if (w < 80 || h < 80) return empty()
        val scanRows = intArrayOf((h * .58f).toInt(), (h * .72f).toInt(), (h * .86f).toInt())
        val pairs = ArrayList<Pair<Int, Int>>(3)
        var edgeStrength = 0f
        for (y in scanRows) {
            val luminance = IntArray(w) { x -> luminance(source.getPixel(x, y)) }
            val left = strongestEdge(luminance, (w * .10f).toInt(), (w * .48f).toInt())
            val right = strongestEdge(luminance, (w * .52f).toInt(), (w * .90f).toInt())
            if (left.first >= 0 && right.first >= 0) {
                val roadWidth = right.first - left.first
                if (roadWidth in (w * .18f).toInt()..(w * .82f).toInt()) {
                    pairs.add(left.first to right.first)
                    edgeStrength += (left.second + right.second) / 510f
                }
            }
        }
        if (pairs.isEmpty()) return empty()
        val leftMean = pairs.map { it.first }.average().toInt()
        val rightMean = pairs.map { it.second }.average().toInt()
        val center = (leftMean + rightMean) / 2f
        // Positive offset means the road center lies to the right of screen/bus center.
        val offset = ((center - w / 2f) / (w / 2f)).coerceIn(-1f, 1f)
        val curvature = if (pairs.size >= 2) {
            val far = pairs.first()
            val near = pairs.last()
            (((near.first + near.second) - (far.first + far.second)) / 2f / w).coerceIn(-1f, 1f)
        } else 0f
        val direction = when {
            curvature > .035f -> "Tikungan kanan"
            curvature < -.035f -> "Tikungan kiri"
            else -> "Jalan relatif lurus"
        }
        val confidence = ((pairs.size / 3f) * (edgeStrength / pairs.size)).coerceIn(0f, 1f)
        return RoadState(true, offset, curvature, direction, confidence, leftMean, rightMean)
    }

    private fun strongestEdge(row: IntArray, from: Int, until: Int): Pair<Int, Int> {
        var bestX = -1
        var bestGradient = 25
        for (x in (from + 2).coerceAtMost(row.lastIndex - 2) until (until - 2).coerceAtMost(row.lastIndex - 1)) {
            val gradient = abs(row[x + 2] - row[x - 2])
            if (gradient > bestGradient) { bestGradient = gradient; bestX = x }
        }
        return bestX to bestGradient
    }

    private fun luminance(c: Int) =
        (((c shr 16 and 255) * 299 + (c shr 8 and 255) * 587 + (c and 255) * 114) / 1000)

    private fun empty() = RoadState(false, 0f, 0f, "Jalan tidak terdeteksi", 0f, -1, -1)
}
