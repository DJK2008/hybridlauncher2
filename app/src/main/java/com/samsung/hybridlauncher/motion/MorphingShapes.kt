package com.samsung.hybridlauncher.motion

import android.graphics.Path
import android.graphics.RectF
import androidx.annotation.FloatRange
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M3 Expressive Shape Morphing Engine.
 * Generates highly optimized, zero-allocation hardware paths for squircles,
 * pills, and adaptive icons.
 */
@Singleton
class MorphingShapes @Inject constructor() {

    // Pre-allocated objects to prevent Garbage Collection thrashing during 60fps morphs
    private val workingPath = Path()
    private val startRect = RectF()
    private val endRect = RectF()
    private val currentRect = RectF()

    /**
     * Mutates a given path to interpolate between a circle and a rounded rectangle.
     * * @param path The pre-allocated Path object to mutate.
     * @param startBounds The bounding box of the starting shape (e.g., collapsed icon).
     * @param endBounds The bounding box of the expanded shape (e.g., expanded folder).
     * @param progress The spring physics interpolation value (0.0f to 1.0f).
     * @param startRadius Corner radius of the starting shape.
     * @param endRadius Corner radius of the ending shape.
     */
    fun morphPillToSquircle(
        path: Path,
        startBounds: RectF,
        endBounds: RectF,
        @FloatRange(from = 0.0, to = 1.0) progress: Float,
        startRadius: Float,
        endRadius: Float
    ) {
        // 1. Reset the path cleanly without triggering reallocation
        path.rewind()

        // 2. Interpolate the spatial boundaries
        currentRect.set(
            lerp(startBounds.left, endBounds.left, progress),
            lerp(startBounds.top, endBounds.top, progress),
            lerp(startBounds.right, endBounds.right, progress),
            lerp(startBounds.bottom, endBounds.bottom, progress)
        )

        // 3. Interpolate the corner radius
        val currentRadius = lerp(startRadius, endRadius, progress)

        // 4. Construct the new hardware-accelerated path
        path.addRoundRect(currentRect, currentRadius, currentRadius, Path.Direction.CW)
    }

    /**
     * Generates a Material 3 / One UI 7 standard "Squircle" (Superellipse).
     * Used heavily in the Adaptive Icon Masking protocol.
     */
    fun generateStaticSquircle(width: Float, height: Float, radius: Float): Path {
        workingPath.rewind()
        currentRect.set(0f, 0f, width, height)
        workingPath.addRoundRect(currentRect, radius, radius, Path.Direction.CW)
        return workingPath
    }

    /**
     * Linear interpolation helper function.
     */
    private fun lerp(start: Float, stop: Float, amount: Float): Float {
        return start + (stop - start) * amount
    }
}