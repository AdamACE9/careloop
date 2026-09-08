package com.careloop.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing and sizing constants for elder-facing surfaces.
 *
 * These are deliberately larger than platform defaults. Material's default 48dp touch
 * target is the *research floor* for older adults, not a comfortable value — hand tremor
 * and reduced fingertip precision mean targets at the floor still produce failed touches,
 * and a failed touch on a touchscreen often gets reinterpreted as a stray gesture.
 * We treat 48dp as the minimum and design at 64dp+.
 *
 * If you are tempted to shrink something to fit more on screen: don't. Prefer splitting
 * into two screens. One complete task per screen beats a dense screen the user cannot hit.
 */
object CareDimens {

    // ---- Touch targets ----
    /** Absolute floor. Nothing tappable may be smaller. */
    val MinTouchTarget = 48.dp
    /** Standard tappable row / secondary button. */
    val TouchTarget = 64.dp
    /** Primary actions — "Answer", "I've taken it". */
    val LargeTouchTarget = 80.dp
    /** Answer/decline circles on the incoming-call screen. */
    val CallActionSize = 88.dp

    // ---- Spacing ----
    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 16.dp
    val SpaceLg = 24.dp
    val SpaceXl = 32.dp
    val SpaceXxl = 48.dp

    /** Horizontal page gutter. Generous on purpose — whitespace aids scanning. */
    val ScreenPadding = 24.dp

    // ---- Shape ----
    val CardRadius = 20.dp
    val ButtonRadius = 16.dp
    val PillRadius = 999.dp

    // ---- Elevation ----
    val CardElevation = 1.dp
    val RaisedElevation = 6.dp

    // ---- The Loop mark ----
    val LoopSmall = 40.dp
    val LoopMedium = 96.dp
    val LoopLarge = 200.dp
    val LoopStrokeRatio = 0.085f
}
