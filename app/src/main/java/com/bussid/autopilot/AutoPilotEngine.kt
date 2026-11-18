package com.bussid.autopilot

data class PilotDecision(val steering: Float, val throttle: Float, val brake: Float, val status: String)

class AutoPilotEngine(
    private val steeringController: SteeringController = SteeringController(),
    private val speedController: SpeedController = SpeedController()
) {
    fun process(road: RoadState, sensitivity: Float, maxSpeed: Int): PilotDecision {
        val speed = speedController.decide(road, maxSpeed)
        return PilotDecision(
            steeringController.steering(road, sensitivity),
            speed.throttle, speed.brake,
            if (road.detected) "${road.direction} • confidence ${(road.confidence * 100).toInt()}%" else road.direction
        )
    }
}
