package com.careloop.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.careloop.app.data.model.CaraActivity
import com.careloop.app.ui.theme.CareColors
import com.careloop.app.ui.theme.CareDimens
import com.careloop.app.ui.theme.CareLoopTheme

/**
 * "The Loop" — Cara's visual identity.
 *
 * A single unbroken gold ring. It is deliberately **not a face**: research on companion
 * technology for older adults found no measured benefit from avatars for this cohort, and
 * real risk of both uncanny-valley discomfort and infantilisation. ElliQ, the closest
 * shipped precedent, reached the same conclusion and expressed personality through abstract
 * light and motion instead of features.
 *
 * The same mark does three jobs — app icon, in-call listening indicator, call-history
 * avatar — so Cara is recognisable everywhere without ever being a character.
 *
 * The motion carries meaning rather than decoration:
 * - [CaraActivity.LISTENING] — slow, even breathing. She is waiting for you, unhurried.
 * - [CaraActivity.SPEAKING]  — a brighter, quicker pulse.
 * - [CaraActivity.THINKING]  — a travelling arc.
 * - [CaraActivity.CHECKING]  — a second arc counter-rotates, showing background work
 *   (the live interaction lookup) is happening *without* the conversation stopping. This is
 *   the visual tell that the agent is doing something genuinely concurrent.
 */
@Composable
fun LoopMark(
    modifier: Modifier = Modifier,
    size: Dp = CareDimens.LoopMedium,
    activity: CaraActivity = CaraActivity.LISTENING,
    color: Color = CareColors.Gold,
    animated: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "loop")

    val breathDurationMs = when (activity) {
        CaraActivity.LISTENING -> 2600
        CaraActivity.SPEAKING -> 900
        CaraActivity.THINKING -> 1800
        CaraActivity.CHECKING -> 1400
    }

    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(breathDurationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    val sweepRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )

    // Held still for previews and for users who have asked the system to reduce motion.
    val breathValue = if (animated) breath else 0.5f

    Canvas(modifier = modifier.size(size)) {
        val stroke = this.size.minDimension * CareDimens.LoopStrokeRatio
        // Breathing changes radius by only ~4% — enough to read as alive, not as throbbing.
        val radius = (this.size.minDimension / 2f - stroke) * (0.96f + 0.04f * breathValue)
        val centre = Offset(this.size.width / 2f, this.size.height / 2f)
        val topLeft = Offset(centre.x - radius, centre.y - radius)
        val arcSize = Size(radius * 2, radius * 2)

        // Soft halo so the ring sits on dark grounds without a hard edge.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.18f * breathValue), Color.Transparent),
                center = centre,
                radius = radius * 1.55f,
            ),
            radius = radius * 1.55f,
            center = centre,
        )

        // The ring itself — always unbroken.
        drawCircle(
            color = color.copy(alpha = 0.30f + 0.25f * breathValue),
            radius = radius,
            center = centre,
            style = Stroke(width = stroke),
        )

        // Travelling arc for the active states.
        if (activity != CaraActivity.LISTENING) {
            drawArc(
                color = color,
                startAngle = sweepRotation,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }

        // Second, counter-rotating arc: background work in flight. The visual proof that
        // Cara is checking something without the conversation going silent.
        if (activity == CaraActivity.CHECKING) {
            drawArc(
                color = CareColors.Yellow,
                startAngle = -sweepRotation * 1.4f,
                sweepAngle = 40f,
                useCenter = false,
                topLeft = Offset(centre.x - radius * 0.78f, centre.y - radius * 0.78f),
                size = Size(radius * 1.56f, radius * 1.56f),
                style = Stroke(width = stroke * 0.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF16264D)
@Composable
private fun LoopMarkPreview() {
    CareLoopTheme(darkTheme = true) {
        LoopMark(size = 160.dp, activity = CaraActivity.CHECKING)
    }
}
