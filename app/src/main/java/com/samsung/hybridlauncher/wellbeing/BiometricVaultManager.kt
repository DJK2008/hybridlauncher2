package com.samsung.hybridlauncher.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Outline
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.Window
import android.appwidget.AppWidgetHostView
import androidx.annotation.RequiresApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-level System UI and Window hacks required to stabilize the launcher on One UI 4
 * and enforce Material 3 Expressive design tokens globally.
 */
@Singleton
class SystemUiOverrides @Inject constructor() {

    companion object {
        // Material 3 Expressive / One UI 7 standard widget corner radius (in pixels).
        // In a full app, this would be converted from 24dp to pixels using resources.
        private const val WIDGET_CORNER_RADIUS_PX = 64f
    }

    /**
     * Fixes the infamous Android 12 / One UI 4 gesture navigation freeze.
     * * How it works:
     * When returning home via swipe, SystemUI occasionally leaves the window in an orphaned
     * gesture state, swallowing touch events. By wrapping the Window's callback, we intercept
     * focus changes and forcefully clear the frozen touch queue.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun applyGestureFreezeWorkaround(activity: Activity) {
        val window: Window = activity.window
        val decorView: View = window.decorView

        // 1. Force hardware acceleration and prevent drawing delays during the home swipe
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        // 2. Intercept touches at the absolute root of the view hierarchy
        decorView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                // If the view is inexplicably disabled by SystemUI during a gesture handoff,
                // we forcefully re-enable the hierarchy to break the lock.
                if (!v.isEnabled) {
                    forceEnableViewHierarchy(v)
                }
            }
            // Return false to allow the touch to continue propagating down to the launcher UI
            false
        }

        // 3. Reset state immediately on window focus
        decorView.viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                // When the launcher regains focus after a home swipe, ensure no ghost touches
                // are blocking the input queue.
                decorView.cancelPendingInputEvents()
            }
        }
    }

    /**
     * Recursively walk the tree and re-enable views that SystemUI might have disabled
     * during a botched RecentsAnimation handoff.
     */
    private fun forceEnableViewHierarchy(view: View) {
        view.isEnabled = true
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                forceEnableViewHierarchy(view.getChildAt(i))
            }
        }
    }

    /**
     * Overrides the default widget rendering bounds.
     * Many legacy apps ignore Android 12's `system_app_widget_background_radius`.
     * This function forcibly clips the host view to our standard One UI 7 squircle,
     * ensuring aesthetic consistency across all third-party widgets.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun enforceUniformWidgetCorners(widgetHostView: AppWidgetHostView) {
        // Disable the legacy background to prevent double-drawing
        widgetHostView.setClipToOutline(true)

        widgetHostView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                // Clip the widget strictly to a rounded rectangle with our M3/One UI 7 radius.
                // We shrink the rect slightly (e.g., 1px) to prevent anti-aliasing artifacts
                // on the edges of poorly drawn legacy widgets.
                outline.setRoundRect(
                    1,
                    1,
                    view.width - 1,
                    view.height - 1,
                    WIDGET_CORNER_RADIUS_PX
                )
            }
        }
    }
}