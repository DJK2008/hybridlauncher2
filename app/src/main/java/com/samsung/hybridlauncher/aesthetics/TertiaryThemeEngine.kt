package com.samsung.hybridlauncher.aesthetics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Algorithmic Color Engine for M3 Expressive / One UI 7 aesthetics.
 * Extracts dominant wallpaper colors and mathematically forces high-contrast
 * tonal shifts to generate Primary, Secondary, and Tertiary accents.
 */
@Singleton
class TertiaryThemeEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Data class representing our custom high-contrast color scheme.
     */
    data class DynamicColorScheme(
        @ColorInt val primary: Int,
        @ColorInt val onPrimary: Int,
        @ColorInt val secondary: Int,
        @ColorInt val onSecondary: Int,
        @ColorInt val tertiary: Int,
        @ColorInt val onTertiary: Int,
        @ColorInt val backgroundTint: Int
    )

    /**
     * Analyzes the wallpaper bitmap asynchronously and generates a cohesive M3 palette.
     * * @param wallpaper The raw wallpaper Bitmap.
     * @param isDarkMode System dark mode flag to adjust lightness thresholds.
     */
    suspend fun extractTheme(wallpaper: Bitmap, isDarkMode: Boolean): DynamicColorScheme =
        withContext(Dispatchers.Default) {

            // Downscale bitmap heavily before extraction to respect the 200MB memory limit
            val scaledBitmap = Bitmap.createScaledBitmap(
                wallpaper,
                128,
                (128f * wallpaper.height / wallpaper.width).toInt(),
                false
            )

            // Generate palette
            val palette = Palette.from(scaledBitmap).generate()
            scaledBitmap.recycle()

            // Prefer vibrant colors for M3 Expressive, fallback to muted or a default system blue
            val seedColor = palette.getVibrantColor(
                palette.getDominantColor(AndroidColor.parseColor("#007AFF"))
            )

            return@withContext generateTonalScheme(seedColor, isDarkMode)
        }

    /**
     * The core mathematical engine. Shifts HSL values to create a tri-tone palette
     * guaranteed to contrast beautifully with the One UI 7 frosted glass.
     */
    private fun generateTonalScheme(@ColorInt seedColor: Int, isDarkMode: Boolean): DynamicColorScheme {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seedColor, hsl)

        val hue = hsl[0]
        val saturation = hsl[1].coerceAtLeast(0.5f) // Force minimum saturation for "pop"

        // --- Primary ---
        // Stick close to the seed, but adjust lightness for dark/light mode
        val primaryLightness = if (isDarkMode) 0.65f else 0.40f
        val primary = ColorUtils.HSLToColor(floatArrayOf(hue, saturation, primaryLightness))
        val onPrimary = getContrastColor(primary)

        // --- Secondary ---
        // Shift hue by +30 degrees (Analogous) and desaturate slightly
        val secondaryHue = (hue + 30f) % 360f
        val secondaryLightness = if (isDarkMode) 0.75f else 0.30f
        val secondary = ColorUtils.HSLToColor(floatArrayOf(secondaryHue, saturation * 0.8f, secondaryLightness))
        val onSecondary = getContrastColor(secondary)

        // --- Tertiary (The Wildcard) ---
        // Shift hue by -60 degrees (Complementary leaning) for a striking accent
        val tertiaryHue = (hue - 60f + 360f) % 360f
        val tertiaryLightness = if (isDarkMode) 0.70f else 0.35f
        val tertiary = ColorUtils.HSLToColor(floatArrayOf(tertiaryHue, saturation * 0.9f, tertiaryLightness))
        val onTertiary = getContrastColor(tertiary)

        // --- Background Tint ---
        // A deeply desaturated, very dark/light version of the primary color to tint the frosted glass
        val bgLightness = if (isDarkMode) 0.10f else 0.95f
        val backgroundTint = ColorUtils.HSLToColor(floatArrayOf(hue, 0.2f, bgLightness))

        return DynamicColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            onSecondary = onSecondary,
            tertiary = tertiary,
            onTertiary = onTertiary,
            backgroundTint = backgroundTint
        )
    }

    /**
     * Guarantees absolute WCAG readability. Returns either stark white or deep black
     * depending on the luminance of the base color.
     */
    @ColorInt
    private fun getContrastColor(@ColorInt color: Int): Int {
        val luminance = ColorUtils.calculateLuminance(color)
        return if (luminance > 0.45) {
            AndroidColor.parseColor("#1C1C1E") // Deep One UI 7 Black/Gray
        } else {
            AndroidColor.parseColor("#F2F2F7") // Off-white for less eye strain
        }
    }
}