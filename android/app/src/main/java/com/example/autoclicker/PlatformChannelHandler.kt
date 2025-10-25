package com.example.autoclicker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.example.autoclicker.overlay.OverlayController
import com.example.autoclicker.overlay.PointPickerOverlay
import com.example.autoclicker.service.AutoClickManager
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

interface PlatformEventEmitter {
    fun emitEvent(event: Map<String, Any?>)
}

class PlatformChannelHandler(
    private val activity: Activity
) : MethodChannel.MethodCallHandler, EventChannel.StreamHandler, PlatformEventEmitter {

    companion object {
        const val METHOD_CHANNEL = "autoclicker/channel"
        const val EVENT_CHANNEL = "autoclicker/events"
    }

    private val context: Context = activity.applicationContext
    private val overlayController: OverlayController
    private val pointPickerOverlay: PointPickerOverlay
    private val autoClickManager: AutoClickManager = AutoClickManager.initialize(context, this)
    private var eventSink: EventChannel.EventSink? = null
    private var pointEditingEnabled: Boolean = true

    init {
        pointPickerOverlay = PointPickerOverlay(context, this)
        overlayController = OverlayController(context, autoClickManager, pointPickerOverlay)
        overlayController.setPointEditingEnabled(pointEditingEnabled)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "requestOverlayPermission" -> {
                requestOverlayPermission()
                result.success(null)
            }
            "isOverlayPermissionGranted" -> {
                result.success(OverlayController.isOverlayPermissionGranted(context))
            }
            "openAccessibilitySettings" -> {
                openAccessibilitySettings()
                result.success(null)
            }
            "startAutoClick" -> {
                val x = call.argument<Int>("x")
                val y = call.argument<Int>("y")
                val interval = call.argument<Int>("intervalMs")
                if (x != null && y != null && interval != null) {
                    autoClickManager.start(x, y, interval.toLong())
                }
                result.success(null)
            }
            "stopAutoClick" -> {
                autoClickManager.stop()
                result.success(null)
            }
            "showOverlay" -> {
                overlayController.showBubble()
                result.success(null)
            }
            "hideOverlay" -> {
                overlayController.hideBubble()
                result.success(null)
            }
            "setPointEditingEnabled" -> {
                val enabled = call.argument<Boolean>("enabled") ?: true
                setPointEditingEnabled(enabled)
                result.success(null)
            }
            "enterPointPickMode" -> {
                enterPointPickMode()
                result.success(null)
            }
            "setTapPoint" -> {
                val x = call.argument<Int>("x")
                val y = call.argument<Int>("y")
                if (x != null && y != null) {
                    autoClickManager.setPoint(x, y)
                }
                result.success(null)
            }
            "updateInterval" -> {
                val interval = call.argument<Int>("intervalMs")
                if (interval != null) {
                    autoClickManager.updateInterval(interval.toLong())
                }
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        eventSink = events
        emitPermissions()
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }

    override fun emitEvent(event: Map<String, Any?>) {
        when (event["type"]) {
            "onRunningChanged" -> {
                val running = event["isRunning"] as? Boolean ?: false
                overlayController.updateRunningState(running)
            }
            "onPointPicked" -> {
                val x = event["x"] as? Int
                val y = event["y"] as? Int
                if (x != null && y != null) {
                    autoClickManager.setPoint(x, y)
                }
            }
        }
        eventSink?.success(event)
    }

    fun emitPermissions() {
        emitEvent(
            mapOf(
                "type" to "onPermissionsChanged",
                "overlay" to OverlayController.isOverlayPermissionGranted(context),
                "accessibility" to autoClickManager.isAccessibilityEnabled()
            )
        )
    }

    fun checkOverlayPermission() {
        emitPermissions()
    }

    private fun requestOverlayPermission() {
        if (OverlayController.isOverlayPermissionGranted(context)) {
            Toast.makeText(context, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
            emitPermissions()
            return
        }
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
    }

    private fun setPointEditingEnabled(enabled: Boolean) {
        val previous = pointEditingEnabled
        pointEditingEnabled = enabled
        overlayController.setPointEditingEnabled(enabled)
        pointPickerOverlay.setEditingEnabled(enabled)
        if (!enabled && previous) {
            Toast.makeText(context, "Tap point locked while running", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enterPointPickMode() {
        if (!OverlayController.isOverlayPermissionGranted(context)) {
            Toast.makeText(context, "Overlay permission required", Toast.LENGTH_SHORT).show()
            return
        }
        if (!pointEditingEnabled) {
            Toast.makeText(context, "Stop first to reposition", Toast.LENGTH_SHORT).show()
            return
        }
        pointPickerOverlay.show(autoClickManager.currentPoint())
    }
}
