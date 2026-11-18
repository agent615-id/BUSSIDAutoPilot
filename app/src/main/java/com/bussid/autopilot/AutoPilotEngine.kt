package com.bussid.autopilot

data class PilotDecision(val steering: Float, val throttle: Float, val brake: Float, val status: String)

class AutoPilotEngine(
    private val steeringController: SteeringController = SteeringController(),
    private val speedController: SpeedController = SpeedController(),
    private val minimumConfidence: Float = .43f
) {
    @Synchronized fun process(road: RoadState, sensitivity: Float, maxSpeed: Int): PilotDecision {
        if (!road.detected || road.confidence < minimumConfidence) {
            steeringController.reset()
            return PilotDecision(0f, 0f, 0f, "FAIL-SAFE • UNKNOWN • input diblokir")
        }
        val speed = speedController.decide(road, maxSpeed)
        return PilotDecision(steeringController.steering(road, sensitivity), speed.throttle,
            speed.brake, "${road.direction} • confidence ${(road.confidence * 100).toInt()}%")
    }
}
