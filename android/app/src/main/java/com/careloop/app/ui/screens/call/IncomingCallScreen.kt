package com.careloop.app.ui.screens.call

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.careloop.app.data.model.CaraActivity
import com.careloop.app.ui.components.LoopMark
import com.careloop.app.ui.theme.CareColors
import com.careloop.app.ui.theme.CareDimens
import com.careloop.app.ui.theme.CareLoopTheme

/**
 * The ringing screen — CareLoop's signature moment.
 *
 * Design intent: this must read instantly as *a call from someone who cares*, not as an app
 * notification. Margaret may be seeing this on a lock screen, without her glasses, from
 * across a room. So:
 *
 * - Cara's name is set at display size. It is the single most important thing on screen.
 * - The Loop breathes slowly. Motion signals "waiting for you", unhurried — a fast pulse
 *   would read as urgency and make a routine check-in feel like an emergency.
 * - **Answer and decline both carry text labels under the icons.** Icon-only call controls
 *   are near-universal in phone UIs and are exactly the convention that fails this audience:
 *   research shows older adults do not reliably decode digital iconography, and getting this
 *   wrong means declining a call you meant to answer.
 * - The two actions are far apart and large (88dp), because a mis-tap here is costly and
 *   tremor makes precise targets unreliable.
 * - Green/red is reinforced by both icon shape and words, never carrying meaning alone.
 */
@Composable
fun IncomingCallScreen(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "incoming")

    // A very slow drift on the background gradient. Barely perceptible, but it stops the
    // screen feeling like a frozen image while it waits to be answered.
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        CareColors.Navy,
                        CareColors.NavyDeep,
                        Color(0xFF0A1228),
                    ),
                    startY = -200f * drift,
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = CareDimens.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(CareDimens.SpaceXxl))

            Text(
                text = "CareLoop",
                style = MaterialTheme.typography.labelMedium,
                color = CareColors.Gold,
            )

            Spacer(Modifier.weight(0.6f))

            LoopMark(
                size = CareDimens.LoopLarge,
                activity = CaraActivity.LISTENING,
            )

            Spacer(Modifier.height(CareDimens.SpaceXl))

            Text(
                text = "Cara",
                style = MaterialTheme.typography.displayLarge,
                color = CareColors.White,
            )

            Spacer(Modifier.height(CareDimens.SpaceSm))

            Text(
                text = "Your daily check-in",
                style = MaterialTheme.typography.titleMedium,
                color = CareColors.White.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                CallAction(
                    label = "Decline",
                    icon = { Icon(Icons.Rounded.CallEnd, null, tint = CareColors.White, modifier = Modifier.size(36.dp)) },
                    background = Color(0xFFB3261E),
                    onClick = onDecline,
                )
                CallAction(
                    label = "Answer",
                    icon = { Icon(Icons.Rounded.Call, null, tint = CareColors.White, modifier = Modifier.size(36.dp)) },
                    background = Color(0xFF1E7A5A),
                    onClick = onAnswer,
                )
            }

            Spacer(Modifier.height(CareDimens.SpaceXxl))
        }
    }
}

/**
 * A call control. The label is required, not optional — see the screen's comment for why
 * icon-only call buttons are the wrong convention for this audience.
 */
@Composable
private fun CallAction(
    label: String,
    icon: @Composable () -> Unit,
    background: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(CareDimens.CallActionSize)
                .clip(CircleShape)
                .background(background)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.height(CareDimens.SpaceSm))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = CareColors.White,
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 880)
@Composable
private fun IncomingCallPreview() {
    CareLoopTheme(darkTheme = true) {
        IncomingCallScreen(onAnswer = {}, onDecline = {})
    }
}
