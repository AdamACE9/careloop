package com.careloop.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * CareLoop palette.
 *
 * ## Why there are two golds
 *
 * Measured contrast ratios (WCAG 2.1 relative luminance), not estimates:
 *
 * | Pair                       | Ratio    | Verdict                          |
 * |----------------------------|----------|----------------------------------|
 * | Gold `#C9A227` on White    | **2.4:1** | ✗ fails badly — never for text  |
 * | Gold `#C9A227` on Navy     | 6.1:1    | ✓ passes AA, good for text       |
 * | White on Navy              | 14.8:1   | ✓ excellent                      |
 * | GoldDeep `#8A6A00` on White| 5.1:1    | ✓ passes AA                      |
 *
 * So: **bright Gold is a colour for shapes and fills on dark ground. It is not a text
 * colour on white.** Where gold text must sit on a light surface, use [GoldDeep].
 *
 * ## Why this matters more than usual here
 *
 * Age-related colour vision loss degrades **blue-yellow discrimination first** (not
 * red-green, which is the common assumption). Our palette sits exactly on that axis,
 * and yellow-on-white is a specifically documented failure pair for older eyes.
 *
 * Therefore, on elder-facing surfaces: **never encode meaning in hue alone.** Every
 * status colour must be reinforced by an icon, a shape, or a text label. A user who
 * cannot separate our gold from our white must still be able to use the app perfectly.
 */
object CareColors {

    // ---- Brand ----
    val Navy = Color(0xFF16264D)
    val NavyDeep = Color(0xFF0F1A38)
    val NavySoft = Color(0xFF243766)

    val Gold = Color(0xFFC9A227)
    /** Darkened gold, safe as text on white (5.1:1). See table above. */
    val GoldDeep = Color(0xFF8A6A00)
    val Yellow = Color(0xFFFFD166)

    val White = Color(0xFFFFFFFF)

    // ---- Neutrals ----
    /** Page background on light surfaces. Warmer than pure white, less glare. */
    val Bone = Color(0xFFFAF9F6)
    val Cloud = Color(0xFFEFEDE8)
    val Slate = Color(0xFF5A6173)
    /** Body text on light. 10.4:1 on Bone. */
    val Ink = Color(0xFF1A1F2E)

    // ---- Semantic ----
    // Always paired with an icon or label in the UI — never colour alone.
    val Good = Color(0xFF1E7A5A)
    val GoodSurface = Color(0xFFE6F4EF)
    val Concern = Color(0xFFB4690E)
    val ConcernSurface = Color(0xFFFDF0E0)
    val Urgent = Color(0xFFB3261E)
    val UrgentSurface = Color(0xFFFCEEEE)
}
