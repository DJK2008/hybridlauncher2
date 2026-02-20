package com.samsung.hybridlauncher.nowbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsung.hybridlauncher.R
import com.samsung.hybridlauncher.aesthetics.RenderEffectBlur
import com.samsung.hybridlauncher.aesthetics.oneUiFrostedGlass

/**
 * The dynamic, shape-shifting "Now Bar" positioned above the dock.
 * Consumes the StateFlow from [NowBarNotificationListener] and morphs its layout
 * using Material 3 Expressive spring physics.
 */
@Composable
fun NowBarContainer(modifier: Modifier = Modifier) {
    val nowBarState by NowBarNotificationListener.nowBarState.collectAsState()

    // We wrap the pill in an AnimatedVisibility block so it completely leaves the composition
    // when hidden, freeing up memory and layout passes.
    AnimatedVisibility(
        visible = nowBarState !is NowBarState.Hidden,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(32.dp))
                // Apply our API 31 GPU-accelerated frosted glass modifier
                .oneUiFrostedGlass(depth = RenderEffectBlur.BlurDepth.SUBTLE_OVERLAY)
                // Fallback semi-transparent background for high-contrast visibility
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { /* Expand to full activity overlay */ }
                // The crucial M3 Expressive spatial morphing engine for Compose
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
        ) {
            when (val state = nowBarState) {
                is NowBarState.Media -> MediaPill(state)
                is NowBarState.Timer -> TimerPill(state)
                NowBarState.Hidden -> Spacer(modifier = Modifier.size(0.dp))
            }
        }
    }
}

@Composable
private fun MediaPill(state: NowBarState.Media) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Album Art Placeholder / Equalizer Icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_media_play),
                    contentDescription = "Media",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = state.trackTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.artistName,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Play/Pause indicator
        Icon(
            painter = painterResource(
                id = if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            ),
            contentDescription = "Play/Pause",
            tint = Color.White,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(28.dp)
        )
    }
}

@Composable
private fun TimerPill(state: NowBarState.Timer) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(
            painter = painterResource(id = android.R.drawable.ic_menu_recent_history), // Timer icon
            contentDescription = "Timer",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // In a production environment, this would use a custom layout or heavily optimized
        // LaunchedEffect to tick the timer without causing constant Compose recompositions.
        Text(
            text = "Active Timer", // Placeholder for actual ticking logic
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            letterSpacing = 1.sp
        )
    }
}