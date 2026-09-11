package com.careloop.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * CareLoop's Material 3 theme, wired to the warm palette in [CareColors].
 *
 * **Dynamic colour is deliberately disabled.** Material You would recolour the app from
 * the user's wallpaper, which would (a) destroy the brand on the single screen that most
 * needs to be instantly recognisable — the incoming call — and (b) silently break every
 * contrast measurement documented in [CareColors]. We cannot verify contrast for a colour
 * scheme derived from an unknown wallpaper, so we don't allow one.
 *
 * **Light is the default, per §5 of CLAUDE.md — elder-facing surfaces default to dark
 * text on light ground.** Reverse type (light text on a dark fill) is measurably harder
 * to read in bright ambient light, which is exactly the condition a phone held by a
 * window or outdoors puts it in.
 *
 * ## Why the roles are assigned the way they are
 *
 * Material components frequently render a colour-scheme role *as text* on their own
 * (a `TextButton`'s content colour is `colorScheme.primary`, for instance) — the role
 * itself has to be text-safe, not just "a colour in the brand". So every LightColors
 * role that could plausibly carry text uses a colour from [CareColors] already measured
 * safe for that job: [CareColors.GoldDeep], not [CareColors.GoldBright] or
 * [CareColors.Gold]; [CareColors.AquaDeep], not raw [CareColors.Aqua]; [CareColors.Brown]
 * for tertiary, not [CareColors.Amber]. GoldBright and Amber stay out of the ColorScheme
 * entirely and are only ever reached for directly, as large shapes or fills, which is the
 * one job they are measured safe for (see [CareColors] for the numbers).
 *
 * DarkColors keeps the pre-existing Navy-anchored structure working rather than
 * inverting the light scheme: on a dark ground the "Deep" gold/aqua variants are the
 * wrong tool (they were darkened specifically to survive on *light* backgrounds), so
 * dark mode reaches for the bright members of each family instead — [CareColors.Gold],
 * [CareColors.Aqua] — exactly as v1 of this theme already did for gold. [CareColors.
 * Amber] and [CareColors.GoldBright] appear only on `tertiary`/`onTertiaryContainer`
 * here, at 4.5:1-tier contrast (5.21:1, 5.79:1) rather than the 7:1 small-text floor —
 * flagged the same way it's flagged in [CareColors]: large text and icons only.
 */

private val LightColors = lightColorScheme(
    // GoldDeep, not GoldBright/Gold: primary is a role Material components will
    // render text in directly, and only GoldDeep is measured text-safe on light ground.
    primary = CareColors.GoldDeep,
    onPrimary = CareColors.White,
    primaryContainer = CareColors.GoldBright,
    onPrimaryContainer = CareColors.Brown,

    // The single accent. AquaDeep carries the role (not raw Aqua) for the same
    // text-safety reason as primary above; raw Aqua stays reachable only as
    // CareColors.Aqua, for the large shapes the brief calls for.
    secondary = CareColors.AquaDeep,
    onSecondary = CareColors.White,
    secondaryContainer = CareColors.AquaSurface,
    onSecondaryContainer = CareColors.AquaDeep,

    // Brown carries "and brown" of the gold/amber/brown identity as an always-safe
    // dark neutral, rather than Amber (which fails even the 4.5:1 floor as text).
    tertiary = CareColors.Brown,
    onTertiary = CareColors.White,
    tertiaryContainer = CareColors.Bone,
    onTertiaryContainer = CareColors.Brown,

    background = CareColors.Cream,
    onBackground = CareColors.Ink,
    surface = CareColors.Surface,
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
 * Dark scheme. Navy stays the anchor — this is the call screen's territory — so this
 * reaches for the bright half of each family (Gold, Aqua) rather than the Deep variants,
 * which were built to survive on light backgrounds, not dark ones.
 */
private val DarkColors = darkColorScheme(
    primary = CareColors.Gold,
    onPrimary = CareColors.NavyDeep,
    primaryContainer = CareColors.NavySoft,
    onPrimaryContainer = CareColors.White,

    secondary = CareColors.Aqua,
    onSecondary = CareColors.NavyDeep,
    secondaryContainer = CareColors.NavyDeep,
    onSecondaryContainer = CareColors.Aqua,

    // Large-text/icon tier only (5.21:1, 5.79:1) — see class doc. Not held to the 7:1
    // small-text floor, deliberately, same as CareColors flags for Amber/GoldBright.
    tertiary = CareColors.Amber,
    onTertiary = CareColors.NavyDeep,
    tertiaryContainer = CareColors.NavySoft,
    onTertiaryContainer = CareColors.GoldBright,

    background = CareColors.NavyDeep,
    onBackground = CareColors.White,
    surface = CareColors.Navy,
    onSurface = CareColors.White,
    surfaceVariant = CareColors.NavySoft,
    // Cloud, not the old cool D5DAE6 — warm secondary text on Navy, 8.74-11.17:1.
    onSurfaceVariant = CareColors.Cloud,

    // Standard Material dark-theme error red, left as-is: it is not part of the warm
    // palette (error stays semantically red regardless of theme warmth) and it already
    // works, which is what "keep the dark scheme legible, don't just invert it" means.
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

/**
 * @param textSize Defaults to the persisted value (see [rememberTextSize]/TextScale.kt)
 * rather than always [TextSize.NORMAL], so callers that just want "the real app" get the
 * real, saved preference without having to know DataStore exists. Pass an explicit value
 * to preview a specific size or to keep a test hermetic.
 */
@Composable
fun CareLoopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    textSize: TextSize = rememberTextSize(),
    content: @Composable () -> Unit,
) {
    CareLoopTextScale(scale = textSize.scale) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = careTypography(LocalTextScale.current),
            shapes = CareShapes,
            content = content,
        )
    }
}
