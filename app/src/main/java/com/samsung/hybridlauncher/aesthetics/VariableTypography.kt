package com.samsung.hybridlauncher.aesthetics

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.samsung.hybridlauncher.R

/**
 * Variable Typography Engine for M3 Expressive aesthetics.
 * Dynamically shifts font weight and width using mathematical spring physics
 * when the user interacts with text elements (e.g., long-pressing a widget or folder label).
 */
object VariableTypography {

    // The target font must be a true Variable Font (.ttf) supporting multiple axes.
    // We assume it's referenced via R.font.roboto_flex for this implementation.
    @OptIn(ExperimentalTextApi::class)
    val SystemVariableFont = FontFamily(
        Font(
            resId = R.font.roboto_flex,
            weight = FontWeight.Normal
        )
    )

    /**
     * Standard text weight tokens for fluid morphing.
     */
    const val WEIGHT_DEFAULT = 400f
    const val WEIGHT_PRESSED = 700f

    /**
     * Standard text width tokens (requires a font that supports the 'wdth' axis).
     */
    const val WIDTH_DEFAULT = 100f
    const val WIDTH_PRESSED = 115f
}

/**
 * A highly interactive Text composable that reacts to touch by morphing its variable font axes.
 * Perfectly mirrors the M3 Expressive "Shape Morphing Engine" logic.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun InteractiveVariableText(
    text: String,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    style: TextStyle = LocalTextStyle.current
) {
    var isPressed by remember { mutableStateOf(false) }

    // Observe interaction source to detect touch down/up events seamlessly
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> isPressed = true
                is PressInteraction.Release, is PressInteraction.Cancel -> isPressed = false
            }
        }
    }

    // M3 Expressive Spring Physics for Font Weight
    val animatedWeight by animateFloatAsState(
        targetValue = if (isPressed) VariableTypography.WEIGHT_PRESSED else VariableTypography.WEIGHT_DEFAULT,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "FontWeightSpring"
    )

    // M3 Expressive Spring Physics for Font Width
    val animatedWidth by animateFloatAsState(
        targetValue = if (isPressed) VariableTypography.WIDTH_PRESSED else VariableTypography.WIDTH_DEFAULT,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "FontWidthSpring"
    )

    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontFamily = VariableTypography.SystemVariableFont,
        style = style.copy(
            fontVariationSettings = FontVariation.Settings(
                FontVariation.weight(animatedWeight.toInt()),
                FontVariation.width(animatedWidth)
            )
        )
    )
}