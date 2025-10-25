package com.example.autoclicker.service

import android.content.Context
import android.widget.Toast
import com.example.autoclicker.PlatformEventEmitter
import com.example.autoclicker.overlay.OverlayController

class AutoClickManager private constructor(private val context: Context) {

    @Volatile
    private var emitter: PlatformEventEmitter = object : PlatformEventEmitter {
        override fun emitEvent(event: Map<String, Any?>) {
            // no-op until Flutter side is ready
        }
    }

    private var service: AutoClickAccessibilityService? = null
    private var tapX: Int? = null
    private var tapY: Int? = null
    private var intervalMs: Long = 500
    private var isRunning: Boolean = false

    fun attachService(service: AutoClickAccessibilityService) {
        this.service = service
        emitRunningState()
        emitPermissions()
        if (isRunning && tapX != null && tapY != null) {
            service.startAutoClick(tapX!!, tapY!!, intervalMs)
        }
    }

    fun detachService() {
        service = null
        if (isRunning) {
            isRunning = false
            emitRunningState()
        }
        emitPermissions()
    }

    fun setPoint(x: Int, y: Int) {
        tapX = x
        tapY = y
    }

    fun updateInterval(interval: Long) {
        intervalMs = interval
    }

    fun start(x: Int, y: Int, interval: Long) {
        setPoint(x, y)
        updateInterval(interval)
        val svc = service
        if (svc == null) {
            Toast.makeText(context, "Enable accessibility service first", Toast.LENGTH_SHORT).show()
            emitPermissions()
            return
        }
        if (isRunning) return
        svc.startAutoClick(x, y, interval)
        isRunning = true
        emitRunningState()
    }

    fun stop() {
        val svc = service
        if (!isRunning) return
        isRunning = false
        svc?.stopAutoClick()
        emitRunningState()
    }

    fun notifyStoppedFromService() {
        if (isRunning) {
            isRunning = false
            emitRunningState()
        }
    }

    fun isAccessibilityEnabled(): Boolean = service != null

    fun currentPoint(): Pair<Int, Int>? {
        val x = tapX
        val y = tapY
        return if (x != null && y != null) Pair(x, y) else null
    }

    fun isRunning(): Boolean = isRunning

    fun currentInterval(): Long = intervalMs

    private fun emitRunningState() {
        emitter.emitEvent(mapOf("type" to "onRunningChanged", "isRunning" to isRunning))
    }

    fun emitPermissions() {
        emitter.emitEvent(
            mapOf(
                "type" to "onPermissionsChanged",
                "overlay" to OverlayController.isOverlayPermissionGranted(context),
                "accessibility" to isAccessibilityEnabled()
            )
        )
    }

    companion object {
        @Volatile
        private var instance: AutoClickManager? = null

        fun initialize(context: Context, emitter: PlatformEventEmitter): AutoClickManager {
            val manager = getOrCreate(context)
            manager.emitter = emitter
            manager.emitPermissions()
            return manager
        }

        fun getOrCreate(context: Context): AutoClickManager {
            return instance ?: synchronized(this) {
                instance ?: AutoClickManager(context.applicationContext).also { instance = it }
            }
        }

        fun get(): AutoClickManager {
            return instance ?: throw IllegalStateException("AutoClickManager not initialized")
        }
    }
}
