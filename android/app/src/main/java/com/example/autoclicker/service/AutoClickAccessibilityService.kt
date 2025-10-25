package com.example.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class AutoClickAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var tapX: Int = 0
    private var tapY: Int = 0
    private var intervalMs: Long = 500

    override fun onServiceConnected() {
        super.onServiceConnected()
        AutoClickManager.getOrCreate(applicationContext).attachService(this)
        Toast.makeText(applicationContext, "AutoClick service connected", Toast.LENGTH_SHORT).show()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        stopAutoClick()
        AutoClickManager.getOrCreate(applicationContext).detachService()
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        stopAutoClick()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoClick()
        AutoClickManager.getOrCreate(applicationContext).detachService()
    }

    fun startAutoClick(x: Int, y: Int, interval: Long) {
        tapX = x
        tapY = y
        intervalMs = interval
        if (isRunning) return
        isRunning = true
        performTap()
    }

    fun stopAutoClick() {
        if (!isRunning) return
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        AutoClickManager.getOrCreate(applicationContext).notifyStoppedFromService()
    }

    private fun performTap() {
        if (!isRunning) return
        val path = Path().apply { moveTo(tapX.toFloat(), tapY.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                scheduleNext()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                scheduleNext()
            }
        }, null)

        if (!dispatched) {
            scheduleNext()
        }
    }

    private fun scheduleNext() {
        if (!isRunning) return
        handler.postDelayed({ performTap() }, intervalMs)
    }
}
