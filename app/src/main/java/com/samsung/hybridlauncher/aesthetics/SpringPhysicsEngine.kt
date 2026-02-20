package com.samsung.hybridlauncher.aesthetics

import android.view.View

/**
 * M3 Expressive Motion Engine.
 * Handles spatial spring physics for swiping, tapping, and shape morphing.
 * Guarantees mid-interaction interruptibility.
 */
@Singleton
class SpringPhysicsEngine @Inject constructor() {

    enum class SpringProfile(val stiffness: Float, val dampingRatio: Float) {
        // High tension, slight bounce - ideal for quick icon taps or folder expansion
        EXPRESSIVE_SNAP(SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY),

        // Loose, fluid movement - used for spatial depth and parallax shifting
        PARALLAX_SHIFT(SpringForce.STIFFNESS_LOW, SpringForce.DAMPING_RATIO_NO_BOUNCY),

        // High resistance - used for layout boundaries and fast-scroll overscroll
        RUBBER_BAND(SpringForce.STIFFNESS_HIGH, SpringForce.DAMPING_RATIO_HIGH_BOUNCY)
    }

    /**
     * Applies a continuous spring animation to a specific view property.
     * If an animation is already running on this property, it updates the target mid-flight,
     * maintaining the current velocity to ensure seamless interruptibility.
     */
    fun animateToTarget(
        view: View,
        property: DynamicAnimation.ViewProperty,
        targetValue: Float,
        profile: SpringProfile = SpringProfile.EXPRESSIVE_SNAP,
        startVelocity: Float? = null,
        onEnd: (() -> Unit)? = null
    ) {
        // Retrieve existing animation to maintain velocity, or create a new one
        var springAnim = view.getTag(getPropertyTagKey(property)) as? SpringAnimation

        if (springAnim == null) {
            springAnim = SpringAnimation(view, property)
            view.setTag(getPropertyTagKey(property), springAnim)
        }

        val springForce = springAnim.spring ?: SpringForce().apply { springAnim.spring = this }

        springForce.apply {
            stiffness = profile.stiffness
            dampingRatio = profile.dampingRatio
            finalPosition = targetValue
        }

        startVelocity?.let { springAnim.setStartVelocity(it) }

        if (onEnd != null) {
            springAnim.addEndListener(object : DynamicAnimation.OnAnimationEndListener {
                override fun onAnimationEnd(
                    animation: DynamicAnimation?,
                    canceled: Boolean,
                    value: Float,
                    velocity: Float
                ) {
                    onEnd()
                    springAnim.removeEndListener(this)
                }
            })
        }

        springAnim.start()
    }

    /**
     * Specialized function for the M3 "Shape Morphing Engine".
     * Animates scale and translation simultaneously to create a cohesive expansion effect
     * (e.g., pill-shaped search bar expanding into a rounded rectangle).
     */
    fun performMorphExpansion(
        view: View,
        targetScaleX: Float,
        targetScaleY: Float,
        targetTranslationY: Float,
        profile: SpringProfile = SpringProfile.EXPRESSIVE_SNAP
    ) {
        animateToTarget(view, DynamicAnimation.SCALE_X, targetScaleX, profile)
        animateToTarget(view, DynamicAnimation.SCALE_Y, targetScaleY, profile)
        animateToTarget(view, DynamicAnimation.TRANSLATION_Y, targetTranslationY, profile)
    }

    /**
     * Calculates a pseudo-3D Z-axis shift for backgrounds and stacked widgets based on scroll.
     */
    fun applyParallaxScroll(view: View, scrollOffset: Float, depthMultiplier: Float = 0.5f) {
        val targetTranslation = scrollOffset * depthMultiplier
        animateToTarget(
            view = view,
            property = DynamicAnimation.TRANSLATION_Y,
            targetValue = targetTranslation,
            profile = SpringProfile.PARALLAX_SHIFT
        )
    }

    /**
     * Instantly cancels all running physics on a view, critical for gesture handoffs.
     */
    fun cancelAllPhysics(view: View) {
        val properties = listOf(
            DynamicAnimation.TRANSLATION_X, DynamicAnimation.TRANSLATION_Y,
            DynamicAnimation.SCALE_X, DynamicAnimation.SCALE_Y,
            DynamicAnimation.ALPHA, DynamicAnimation.ROTATION
        )

        for (prop in properties) {
            (view.getTag(getPropertyTagKey(prop)) as? SpringAnimation)?.cancel()
        }
    }

    private fun getPropertyTagKey(property: DynamicAnimation.ViewProperty): Int {
        // Generate a unique view tag key based on the property name to store the animation instance
        return property.toString().hashCode()
    }
}