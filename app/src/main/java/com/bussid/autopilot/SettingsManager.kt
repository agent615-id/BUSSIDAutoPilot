package com.bussid.autopilot

import android.content.Context

/** Persistent display-relative (0..1) calibration for BUSSID touch controls. */
class SettingsManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("autopilot_settings", Context.MODE_PRIVATE)
    var sensitivity: Float
        get() = prefs.getFloat("sensitivity", .65f)
        set(v) { prefs.edit().putFloat("sensitivity", v.coerceIn(.1f, 1.5f)).apply() }
    var maxSpeed: Int
        get() = prefs.getInt("max_speed", 80)
        set(v) { prefs.edit().putInt("max_speed", v.coerceIn(20, 150)).apply() }

    var gasX: Float get() = get("gas_x", .91f); set(v) = put("gas_x", v)
    var gasY: Float get() = get("gas_y", .78f); set(v) = put("gas_y", v)
    var brakeX: Float get() = get("brake_x", .82f); set(v) = put("brake_x", v)
    var brakeY: Float get() = get("brake_y", .78f); set(v) = put("brake_y", v)
    var steerLeftX: Float get() = get("steer_left_x", .10f); set(v) = put("steer_left_x", v)
    var steerLeftY: Float get() = get("steer_left_y", .80f); set(v) = put("steer_left_y", v)
    var steerRightX: Float get() = get("steer_right_x", .22f); set(v) = put("steer_right_x", v)
    var steerRightY: Float get() = get("steer_right_y", .80f); set(v) = put("steer_right_y", v)

    private fun get(key: String, default: Float) = prefs.getFloat(key, default)
    private fun put(key: String, value: Float) { prefs.edit().putFloat(key, value.coerceIn(0f, 1f)).apply() }
}
