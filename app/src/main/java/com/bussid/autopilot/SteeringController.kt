package com.bussid.autopilot

class SteeringController {
    fun steering(state: RoadState, sensitivity: Float): Float {
        if (!state.detected) return 0f
        // Road center to the right => steer right; curvature contributes feed-forward.
        return (state.centerOffset * sensitivity + state.curvature * .65f)
            .coerceIn(-1f, 1f)
    }
}
