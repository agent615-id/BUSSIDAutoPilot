package com.bussid.autopilot

import kotlin.math.abs

/** Bounded PID + curvature feed-forward. Output is normalized to [-1, 1]. */
class SteeringController {
    private var integral = 0f
    private var previousError = 0f
    private var previousAt = 0L

    @Synchronized fun steering(state: RoadState, sensitivity: Float): Float {
        if (!state.detected || state.confidence < .43f) { reset(); return 0f }
        val now = System.nanoTime()
        val dt = if (previousAt == 0L) .12f else ((now - previousAt) / 1_000_000_000f).coerceIn(.04f, .35f)
        val error = state.centerOffset
        integral = (integral + error * dt).coerceIn(-.35f, .35f)
        val derivative = (error - previousError) / dt
        previousError = error
        previousAt = now
        val pid = .78f * error + .10f * integral + .035f * derivative
        val feedForward = state.curvature * .34f
        val raw = (pid + feedForward) * sensitivity.coerceIn(.1f, 1.5f)
        // Deadband and slew limit reduce oscillation/abrupt reversals.
        val target = if (abs(raw) < .055f) 0f else raw.coerceIn(-.72f, .72f)
        return target
    }

    @Synchronized fun reset() { integral = 0f; previousError = 0f; previousAt = 0L }
}
