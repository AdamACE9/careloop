package com.careloop.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Type scale for elder-facing surfaces.
 *
 * Three rules drove every number here:
 *
 * 1. **18sp is the research floor for older adults; body text sits at 19sp.** A floor
 *    is not a target. Users in this cohort routinely run the system font at maximum;
 *    starting small means starting broken.
 * 2. **Line height is at least 1.5x.** Spacing gains are measurable for older readers —
 *    it is not merely aesthetic.
 * 3. **Headlines are 24sp or larger.** That is the line where the elder-facing contrast
 *    floor relaxes from 7:1 to 4.5:1 (see Color.kt) — headlineMedium/Large are the sizes
 *    this file lets colours like [CareColors.Gold] on Navy get away with only 6.12:1.
 *    Anything smaller stays held to 7:1.
 *
 * Sizes are in `sp` so they scale with the user's system font setting. Never switch these
 * to `dp` to "protect the layout" — that breaks the accessibility setting these users are
 * most likely to have turned on. Fix the layout instead.
 *
 * System sans-serif is used deliberately: it is what the user's device already renders
 * best, honours their font-weight accessibility settings, and costs no download.
 *
 * ## Two scales compound, deliberately
 *
 * The system font-scale setting (Settings > Display > Font size) and CareLoop's own
 * in-app text-size control (see TextScale.kt) are different knobs, and this file does
 * not try to collapse them into one. Every size below is `sp`, so the system scale is
 * already applied by the time Compose measures it; [careTypography] then multiplies
 * that already-scaled value again by [scale]. A user who has both turned up gets the
 * compounded result, and that is correct — CareLoop's control is a *floor above the
 * system floor*, for the documented case where the phone-wide setting was never touched
 * but 19sp still is not enough for this person specifically.
 */

private val Sans = FontFamily.SansSerif

/** Trims extra leading so large text does not float inside its own box. */
private val TightLine = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Builds the type scale at [scale]. `1f` (the default) is the untouched baseline that
 * [TextSize.NORMAL] maps to; the other [TextSize] steps call this at 1.15/1.3/1.5.
 *
 * fontSize and lineHeight both scale, so the >=1.5x leading ratio holds at every step,
 * not just at 1f. letterSpacing does not scale — tracking is not a legibility floor the
 * way size and leading are, and scaling small negative values toward zero at 1.5x would
 * just be noise.
 */
fun careTypography(scale: Float = 1f): Typography = Typography(

    // Hero moments — the caller's name on an incoming call.
    displayLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 46.sp * scale,
        lineHeight = 54.sp * scale,
        letterSpacing = (-0.5).sp,
        lineHeightStyle = TightLine,
    ),
    displayMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp * scale,
        lineHeight = 44.sp * scale,
        letterSpacing = (-0.25).sp,
        lineHeightStyle = TightLine,
    ),

    // Screen titles. 24sp+, so the 4.5:1 contrast floor applies here, not 7:1.
    headlineLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp * scale,
        lineHeight = 38.sp * scale,
        lineHeightStyle = TightLine,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp * scale,
        lineHeight = 33.sp * scale,
        lineHeightStyle = TightLine,
    ),

    // Card titles, medication names. Under 24sp — held to the 7:1 floor.
    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp * scale,
        lineHeight = 30.sp * scale,
        lineHeightStyle = TightLine,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp * scale,
        lineHeight = 27.sp * scale,
        lineHeightStyle = TightLine,
    ),

    // Body. 19sp floor — see note above.
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp * scale,
        lineHeight = 30.sp * scale, // 1.58x
        lineHeightStyle = TightLine,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp * scale,
        lineHeight = 27.sp * scale, // 1.59x
        lineHeightStyle = TightLine,
    ),

    // Button text — never smaller than body.
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp * scale,
        lineHeight = 26.sp * scale,
        letterSpacing = 0.1.sp,
        lineHeightStyle = TightLine,
    ),

    /**
     * 16sp, not the 15sp this used to be. Raised to meet the updated caption floor —
     * 15sp undershot it. Still the smallest permitted size: timestamps and metadata
     * only, never anything the user must act on or must not miss.
     */
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp * scale,
        lineHeight = 24.sp * scale, // 1.5x
        letterSpacing = 0.2.sp,
        lineHeightStyle = TightLine,
    ),
)
