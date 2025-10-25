package com.example.autoclicker.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import com.example.autoclicker.PlatformEventEmitter
import kotlin.math.roundToInt

class PointPickerOverlay(
    private val context: Context,
    private val emitter: PlatformEventEmitter
) {

    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var container: FrameLayout? = null
    private var crosshair: View? = null
    private var crosshairParams: FrameLayout.LayoutParams? = null
    private var editingEnabled: Boolean = true

    fun show(initialPoint: Pair<Int, Int>?) {
        if (!editingEnabled) {
            Toast.makeText(context, "Stop first to reposition", Toast.LENGTH_SHORT).show()
            return
        }
        if (container != null) return
        val overlayParams = createLayoutParams()
        val frame = FrameLayout(context)
        frame.setBackgroundColor(Color.parseColor("#55000000"))
        val size = (context.resources.displayMetrics.density * 72).roundToInt()
        val crosshairView = CrosshairView(context)
        val params = FrameLayout.LayoutParams(size, size)
        params.gravity = Gravity.TOP or Gravity.START
        if (initialPoint != null) {
            params.leftMargin = initialPoint.first - size / 2
            params.topMargin = initialPoint.second - size / 2
        } else {
            val displayMetrics = context.resources.displayMetrics
            params.leftMargin = (displayMetrics.widthPixels - size) / 2
            params.topMargin = (displayMetrics.heightPixels - size) / 2
        }
        clampParams(params)
        crosshairView.layoutParams = params
        setupDrag(crosshairView)
        frame.addView(crosshairView)
        container = frame
        crosshair = crosshairView
        crosshairParams = params
        windowManager.addView(frame, overlayParams)
    }

    fun hide() {
        container?.let { windowManager.removeView(it) }
        container = null
        crosshair = null
        crosshairParams = null
    }

    fun setEditingEnabled(enabled: Boolean) {
        editingEnabled = enabled
        if (!enabled) {
            hide()
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun setupDrag(view: View) {
        var lastRawX = 0f
        var lastRawY = 0f
        view.setOnTouchListener { _, event ->
            val params = crosshairParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastRawX
                    val dy = event.rawY - lastRawY
                    params.leftMargin += dx.toInt()
                    params.topMargin += dy.toInt()
                    clampParams(params)
                    crosshair?.layoutParams = params
                    container?.invalidate()
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    finalizeSelection()
                    true
                }
                else -> false
            }
        }
    }

    private fun clampParams(params: FrameLayout.LayoutParams) {
        val size = params.width
        val displayMetrics = context.resources.displayMetrics
        params.leftMargin = params.leftMargin.coerceIn(0, displayMetrics.widthPixels - size)
        params.topMargin = params.topMargin.coerceIn(0, displayMetrics.heightPixels - size)
    }

    private fun finalizeSelection() {
        val params = crosshairParams ?: return
        val centerX = params.leftMargin + params.width / 2
        val centerY = params.topMargin + params.height / 2
        crosshair?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        vibrate()
        Toast.makeText(context, "Tap point saved ($centerX, $centerY)", Toast.LENGTH_SHORT).show()
        emitter.emitEvent(
            mapOf(
                "type" to "onPointPicked",
                "x" to centerX,
                "y" to centerY
            )
        )
        hide()
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }

    private class CrosshairView(context: Context) : View(context) {
        private val paint = android.graphics.Paint().apply {
            color = Color.WHITE
            strokeWidth = context.resources.displayMetrics.density * 2
            style = android.graphics.Paint.Style.STROKE
            isAntiAlias = true
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val width = canvas.width.toFloat()
            val height = canvas.height.toFloat()
            val halfWidth = width / 2
            val halfHeight = height / 2
            canvas.drawCircle(halfWidth, halfHeight, width / 2.5f, paint)
            canvas.drawLine(halfWidth, 0f, halfWidth, height, paint)
            canvas.drawLine(0f, halfHeight, width, halfHeight, paint)
        }
    }
}
