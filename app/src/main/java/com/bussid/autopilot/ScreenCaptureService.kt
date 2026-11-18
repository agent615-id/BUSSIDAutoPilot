package com.bussid.autopilot

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.WindowManager

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val detector = RoadDetector()
    private val engine = AutoPilotEngine()
    private var running = false
    private var lastFrameAt = 0L
    private lateinit var worker: Handler
    private var workerThread: HandlerThread? = null

    override fun onCreate() {
        super.onCreate()
        workerThread = HandlerThread("road-vision").also { it.start() }
        worker = Handler(workerThread!!.looper)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopCapture(); stopSelf(); return START_NOT_STICKY }
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification("Menunggu frame layar"), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification("Menunggu frame layar"))
        }
        val code = intent?.getIntExtra(EXTRA_RESULT, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (code != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(code, data)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopCapture(); stopSelf() }
            }, worker)
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
            val width = (metrics.widthPixels / 3).coerceAtLeast(320)
            val height = (metrics.heightPixels / 3).coerceAtLeast(180)
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            reader?.setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * image.width
                    val full = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
                    full.copyPixelsFromBuffer(buffer)
                    val frame = Bitmap.createBitmap(full, 0, 0, image.width, image.height)
                    full.recycle()
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastFrameAt > 120) {
                        lastFrameAt = now
                        val road = detector.analyze(frame)
                        val prefs = SettingsManager(this)
                        val decision = engine.process(road, prefs.sensitivity, prefs.maxSpeed)
                        PilotRuntime.latestRoad = road
                        PilotRuntime.latestDecision = decision
                        PilotRuntime.frameCount++
                        // Live actuation is opt-in and requires an enabled Accessibility service.
                        if (PilotRuntime.autoControlEnabled && FloatingControl.isConnected()) {
                            FloatingControl.send(decision, prefs)
                        }
                    }
                    frame.recycle()
                } catch (_: Exception) {
                    PilotRuntime.captureError = "Gagal memproses frame"
                } finally { image.close() }
            }, worker)
            display = projection?.createVirtualDisplay("BUSSID-AutoPilot", width, height,
                metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, worker)
            running = true
            PilotRuntime.captureActive = true
        } catch (e: Exception) {
            PilotRuntime.captureError = e.message ?: "Capture gagal"
            stopCapture(); stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun stopCapture() {
        running = false
        FloatingControl.stopAutoInput("Capture dihentikan")
        PilotRuntime.captureActive = false
        try { display?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        display = null; reader = null; projection = null
    }
    override fun onDestroy() {
        stopCapture()
        workerThread?.quitSafely()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "BUSSID Auto Pilot", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    private fun notification(text: String): Notification {
        val stop = Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP)
        val pi = PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setContentTitle("BUSSID Auto Pilot")
            .setContentText(text).setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(Notification.Action.Builder(null, "Stop", pi).build()).build()
    }
    companion object {
        const val ACTION_START = "com.bussid.autopilot.START_CAPTURE"
        const val ACTION_STOP = "com.bussid.autopilot.STOP_CAPTURE"
        const val EXTRA_RESULT = "resultCode"
        const val EXTRA_DATA = "resultData"
        const val CHANNEL = "bussid_capture"
        const val NOTIFICATION_ID = 45
    }
}

object PilotRuntime {
    @Volatile var captureActive = false
    @Volatile var accessibilityConnected = false
    @Volatile var autoControlEnabled = false
    @Volatile var lastInputStatus = "Input belum diuji"
    @Volatile private var gasGestureTested = false
    @Volatile private var brakeGestureTested = false
    @Volatile private var leftGestureTested = false
    @Volatile private var rightGestureTested = false
    fun markGestureTestCompleted(key: String) {
        when (key) {
            "gas" -> gasGestureTested = true
            "brake" -> brakeGestureTested = true
            "left" -> leftGestureTested = true
            "right" -> rightGestureTested = true
        }
    }
    fun invalidateGestureTest(key: String) {
        when (key) {
            "gas" -> gasGestureTested = false
            "brake" -> brakeGestureTested = false
            "left" -> leftGestureTested = false
            "right" -> rightGestureTested = false
        }
        autoControlEnabled = false
    }
    fun clearGestureTests() {
        gasGestureTested = false; brakeGestureTested = false
        leftGestureTested = false; rightGestureTested = false
        autoControlEnabled = false
    }
    fun allGestureTestsCompleted() = gasGestureTested && brakeGestureTested && leftGestureTested && rightGestureTested
    @Volatile var captureError: String? = null
    @Volatile var latestRoad = RoadState(false, 0f, 0f, "Menunggu frame", 0f, -1, -1)
    @Volatile var latestDecision = PilotDecision(0f, 0f, 0f, "Standby")
    @Volatile var frameCount = 0L
}
