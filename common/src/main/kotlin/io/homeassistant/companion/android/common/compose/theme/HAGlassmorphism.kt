package io.homeassistant.companion.android.common.compose.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * woowtech VI System Glassmorphism utilities.
 *
 * Provides frosted glass effects compliant with the woowtech Visual Identity System.
 * - Light theme: White glass at 50% or 70% opacity
 * - Dark theme: Black glass at 30% opacity
 * - Blur radius: 20dp
 */
object HAGlassmorphism {

    /** White glass at 50% opacity for light theme */
    val WhiteGlass50 = Color.White.copy(alpha = 0.50f)

    /** White glass at 70% opacity for light theme */
    val WhiteGlass70 = Color.White.copy(alpha = 0.70f)

    /** Dark glass at 30% opacity for dark theme */
    val DarkGlass30 = Color.Black.copy(alpha = 0.30f)

    /** Standard blur radius for glassmorphism effect (20dp) */
    val BlurRadius = 20.dp

    /** Minimum API level required for native blur support */
    const val MIN_BLUR_API = Build.VERSION_CODES.S
}

/**
 * Applies a glassmorphism effect to this composable.
 *
 * Uses the woowtech VI System glassmorphism specifications:
 * - Light mode: White at 50% or 70% opacity
 * - Dark mode: Black at 30% opacity
 * - Blur radius: 20dp (requires API 31+)
 *
 * @param isDark Whether to use dark mode glass (30% black) or light mode glass (white)
 * @param opacity The opacity for the glass background (default 0.50f for light, ignored for dark)
 * @param applyBlur Whether to apply blur effect (only works on API 31+)
 * @return Modifier with glassmorphism effect applied
 */
@Composable
fun Modifier.glassmorphism(
    isDark: Boolean = false,
    opacity: Float = 0.50f,
    applyBlur: Boolean = true,
): Modifier {
    val backgroundColor = if (isDark) {
        HAGlassmorphism.DarkGlass30
    } else {
        Color.White.copy(alpha = opacity)
    }

    return this
        .then(
            if (applyBlur && Build.VERSION.SDK_INT >= HAGlassmorphism.MIN_BLUR_API) {
                Modifier.blur(HAGlassmorphism.BlurRadius)
            } else {
                Modifier
            }
        )
        .background(
            color = backgroundColor,
            shape = RoundedCornerShape(HARadius.X2L)
        )
}

/**
 * Applies a light glassmorphism effect with 50% opacity.
 *
 * @param applyBlur Whether to apply blur effect (only works on API 31+)
 * @return Modifier with light glass effect
 */
@Composable
fun Modifier.lightGlass50(applyBlur: Boolean = true): Modifier =
    glassmorphism(isDark = false, opacity = 0.50f, applyBlur = applyBlur)

/**
 * Applies a light glassmorphism effect with 70% opacity.
 *
 * @param applyBlur Whether to apply blur effect (only works on API 31+)
 * @return Modifier with light glass effect at 70% opacity
 */
@Composable
fun Modifier.lightGlass70(applyBlur: Boolean = true): Modifier =
    glassmorphism(isDark = false, opacity = 0.70f, applyBlur = applyBlur)

/**
 * Applies a dark glassmorphism effect with 30% opacity.
 *
 * @param applyBlur Whether to apply blur effect (only works on API 31+)
 * @return Modifier with dark glass effect
 */
@Composable
fun Modifier.darkGlass(applyBlur: Boolean = true): Modifier =
    glassmorphism(isDark = true, applyBlur = applyBlur)
