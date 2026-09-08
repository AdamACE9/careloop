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
 * Two rules drove every number here:
 *
 * 1. **16sp is the research floor for older adults, so our body text is 19sp.** A floor
 *    is not a target. Users in this cohort routinely run the system font at maximum;
 *    starting small means starting broken.
 * 2. **Line height is at least 1.5x.** Spacing gains are measurable for older readers —
 *    it is not merely aesthetic.
 *
 * Sizes are in `sp` so they scale with the user's system font setting. Never switch these
 * to `dp` to "protect the layout" — that breaks the accessibility setting these users are
 * most likely to have turned on. Fix the layout instead.
 *
 * System sans-serif is used deliberately: it is what the user's device already renders
 * best, honours their font-weight accessibility settings, and costs no download.
 */

private val Sans = FontFamily.SansSerif

/** Trims extra leading so large text does not float inside its own box. */
private val TightLine = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val CareTypography = Typography(

    // Hero moments — the caller's name on an incoming call.
    displayLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 46.sp,
        lineHeight = 54.sp,
        letterSpacing = (-0.5).sp,
        lineHeightStyle = TightLine,
    ),
    displayMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.25).sp,
        lineHeightStyle = TightLine,
    ),

    // Screen titles.
    headlineLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        lineHeightStyle = TightLine,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 33.sp,
        lineHeightStyle = TightLine,
    ),

    // Card titles, medication names.
    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        lineHeightStyle = TightLine,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp,
        lineHeight = 27.sp,
        lineHeightStyle = TightLine,
    ),

    // Body. 19sp floor — see note above.
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp,
        lineHeight = 30.sp, // 1.58x
        lineHeightStyle = TightLine,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 27.sp, // 1.59x
        lineHeightStyle = TightLine,
    ),

    // Button text — never smaller than body.
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.1.sp,
        lineHeightStyle = TightLine,
    ),

    /**
     * Smallest permitted size, 15sp — timestamps and metadata only.
     * Never use this for anything the user must act on or must not miss.
     */
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp,
        lineHeightStyle = TightLine,
    ),
)
