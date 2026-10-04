package com.nexus.ai.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.*
import android.widget.TextView

class OverlayViewManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var label: TextView? = null

    fun show(message: String = "Listening…") {
        if (!Settings.canDrawOverlays(context)) return
        if (overlayView != null) {
            update(message)
            return
        }

        val text = TextView(context).apply {
            this.text = message
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(28, 16, 28, 16)
            background = GradientDrawable().apply {
                setColor(Color.rgb(13, 30, 51))
                cornerRadius = 32f
                setStroke(1, Color.rgb(101, 169, 255))
            }
        }
        label = text

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 72
        }

        windowManager.addView(text, params)
        overlayView = text
    }

    fun update(message: String) {
        label?.text = message
    }

    fun hide() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Throwable) {}
        }
        overlayView = null
        label = null
    }
}
