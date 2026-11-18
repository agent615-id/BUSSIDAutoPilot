package com.bussid.autopilot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private val requestCapture = 1001
    private lateinit var status: TextView
    private lateinit var road: TextView
    private lateinit var steering: TextView
    private lateinit var controls: TextView
    private lateinit var inputStatus: TextView
    private lateinit var prefs: SettingsManager
    private lateinit var autoSwitch: Switch
    private lateinit var verifiedControls: CheckBox
    private val uiHandler = Handler(Looper.getMainLooper())
    private val refreshTask = object : Runnable {
        override fun run() { refresh(); uiHandler.postDelayed(this, 500L) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SettingsManager(this)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 18, 22, 22)
            setBackgroundColor(Color.rgb(11, 18, 32))
        }
        root.addView(TextView(this).apply {
            text = "BUSSID  /  AUTO PILOT"
            textSize = 25f; setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        status = label("Capture: belum aktif")
        road = label("Road detection: menunggu")
        steering = label("Steering: standby")
        controls = label("Gas: —     Rem: —")
        inputStatus = label("Input: belum diuji")
        root.addView(status); root.addView(road); root.addView(steering); root.addView(controls); root.addView(inputStatus)

        root.addView(button("MULAI SCREEN CAPTURE") { requestCapture() })
        root.addView(button("EMERGENCY STOP • MATIKAN INPUT") {
            FloatingControl.stopAutoInput("EMERGENCY STOP oleh pengguna")
            if (::autoSwitch.isInitialized) autoSwitch.isChecked = false
            refresh()
        })
        root.addView(button("STOP CAPTURE & INPUT") {
            FloatingControl.stopAutoInput("Dihentikan pengguna")
            stopService(Intent(this, ScreenCaptureService::class.java))
            PilotRuntime.captureActive = false
            refresh()
        })
        root.addView(button("BUKA PENGATURAN ACCESSIBILITY") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        root.addView(section("MODE KONTROL"))
        autoSwitch = Switch(this).apply {
            text = "Aktifkan input otomatis (eksperimental)"
            setTextColor(Color.WHITE)
            isChecked = false
            setOnCheckedChangeListener { _, checked ->
                if (!checked) {
                    FloatingControl.stopAutoInput("Input otomatis dimatikan")
                    refresh()
                    return@setOnCheckedChangeListener
                }
                val allowed = PilotRuntime.captureActive && FloatingControl.isConnected() &&
                    verifiedControls.isChecked && PilotRuntime.allGestureTestsCompleted()
                PilotRuntime.autoControlEnabled = allowed
                if (!allowed) {
                    isChecked = false
                    FloatingControl.stopAutoInput("Belum siap: capture, Accessibility, dan verifikasi gesture wajib aktif")
                    Toast.makeText(this@MainActivity, "Selesaikan 4 uji gesture, centang verifikasi, lalu pastikan capture dan Accessibility aktif", Toast.LENGTH_LONG).show()
                } else {
                    PilotRuntime.lastInputStatus = "Kontrol otomatis aktif — uji di area aman"
                }
                refresh()
            }
        }
        root.addView(autoSwitch)
        verifiedControls = CheckBox(this).apply {
            text = "Saya sudah melihat dan memastikan keempat tombol BUSSID bereaksi benar"
            setTextColor(Color.WHITE)
            isChecked = false
        }
        verifiedControls.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                FloatingControl.stopAutoInput("Verifikasi kontrol dibatalkan")
                if (::autoSwitch.isInitialized) autoSwitch.isChecked = false
            }
        }
        root.addView(verifiedControls)
        root.addView(label("Mode aman: input otomatis selalu OFF saat aplikasi dibuka. Gunakan uji satu tombol sebelum mengaktifkannya."))

        root.addView(section("PENGATURAN KENDARAAN"))
        val sensitivityLabel = label("")
        root.addView(sensitivityLabel)
        root.addView(SeekBar(this).apply {
            max = 140; progress = ((prefs.sensitivity - .1f) / 1.4f * 140).toInt().coerceIn(0, 140)
            setOnSeekBarChangeListener(listener { p ->
                prefs.sensitivity = .1f + p / 100f
                sensitivityLabel.text = "Sensitivitas kemudi: ${fmt(prefs.sensitivity)}"
            })
        })
        val speedLabel = label("")
        root.addView(speedLabel)
        root.addView(SeekBar(this).apply {
            max = 130; progress = prefs.maxSpeed - 20
            setOnSeekBarChangeListener(listener { p ->
                prefs.maxSpeed = p + 20
                speedLabel.text = "Batas kecepatan target: ${prefs.maxSpeed} km/jam (belum ada pembacaan speedometer)"
            })
        })
        sensitivityLabel.text = "Sensitivitas kemudi: ${fmt(prefs.sensitivity)}"
        speedLabel.text = "Batas kecepatan target: ${prefs.maxSpeed} km/jam (belum ada pembacaan speedometer)"

        root.addView(section("KALIBRASI POSISI TOMBOL BUSSID"))
        root.addView(label("Nilai X/Y adalah persentase posisi layar (0–100%). Atur sesuai layout kontrol game dan orientasi landscape. Simpan otomatis."))
        addCalibration(root, "Gas", "gas", { prefs.gasX }, { prefs.gasX = it }, { prefs.gasY }, { prefs.gasY = it })
        addTestButton(root, "gas", "UJI GAS (180 ms)", { prefs.gasX to prefs.gasY })
        addCalibration(root, "Rem", "brake", { prefs.brakeX }, { prefs.brakeX = it }, { prefs.brakeY }, { prefs.brakeY = it })
        addTestButton(root, "brake", "UJI REM (180 ms)", { prefs.brakeX to prefs.brakeY })
        addCalibration(root, "Kemudi kiri", "left", { prefs.steerLeftX }, { prefs.steerLeftX = it }, { prefs.steerLeftY }, { prefs.steerLeftY = it })
        addTestButton(root, "left", "UJI KEMUDI KIRI (180 ms)", { prefs.steerLeftX to prefs.steerLeftY })
        addCalibration(root, "Kemudi kanan", "right", { prefs.steerRightX }, { prefs.steerRightX = it }, { prefs.steerRightY }, { prefs.steerRightY = it })
        addTestButton(root, "right", "UJI KEMUDI KANAN (180 ms)", { prefs.steerRightX to prefs.steerRightY })
        root.addView(button("RESET KALIBRASI DEFAULT") {
            prefs.gasX = .91f; prefs.gasY = .78f
            prefs.brakeX = .82f; prefs.brakeY = .78f
            prefs.steerLeftX = .10f; prefs.steerLeftY = .80f
            prefs.steerRightX = .22f; prefs.steerRightY = .80f
            PilotRuntime.clearGestureTests()
            buildUi()
        })
        root.addView(label("PENTING: Uji gesture satu per satu di area kosong/garasi. Pastikan titik kalibrasi tepat pada tombol game. Jangan menguji saat bus melaju di jalan atau lalu lintas."))

        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }

    private fun addCalibration(parent: LinearLayout, name: String, key: String,
        getX: () -> Float, setX: (Float) -> Unit, getY: () -> Float, setY: (Float) -> Unit) {
        val heading = label("$name • X ${pct(getX())} / Y ${pct(getY())}")
        parent.addView(heading)
        parent.addView(SeekBar(this).apply {
            max = 100; progress = (getX() * 100).toInt()
            setOnSeekBarChangeListener(listener { p -> setX(p / 100f); PilotRuntime.invalidateGestureTest(key); heading.text = "$name • X ${pct(getX())} / Y ${pct(getY())}" })
        })
        parent.addView(SeekBar(this).apply {
            max = 100; progress = (getY() * 100).toInt()
            setOnSeekBarChangeListener(listener { p -> setY(p / 100f); PilotRuntime.invalidateGestureTest(key); heading.text = "$name • X ${pct(getX())} / Y ${pct(getY())}" })
        })
    }

    private fun addTestButton(parent: LinearLayout, key: String, title: String, point: () -> Pair<Float, Float>) {
        parent.addView(button(title) {
            if (!FloatingControl.isConnected()) {
                Toast.makeText(this, "Accessibility service belum aktif", Toast.LENGTH_LONG).show()
                return@button
            }
            val gameIntent = packageManager.getLaunchIntentForPackage(BUSSID_PACKAGE)
            if (gameIntent == null) {
                Toast.makeText(this, "BUSSID tidak ditemukan. Pastikan game terpasang.", Toast.LENGTH_LONG).show()
                return@button
            }
            val (x, y) = point()
            inputStatus.text = "Input: membuka BUSSID; uji $title dijadwalkan 2,8 detik lagi"
            FloatingControl.scheduleTest(x, y, 180L) { ok ->
                if (ok) PilotRuntime.markGestureTestCompleted(key)
                PilotRuntime.lastInputStatus = if (ok) "Gesture selesai di Android — periksa reaksi tombol BUSSID" else "Gesture gagal/dibatalkan"
            }
            startActivity(gameIntent)
            Toast.makeText(this, "BUSSID dibuka. Gesture ${title.lowercase(Locale.getDefault())} dikirim setelah 2,8 detik.", Toast.LENGTH_LONG).show()
        })
    }

    private fun listener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { onChange(progress) }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun button(textValue: String, action: () -> Unit) = Button(this).apply {
        text = textValue; setOnClickListener { action() }
    }
    private fun section(textValue: String) = TextView(this).apply {
        text = textValue; textSize = 16f; setTextColor(Color.rgb(95, 165, 255))
        typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, 20, 0, 6)
    }
    private fun label(value: String) = TextView(this).apply {
        text = value; textSize = 13f; setTextColor(Color.rgb(190, 205, 225)); setPadding(0, 5, 0, 5)
    }
    private fun pct(value: Float) = "${(value * 100).toInt()}%"
    private fun fmt(value: Float) = String.format(Locale.US, "%.2f", value)

    companion object { private const val BUSSID_PACKAGE = "com.maleo.bussimulatorid" }

    private fun requestCapture() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), requestCapture)
    }
    @Deprecated("Legacy result callback retained for minSdk 26")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestCapture && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT, resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, data)
            }
            startForegroundService(service)
            refresh()
        }
    }
    override fun onResume() { super.onResume(); uiHandler.removeCallbacks(refreshTask); uiHandler.post(refreshTask) }
    override fun onPause() { uiHandler.removeCallbacks(refreshTask); super.onPause() }
    private fun refresh() {
        if (!::status.isInitialized) return
        status.text = if (PilotRuntime.captureActive) "Capture: aktif • ${PilotRuntime.frameCount} frame" else "Capture: belum aktif"
        val currentRoad = PilotRuntime.latestRoad
        road.text = "Road: ${currentRoad.direction} • confidence ${(currentRoad.confidence * 100).toInt()}% • center X ${if (currentRoad.centerX >= 0f) currentRoad.centerX.toInt() else "—"} px"
        steering.text = "Steering estimate: ${fmt(PilotRuntime.latestDecision.steering)}"
        controls.text = "${PilotRuntime.latestDecision.status}\nGas: ${(PilotRuntime.latestDecision.throttle * 100).toInt()}% • Rem: ${(PilotRuntime.latestDecision.brake * 100).toInt()}% • Target: ${prefs.maxSpeed} km/jam (tanpa telemetry)"
        inputStatus.text = "Accessibility: ${if (FloatingControl.isConnected()) "terhubung" else "belum aktif"} • ${PilotRuntime.lastInputStatus}" + (PilotRuntime.captureError?.let { " • $it" } ?: "")
        if (::autoSwitch.isInitialized && (!PilotRuntime.captureActive || !FloatingControl.isConnected() || !PilotRuntime.autoControlEnabled)) {
            PilotRuntime.autoControlEnabled = false
            autoSwitch.isChecked = false
        }
    }
}
