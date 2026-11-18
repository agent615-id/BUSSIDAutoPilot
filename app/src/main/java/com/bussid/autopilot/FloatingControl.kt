package com.bussid.autopilot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Accessibility gesture adapter. Coordinates are normalized against the current display.
 * A single GestureDescription can contain simultaneous strokes, but OEM/game behavior varies.
 */
class FloatingControl : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        PilotRuntime.accessibilityConnected = true
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopAutoInput("Accessibility interrupted") }
    override fun onDestroy() {
        stopAutoInput("Accessibility disconnected")
        if (instance === this) instance = null
        PilotRuntime.accessibilityConnected = false
        super.onDestroy()
    }

    fun testTouch(xNorm: Float, yNorm: Float, durationMs: Long = 180L, callback: ((Boolean) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < 24) { callback?.invoke(false); return }
        val x = xNorm.coerceIn(0f, 1f) * resources.displayMetrics.widthPixels
        val y = yNorm.coerceIn(0f, 1f) * resources.displayMetrics.heightPixels
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(80, 800))
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { callback?.invoke(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { callback?.invoke(false) }
        }, null)
    }

    /** Called only after the user explicitly enables live control. */
    fun dispatchDecision(decision: PilotDecision, settings: SettingsManager) {
        if (!PilotRuntime.autoControlEnabled || !PilotRuntime.captureActive || inFlight.get()) return
        if (System.currentTimeMillis() - lastDispatch < 260L) return
        lastDispatch = System.currentTimeMillis()
        val width = resources.displayMetrics.widthPixels.toFloat()
        val height = resources.displayMetrics.heightPixels.toFloat()
        val duration = 260L
        val builder = GestureDescription.Builder()
        var strokes = 0
        fun add(xn: Float, yn: Float) {
            val path = Path().apply { moveTo(xn.coerceIn(0f, 1f) * width, yn.coerceIn(0f, 1f) * height) }
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            strokes++
        }
        // Gas or brake are mutually exclusive. Steering is a second simultaneous stroke.
        if (decision.brake > .20f) add(settings.brakeX, settings.brakeY)
        else if (decision.throttle > .15f) add(settings.gasX, settings.gasY)
        when {
            decision.steering < -.12f -> add(settings.steerLeftX, settings.steerLeftY)
            decision.steering > .12f -> add(settings.steerRightX, settings.steerRightY)
        }
        if (strokes == 0) return
        inFlight.set(true)
        val accepted = dispatchGesture(builder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { inFlight.set(false); PilotRuntime.lastInputStatus = "Gesture terkirim" }
            override fun onCancelled(gestureDescription: GestureDescription?) { inFlight.set(false); PilotRuntime.lastInputStatus = "Gesture dibatalkan" }
        }, Handler(Looper.getMainLooper()))
        if (!accepted) { inFlight.set(false); PilotRuntime.lastInputStatus = "Gesture ditolak Android" }
    }

    companion object {
        @Volatile private var instance: FloatingControl? = null
        private val inFlight = AtomicBoolean(false)
        @Volatile private var lastDispatch = 0L
        fun isConnected() = instance != null
        fun scheduleTest(x: Float, y: Float, durationMs: Long = 180L, callback: ((Boolean) -> Unit)? = null) {
            val service = instance
            if (service == null) { callback?.invoke(false); return }
            PilotRuntime.lastInputStatus = "Menunggu BUSSID siap untuk uji gesture"
            Handler(Looper.getMainLooper()).postDelayed({
                if (instance === service) service.testTouch(x, y, durationMs, callback)
                else callback?.invoke(false)
            }, 2800L)
        }
        fun stopAutoInput(reason: String = "Input dihentikan") {
            PilotRuntime.autoControlEnabled = false
            PilotRuntime.lastInputStatus = reason
            inFlight.set(false)
        }
        fun send(decision: PilotDecision, settings: SettingsManager) { instance?.dispatchDecision(decision, settings) }
    }
}
