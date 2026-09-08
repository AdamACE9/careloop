package com.careloop.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * CareLoop's Material 3 theme.
 *
 * **Dynamic colour is deliberately disabled.** Material You would recolour the app from
 * the user's wallpaper, which would (a) destroy the brand on the single screen that most
 * needs to be instantly recognisable — the incoming call — and (b) silently break the
 * contrast guarantees documented in [CareColors]. We cannot verify contrast for a colour
 * scheme derived from an unknown wallpaper, so we don't allow one.
 */

private val LightColors = lightColorScheme(
    primary = CareColors.Navy,
    onPrimary = CareColors.White,
    primaryContainer = CareColors.NavySoft,
    onPrimaryContainer = CareColors.White,

    // Gold is a *container* colour, never a text colour on light ground.
    secondary = CareColors.GoldDeep,
    onSecondary = CareColors.White,
    secondaryContainer = CareColors.Yellow,
    onSecondaryContainer = CareColors.Ink,

    background = CareColors.Bone,
    onBackground = CareColors.Ink,
    surface = CareColors.White,
    onSurface = CareColors.Ink,
    surfaceVariant = CareColors.Cloud,
    onSurfaceVariant = CareColors.Slate,

    error = CareColors.Urgent,
    onError = CareColors.White,
    errorContainer = CareColors.UrgentSurface,
    onErrorContainer = CareColors.Urgent,

    outline = Color(0xFFC7C3BA),
    outlineVariant = Color(0xFFE2DED5),
)

/**
 * Dark scheme. The navy ground is where gold finally works as a text colour (6.1:1),
 * so gold does more work here than in light mode.
 */
private val DarkColors = darkColorScheme(
    primary = CareColors.Gold,
    onPrimary = CareColors.NavyDeep,
    primaryContainer = CareColors.NavySoft,
    onPrimaryContainer = CareColors.White,

    secondary = CareColors.Yellow,
    onSecondary = CareColors.NavyDeep,
    secondaryContainer = CareColors.NavySoft,
    onSecondaryContainer = CareColors.White,

    background = CareColors.NavyDeep,
    onBackground = CareColors.White,
    surface = CareColors.Navy,
    onSurface = CareColors.White,
    surfaceVariant = CareColors.NavySoft,
    onSurfaceVariant = Color(0xFFD5DAE6),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    outline = Color(0xFF6B7590),
    outlineVariant = CareColors.NavySoft,
)

private val CareShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(CareDimens.SpaceSm),
    small = androidx.compose.foundation.shape.RoundedCornerShape(CareDimens.SpaceMd),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(CareDimens.ButtonRadius),
    large = androidx.compose.foundation.shape.RoundedCornerShape(CareDimens.CardRadius),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(CareDimens.SpaceXl),
)

@Composable
fun CareLoopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CareTypography,
        shapes = CareShapes,
        content = content,
    )
}
