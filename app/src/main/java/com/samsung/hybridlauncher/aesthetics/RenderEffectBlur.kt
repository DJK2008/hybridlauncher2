package com.samsung.hybridlauncher.aesthetics

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Highly optimized Frosted Glass engine designed for One UI 7 aesthetics.
 * Keeps the launcher strictly under the 200MB memory ceiling by offloading
 * all blur calculations directly to the GPU via RenderThread.
 */
@Singleton
class RenderEffectBlur @Inject constructor() {

    companion object {
        // One UI 7 Frosted Glass standard tokens
        private const val FROSTED_GLASS_RADIUS = 80f
        private const val SUBTLE_OVERLAY_RADIUS = 25f
    }

    enum class BlurDepth {
        /** Heavy blur for the App Drawer background, isolating it from the wallpaper. */
        FROSTED_GLASS,

        /** Lighter blur for transient overlays like the Now Bar or Folder backgrounds. */
        SUBTLE_OVERLAY
    }

    /**
     * Applies a hardware-accelerated blur to a standard Android View.
     * * @param view The target View (e.g., the App Drawer background container).
     * @param depth The One UI 7 blur token to apply.
     * @param remove Blur removal flag. If true, instantly strips the RenderEffect to free GPU resources.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun applyToView(view: View, depth: BlurDepth = BlurDepth.FROSTED_GLASS, remove: Boolean = false) {
        if (remove) {
            view.setRenderEffect(null)
            return
        }

        val radius = when (depth) {
            BlurDepth.FROSTED_GLASS -> FROSTED_GLASS_RADIUS
            BlurDepth.SUBTLE_OVERLAY -> SUBTLE_OVERLAY_RADIUS
        }

        // Shader.TileMode.CLAMP ensures the edges don't sample black pixels from outside the view bounds,
        // which prevents the ugly dark vignette ring often seen in poorly implemented blurs.
        val blurEffect = RenderEffect.createBlurEffect(
            radius,
            radius,
            Shader.TileMode.CLAMP
        )

        view.setRenderEffect(blurEffect)
    }

    /**
     * Specialized function to chain a color tint with the blur.
     * Crucial for maintaining Material 3 Expressive high-contrast readability
     * when the background wallpaper is too bright or noisy.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun applyTintedBlurToView(view: View, tintColor: Int, depth: BlurDepth = BlurDepth.FROSTED_GLASS) {
        val radius = when (depth) {
            BlurDepth.FROSTED_GLASS -> FROSTED_GLASS_RADIUS
            BlurDepth.SUBTLE_OVERLAY -> SUBTLE_OVERLAY_RADIUS
        }

        val blurEffect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)

        // Android 12 RenderEffect chaining allows us to apply a color filter over the blurred output
        // in a single GPU pass, saving frame time.
        val colorEffect = RenderEffect.createColorFilterEffect(
            android.graphics.BlendModeColorFilter(tintColor, android.graphics.BlendMode.SRC_OVER)
        )

        val chainEffect = RenderEffect.createChainEffect(colorEffect, blurEffect)
        view.setRenderEffect(chainEffect)
    }
}

/**
 * Compose extension modifier for seamless integration in the hybrid architecture.
 * Maps directly to Compose's internal RenderEffect bindings for API 31+.
 */
@Stable
fun Modifier.oneUiFrostedGlass(
    depth: RenderEffectBlur.BlurDepth = RenderEffectBlur.BlurDepth.FROSTED_GLASS,
    enabled: Boolean = true
): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return this
    }

    val radius = when (depth) {
        RenderEffectBlur.BlurDepth.FROSTED_GLASS -> 80f
        RenderEffectBlur.BlurDepth.SUBTLE_OVERLAY -> 25f
    }

    return this.then(
        Modifier.graphicsLayer {
            renderEffect = androidx.compose.ui.graphics.RenderEffect.createBlurEffect(
                radiusX = radius,
                radiusY = radius,
                edgeTreatment = androidx.compose.ui.graphics.RenderEffect.createEdgeBehavior(
                    androidx.compose.ui.graphics.TileMode.Clamp
                )
            )
            // Clip to bounds to prevent the blur from bleeding into neighboring Compose nodes
            clip = true
        }
    )
}