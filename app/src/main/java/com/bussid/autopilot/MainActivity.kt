package com.bussid.autopilot

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.graphics.Color
import android.content.Context

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val captureRequest = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }

        val title = TextView(this).apply {
            text = "BUSSID AUTO PILOT"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "Status: Belum terhubung"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 24)
        }
        val captureButton = Button(this).apply {
            text = "MULAI SCREEN CAPTURE"
            setOnClickListener { requestScreenCapture() }
        }
        val note = TextView(this).apply {
            text = "Versi awal hanya menguji izin pembacaan layar. Auto steering dan auto brake belum aktif."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(12, 18, 12, 0)
        }

        root.addView(title)
        root.addView(status)
        root.addView(captureButton)
        root.addView(note)
        setContentView(root)
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
    }

    @Deprecated("Deprecated by Android; kept for broad compatibility in this starter project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest) {
            status.text = if (resultCode == RESULT_OK && data != null) {
                "Status: Izin screen capture diberikan. Tahap capture service berikutnya."
            } else {
                "Status: Izin screen capture ditolak."
            }
        }
    }
}
