package com.samsung.hybridlauncher.wellbeing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsung.hybridlauncher.aesthetics.RenderEffectBlur
import com.samsung.hybridlauncher.aesthetics.oneUiFrostedGlass
import kotlinx.coroutines.delay

/**
 * Mindful Launch Friction Overlay.
 * Intercepts the launch of heavy distraction apps (e.g., social media) and enforces
 * a 3-second breathing prompt. Designed to break unconscious scrolling habits.
 */
@Composable
fun LaunchFrictionOverlay(
    isVisible: Boolean,
    appName: String,
    onProceed: () -> Unit,
    onCancel: () -> Unit
) {
    // We completely remove the overlay from the composition when not visible
    // to ensure zero background memory or CPU usage.
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(400)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        var timeLeft by remember { mutableIntStateOf(3) }

        // M3 Expressive infinite breathing animation
        val infiniteTransition = rememberInfiniteTransition(label = "breathing")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathing_scale"
        )
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathing_alpha"
        )

        // Countdown timer logic
        LaunchedEffect(isVisible) {
            if (isVisible) {
                timeLeft = 3
                while (timeLeft > 0) {
                    delay(1000L)
                    timeLeft--
                }
                // Automatically launch the app and dismiss the overlay once the timer hits 0
                onProceed()
            }
        }

        // Main overlay structure
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Apply the heavy One UI 7 frosted glass to obscure the workspace completely
                .oneUiFrostedGlass(depth = RenderEffectBlur.BlurDepth.FROSTED_GLASS)
                .background(Color.Black.copy(alpha = 0.6f))
                // Intercept all background clicks so the user can't accidentally interact
                // with the launcher while the prompt is active
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Consume clicks */ }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {

                // The Breathing Indicator
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(120.dp)
                ) {
                    // Outer morphing aura
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(scale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                    )

                    // Inner countdown text
                    Text(
                        text = timeLeft.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = "Take a breath.",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Opening $appName in $timeLeft...",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(64.dp))

                // Escape hatch: Allow the user to mindfully back out
                Text(
                    text = "Cancel",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onCancel() }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}