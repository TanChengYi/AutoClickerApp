package com.example.autoclicker.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.autoclicker.R
import com.example.autoclicker.service.AutoClickManager

class OverlayController(
    private val context: Context,
    private val autoClickManager: AutoClickManager,
    private val pointPickerOverlay: PointPickerOverlay
) {

    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var pointEditingEnabled: Boolean = true

    fun showBubble() {
        if (!isOverlayPermissionGranted(context) || bubbleView != null) return
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.overlay_bubble, null)
        val params = createLayoutParams()
        windowManager.addView(view, params)
        bubbleView = view
        bubbleParams = params
        setupBubbleInteractions(view)
        updateRunningState(autoClickManager.isRunning())
    }

    fun hideBubble() {
        bubbleView?.let { windowManager.removeView(it) }
        panelView?.let { windowManager.removeView(it) }
        bubbleView = null
        panelView = null
    }

    fun setPointEditingEnabled(enabled: Boolean) {
        val previous = pointEditingEnabled
        pointEditingEnabled = enabled
        if (enabled && !previous) {
            Toast.makeText(context, "Point editing unlocked", Toast.LENGTH_SHORT).show()
        }
        panelView?.findViewById<View>(R.id.panel_reposition)?.isEnabled =
            pointEditingEnabled && !autoClickManager.isRunning()
    }

    fun updateRunningState(isRunning: Boolean) {
        val bubble = bubbleView ?: return
        val indicator = bubble.findViewById<ImageView>(R.id.bubble_icon)
        indicator.isSelected = isRunning
        indicator.alpha = if (isRunning) 1f else 0.85f
        indicator.setBackgroundResource(if (isRunning) R.drawable.ic_bubble_on else R.drawable.ic_bubble_off)
    }

    private fun showPanel() {
        if (panelView != null) return
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.overlay_panel, null)
        val params = createPanelLayoutParams()
        bubbleParams?.let {
            params.x = it.x
            params.y = it.y + it.height
        }
        windowManager.addView(view, params)
        panelView = view
        panelParams = params
        bindPanelActions(view)
    }

    private fun hidePanel() {
        panelView?.let { windowManager.removeView(it) }
        panelView = null
        panelParams = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBubbleInteractions(view: View) {
        val gestureDetector = OverlayGestureDetector()
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    gestureDetector.start(event.rawX, event.rawY)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    gestureDetector.detectMove(event.rawX, event.rawY)
                    return@setOnTouchListener if (gestureDetector.isDragging) {
                        updateBubblePosition(event.rawX, event.rawY)
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (gestureDetector.isClick(event.rawX, event.rawY)) {
                        toggleStartStop()
                    }
                    gestureDetector.reset()
                    true
                }
                else -> false
            }
        }
        view.setOnLongClickListener {
            showPanel()
            true
        }
    }

    private fun bindPanelActions(view: View) {
        val statusText = view.findViewById<TextView>(R.id.panel_status)
        val toggleButton = view.findViewById<View>(R.id.panel_toggle)
        val repositionButton = view.findViewById<View>(R.id.panel_reposition)
        statusText.text = if (autoClickManager.isRunning()) "Running" else "Stopped"
        if (toggleButton is android.widget.Button) {
            toggleButton.text = if (autoClickManager.isRunning()) "Stop" else "Start"
        }
        repositionButton.isEnabled = pointEditingEnabled && !autoClickManager.isRunning()
        toggleButton.setOnClickListener {
            toggleStartStop()
            hidePanel()
        }
        repositionButton.setOnClickListener {
            if (autoClickManager.isRunning() || !pointEditingEnabled) {
                Toast.makeText(context, "Locked while running", Toast.LENGTH_SHORT).show()
            } else {
                pointPickerOverlay.show(autoClickManager.currentPoint())
            }
            hidePanel()
        }
        view.findViewById<View>(R.id.panel_close).setOnClickListener {
            hideBubble()
        }
    }

    private fun toggleStartStop() {
        if (autoClickManager.isRunning()) {
            autoClickManager.stop()
            updateRunningState(false)
        } else {
            val point = autoClickManager.currentPoint()
            if (point == null) {
                Toast.makeText(context, "Pick a tap point first", Toast.LENGTH_SHORT).show()
                return
            }
            autoClickManager.start(point.first, point.second, autoClickManager.currentInterval())
            updateRunningState(true)
        }
        panelView?.findViewById<TextView>(R.id.panel_status)?.text =
            if (autoClickManager.isRunning()) "Running" else "Stopped"
        (panelView?.findViewById<View>(R.id.panel_toggle) as? android.widget.Button)?.text =
            if (autoClickManager.isRunning()) "Stop" else "Start"
        panelView?.findViewById<View>(R.id.panel_reposition)?.isEnabled =
            pointEditingEnabled && !autoClickManager.isRunning()
    }

    private fun updateBubblePosition(rawX: Float, rawY: Float) {
        val params = bubbleParams ?: return
        params.x = (rawX - params.width / 2).toInt()
        params.y = (rawY - params.height / 2).toInt()
        val metrics = context.resources.displayMetrics
        params.x = params.x.coerceIn(0, metrics.widthPixels - params.width)
        params.y = params.y.coerceIn(0, metrics.heightPixels - params.height)
        windowManager.updateViewLayout(bubbleView, params)
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val size = (context.resources.displayMetrics.density * 56).toInt()
        return WindowManager.LayoutParams(
            size,
            size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }
    }

    private fun createPanelLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val width = WindowManager.LayoutParams.WRAP_CONTENT
        val height = WindowManager.LayoutParams.WRAP_CONTENT
        return WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private class OverlayGestureDetector {
        private var downX = 0f
        private var downY = 0f
        var isDragging = false
            private set

        fun start(x: Float, y: Float) {
            downX = x
            downY = y
            isDragging = false
        }

        fun detectMove(x: Float, y: Float) {
            val dx = x - downX
            val dy = y - downY
            if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                isDragging = true
            }
        }

        fun isClick(x: Float, y: Float): Boolean {
            val dx = kotlin.math.abs(x - downX)
            val dy = kotlin.math.abs(y - downY)
            return dx < 10 && dy < 10
        }

        fun reset() {
            downX = 0f
            downY = 0f
            isDragging = false
        }
    }

    companion object {
        fun isOverlayPermissionGranted(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }
    }
}
