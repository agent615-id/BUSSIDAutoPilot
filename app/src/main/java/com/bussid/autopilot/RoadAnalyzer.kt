package com.bussid.autopilot

import kotlin.math.abs

/** Temporal stabilizer. A failed frame is never carried forward as a valid road detection. */
class RoadAnalyzer(private val detector: RoadDetector = RoadDetector()) {
    private var filteredOffset = 0f
    private var filteredCurve = 0f
    private var initialized = false

    fun analyze(frame: android.graphics.Bitmap): RoadState {
        val raw = detector.analyze(frame)
        if (!raw.detected || raw.confidence < .43f) {
            initialized = false
            return raw.copy(detected = false, direction = "UNKNOWN", confidence = 0f)
        }
        val alpha = if (initialized) .38f else 1f
        filteredOffset += alpha * (raw.centerOffset - filteredOffset)
        filteredCurve += alpha * (raw.curvature - filteredCurve)
        initialized = true
        val direction = when {
            filteredCurve > .055f -> "RIGHT CURVE"
            filteredCurve < -.055f -> "LEFT CURVE"
            else -> "STRAIGHT"
        }
        return raw.copy(centerOffset = filteredOffset, curvature = filteredCurve, direction = direction)
    }
}
