package com.samsung.hybridlauncher.workspace

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.children
import com.samsung.hybridlauncher.motion.SpringPhysicsEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * 2.5D Interactive Folder for the Hybrid Launcher.
 * Displays up to 4 direct-tap icons in its collapsed state.
 * Relies on math-based touch delegation to decide whether to launch an app
 * or morph into the expanded folder view.
 */
@AndroidEntryPoint
class InteractiveFolderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    @Inject
    lateinit var springPhysics: SpringPhysicsEngine

    // Represents the maximum number of items visible and interactive from the home screen
    private val maxDirectTapChildren = 4

    // Tracks whether the folder is currently morphed into its open state
    var isExpanded: Boolean = false
        private set

    // Pre-allocated rect for hit testing to avoid GC thrashing during touch events
    private val hitRect = Rect()

    init {
        // Enforce 2.5D spatial depth out of the box
        elevation = 12f
        clipChildren = false
        clipToPadding = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        if (!isExpanded) {
            // Measure the direct-tap children for the collapsed 2x2 grid
            val childWidthSpec = MeasureSpec.makeMeasureSpec(width / 2, MeasureSpec.EXACTLY)
            val childHeightSpec = MeasureSpec.makeMeasureSpec(height / 2, MeasureSpec.EXACTLY)

            children.take(maxDirectTapChildren).forEach { child ->
                child.measure(childWidthSpec, childHeightSpec)
            }
        } else {
            // In a production implementation, you measure the expanded scrollable grid here
            val expandedWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST)
            val expandedHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
            children.forEach { it.measure(expandedWidthSpec, expandedHeightSpec) }
        }

        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val height = b - t

        if (!isExpanded) {
            // Layout a perfectly spaced 2x2 micro-grid for the first 4 children
            val halfWidth = width / 2
            val halfHeight = height / 2

            children.take(maxDirectTapChildren).forEachIndexed { index, child ->
                val col = index % 2
                val row = index / 2

                // Add a subtle padding (e.g., 8px) so the icons don't touch the very edge of the card
                val padding = 8

                val childLeft = (col * halfWidth) + padding
                val childTop = (row * halfHeight) + padding
                val childRight = childLeft + halfWidth - (padding * 2)
                val childBottom = childTop + halfHeight - (padding * 2)

                child.layout(childLeft, childTop, childRight, childBottom)
                child.visibility = View.VISIBLE
            }

            // Hide the rest of the contents while collapsed to save render cycles
            children.drop(maxDirectTapChildren).forEach { it.visibility = View.GONE }
        } else {
            // Logic for laying out the fully expanded folder grid goes here
        }
    }

    /**
     * Intercept touch events before they reach the workspace.
     * We determine if the user touched exactly on a direct-tap icon.
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && !isExpanded) {
            val x = ev.x.roundToInt()
            val y = ev.y.roundToInt()

            // Check if the tap landed inside one of the 4 visible children
            val tappedChild = children.take(maxDirectTapChildren).find { child ->
                child.getHitRect(hitRect)
                hitRect.contains(x, y)
            }

            // If they didn't tap an icon, intercept the touch so onTouchEvent can handle expansion
            if (tappedChild == null) {
                return true
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    /**
     * Handles taps on the "empty space" of the folder card to trigger expansion.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && !isExpanded) {
            expandFolder()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun expandFolder() {
        if (isExpanded) return
        isExpanded = true

        // Ensure the layout system knows we are revealing all children
        requestLayout()

        // Trigger the M3 Expressive shape morphing
        // We expand the scale and shift the Y-axis slightly to center it on screen
        springPhysics.performMorphExpansion(
            view = this,
            targetScaleX = 2.5f,
            targetScaleY = 2.5f,
            targetTranslationY = -200f, // Shift up towards center of screen
            profile = SpringPhysicsEngine.SpringProfile.EXPRESSIVE_SNAP
        )
    }

    fun collapseFolder() {
        if (!isExpanded) return
        isExpanded = false

        requestLayout()

        // Spring back to original workspace slot
        springPhysics.performMorphExpansion(
            view = this,
            targetScaleX = 1.0f,
            targetScaleY = 1.0f,
            targetTranslationY = 0f,
            profile = SpringPhysicsEngine.SpringProfile.EXPRESSIVE_SNAP
        )
    }

    /**
     * Helper to add a new app to this folder dynamically.
     */
    fun addAppIcon(iconView: ImageView, appPackage: String) {
        iconView.tag = appPackage // Store package name for the splash screen handoff later
        iconView.setOnClickListener {
            if (!isExpanded) {
                // Instantly launch the app directly from the home screen
                launchApp(appPackage, it)
            }
        }
        addView(iconView)
    }

    /**
     * Seamless API 31 Splash Screen Handoff.
     * Eliminates black flashes by mapping the OS starting window strictly to the icon bounds.
     */
    private fun launchApp(packageName: String, iconView: View) {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return

        // 1. Base spatial animation mapping the icon's exact pixels to the app window
        val options = ActivityOptions.makeClipRevealAnimation(
            iconView,
            0,
            0,
            iconView.width,
            iconView.height
        )

        val bundle = options.toBundle()

        // 2. Android 12 S SystemUI Handoff injection
        // We inject the splashScreenStyle flag to force the OS to use the seamless
        // icon-based splash screen transition rather than falling back to a blank window.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bundle.putInt("android.activity.splashScreenStyle", 1 /* SPLASH_SCREEN_STYLE_ICON */)
        }

        // 3. Apply standard launcher execution flags
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        try {
            context.startActivity(intent, bundle)
        } catch (e: Exception) {
            // Failsafe in case the app was uninstalled or suspended mid-interaction
            e.printStackTrace()
        }
    }
}