package com.bussid.autopilot

data class SpeedDecision(val throttle: Float, val brake: Float, val note: String)

class SpeedController {
    fun decide(state: RoadState, maxSpeed: Int): SpeedDecision {
        if (!state.detected) return SpeedDecision(0f, .35f, "Jalan tidak terbaca: gas dilepas")
        val bend = kotlin.math.abs(state.curvature)
        return when {
            bend > .13f -> SpeedDecision(.15f, .30f, "Tikungan tajam: kurangi kecepatan")
            bend > .065f -> SpeedDecision(.25f, 0f, "Tikungan: gas dibatasi")
            else -> SpeedDecision(.35f, 0f, "Jalan relatif lurus • gas dibatasi tanpa telemetry speed")
        }
    }
}
