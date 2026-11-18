package com.bussid.autopilot

import kotlin.math.abs

data class SpeedDecision(val throttle: Float, val brake: Float, val note: String)

/** Curve-aware conservative command. maxSpeed remains a preference until game speed telemetry exists. */
class SpeedController {
    fun decide(state: RoadState, maxSpeed: Int): SpeedDecision {
        if (!state.detected || state.confidence < .43f)
            return SpeedDecision(0f, 0f, "Fail-safe: deteksi jalan tidak valid; gas dan rem dilepas")
        val bend = abs(state.curvature)
        val confidenceScale = state.confidence.coerceIn(0f, 1f)
        return when {
            bend > .24f -> SpeedDecision(0f, .24f, "Kurva tajam: kurangi akselerasi")
            bend > .13f -> SpeedDecision(.08f, .12f, "Kurva sedang: gas rendah")
            bend > .065f -> SpeedDecision(.16f, 0f, "Kurva ringan: gas dibatasi")
            else -> SpeedDecision(.22f * confidenceScale, 0f,
                "Jalan relatif lurus • target $maxSpeed km/jam belum dapat diverifikasi tanpa telemetry")
        }
    }
}
