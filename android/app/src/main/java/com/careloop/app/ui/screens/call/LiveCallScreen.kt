package com.careloop.app.ui.screens.call

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.careloop.app.data.mock.MockData
import com.careloop.app.data.model.*
import com.careloop.app.ui.components.LoopMark
import com.careloop.app.ui.theme.CareColors
import com.careloop.app.ui.theme.CareDimens
import com.careloop.app.ui.theme.CareLoopTheme
import kotlinx.coroutines.delay

/**
 * The live call.
 *
 * This screen exists to make something invisible legible: that Cara is *reasoning while
 * she talks*, not running a script. Three things carry that:
 *
 * 1. **The Loop's state changes with what she is actually doing** — listening, speaking,
 *    thinking, or checking something in the background.
 * 2. **The interaction check happens mid-conversation without the call going silent.**
 *    When Margaret mentions ibuprofen, the Loop picks up a second counter-rotating arc and
 *    the status line changes, while the conversation carries on underneath. That is the
 *    visual proof of async function-calling, and it is the single clearest demonstration
 *    of agency in the product.
 * 3. **The transcript is live**, so a judge watching over a shoulder can follow exactly
 *    what was said and when the agent acted on it.
 *
 * Tonight this plays [MockData] on a timer. The structure is deliberately the same shape a
 * real Gemini Live session would drive, so wiring the socket later replaces the timer and
 * nothing else.
 *
 * TODO(backend): replace [ScriptedCallPlayer] with a Gemini Live WebSocket session.
 *   - Stream mic audio up as 16-bit PCM; play received audio through AudioTrack.
 *   - Map partial transcripts onto [TranscriptLine] and append as they arrive.
 *   - Register `check_drug_interaction` as an async function call; when the model invokes
 *     it, set activity to [CaraActivity.CHECKING] and surface the result as an
 *     [InteractionAlert] exactly as the mock does here.
 *   - Handle reconnects with backoff: a dropped socket mid-call must not end the call
 *     silently. Verify current Gemini Live socket stability before relying on it.
 */
@Composable
fun LiveCallScreen(
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val script = remember { MockData.checkIns.first { it.id == "ci-2" }.transcript }

    var visibleLines by remember { mutableStateOf(listOf<TranscriptLine>()) }
    var activity by remember { mutableStateOf(CaraActivity.SPEAKING) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var interaction by remember { mutableStateOf<DrugInteraction?>(null) }

    ScriptedCallPlayer(
        script = script,
        onLine = { line -> visibleLines = visibleLines + line },
        onActivityChange = { activity = it },
        onInteractionFound = { interaction = it },
        onTick = { elapsedSeconds = it },
    )

    val listState = rememberLazyListState()
    LaunchedEffect(visibleLines.size) {
        if (visibleLines.isNotEmpty()) {
            listState.animateScrollToItem(visibleLines.lastIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(CareColors.Navy, CareColors.NavyDeep))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = CareDimens.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(CareDimens.SpaceLg))

            // --- Cara, and what she is doing right now ---
            LoopMark(size = CareDimens.LoopMedium, activity = activity)

            Spacer(Modifier.height(CareDimens.SpaceMd))

            Text("Cara", style = MaterialTheme.typography.headlineMedium, color = CareColors.White)

            Spacer(Modifier.height(CareDimens.SpaceXs))

            Text(
                text = activity.statusLine(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (activity == CaraActivity.CHECKING) CareColors.Yellow
                else CareColors.White.copy(alpha = 0.66f),
            )

            Spacer(Modifier.height(CareDimens.SpaceXs))

            Text(
                text = formatDuration(elapsedSeconds),
                style = MaterialTheme.typography.labelMedium,
                color = CareColors.White.copy(alpha = 0.45f),
            )

            Spacer(Modifier.height(CareDimens.SpaceLg))

            // --- The live interaction catch ---
            AnimatedVisibility(
                visible = interaction != null,
                enter = fadeIn() + expandVertically() + slideInVertically { it / 3 },
            ) {
                interaction?.let { InteractionAlert(it) }
            }

            // --- Transcript ---
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CareDimens.SpaceMd),
                contentPadding = PaddingValues(vertical = CareDimens.SpaceMd),
            ) {
                items(visibleLines) { line -> TranscriptBubble(line) }
            }

            // --- End call ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(CareDimens.CallActionSize)
                        .clip(CircleShape)
                        .background(Color(0xFFB3261E))
                        .clickable(onClick = onEndCall),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.CallEnd,
                        contentDescription = null,
                        tint = CareColors.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Spacer(Modifier.height(CareDimens.SpaceSm))
                // Labelled, like every other control in the app.
                Text("End call", style = MaterialTheme.typography.labelLarge, color = CareColors.White)
            }

            Spacer(Modifier.height(CareDimens.SpaceLg))
        }
    }
}

/**
 * Drives the scripted conversation.
 *
 * Playback is time-compressed against the real transcript offsets so a ~100-second call
 * demos in about 35 seconds, while keeping the *rhythm* of the original — the pauses land
 * where they actually landed, which is what makes it feel like a conversation rather than
 * text appearing on a timer.
 */
@Composable
private fun ScriptedCallPlayer(
    script: List<TranscriptLine>,
    onLine: (TranscriptLine) -> Unit,
    onActivityChange: (CaraActivity) -> Unit,
    onInteractionFound: (DrugInteraction) -> Unit,
    onTick: (Int) -> Unit,
) {
    LaunchedEffect(script) {
        val speedFactor = 0.35
        var previousOffset = 0

        script.forEach { line ->
            val gapMs = ((line.offsetSeconds - previousOffset) * 1000 * speedFactor).toLong()
            previousOffset = line.offsetSeconds

            // Show who is about to hold the floor before their words appear.
            onActivityChange(
                if (line.speaker == Speaker.CARA) CaraActivity.SPEAKING else CaraActivity.LISTENING
            )
            delay(gapMs.coerceAtLeast(700L))

            onLine(line)
            onTick(line.offsetSeconds)

            if (line.flag == TranscriptFlag.INTERACTION_CHECK) {
                // The key beat: Margaret mentions ibuprofen. Cara starts checking it
                // against her other medications *without pausing the conversation*.
                onActivityChange(CaraActivity.CHECKING)
                delay(1800)
                onInteractionFound(MockData.warfarinIbuprofen)
            }
        }

        onActivityChange(CaraActivity.LISTENING)
    }
}

@Composable
private fun TranscriptBubble(line: TranscriptLine) {
    val isCara = line.speaker == Speaker.CARA

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isCara) Alignment.Start else Alignment.End,
    ) {
        Text(
            text = if (isCara) "Cara" else MockData.elder.preferredName,
            style = MaterialTheme.typography.labelMedium,
            color = CareColors.White.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(CareDimens.SpaceXs))
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    color = if (isCara) CareColors.NavySoft else CareColors.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(CareDimens.CardRadius),
                )
                .padding(CareDimens.SpaceMd),
        ) {
            Text(
                text = line.text,
                style = MaterialTheme.typography.bodyMedium,
                color = CareColors.White,
            )
        }

        // Why the agent reacted here. Makes the reasoning traceable to actual words.
        line.flag?.annotation()?.let { note ->
            Spacer(Modifier.height(CareDimens.SpaceXs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    tint = CareColors.Yellow,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(CareDimens.SpaceXs))
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelMedium,
                    color = CareColors.Yellow,
                )
            }
        }
    }
}

/**
 * The mid-call safety catch.
 *
 * Shows what it means and *why it happens*, not just "avoid". Explaining the mechanism is
 * what actually earns compliance — and it is also what distinguishes an agent that
 * understands the interaction from a lookup table that matched two strings.
 */
@Composable
private fun InteractionAlert(interaction: DrugInteraction) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = CareColors.Yellow.copy(alpha = 0.13f),
                shape = RoundedCornerShape(CareDimens.CardRadius),
            )
            .padding(CareDimens.SpaceLg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = CareColors.Yellow,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(CareDimens.SpaceSm))
            Text(
                text = "${interaction.drugA} and ${interaction.drugB}",
                style = MaterialTheme.typography.titleMedium,
                color = CareColors.Yellow,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(CareDimens.SpaceSm))
        Text(
            text = interaction.whatItMeans,
            style = MaterialTheme.typography.bodyMedium,
            color = CareColors.White,
        )
        Spacer(Modifier.height(CareDimens.SpaceSm))
        Text(
            text = interaction.mechanism,
            style = MaterialTheme.typography.bodyMedium,
            color = CareColors.White.copy(alpha = 0.72f),
        )
        Spacer(Modifier.height(CareDimens.SpaceSm))
        Text(
            text = "Checked against ${interaction.source}",
            style = MaterialTheme.typography.labelMedium,
            color = CareColors.White.copy(alpha = 0.45f),
        )
    }
}

private fun CaraActivity.statusLine(): String = when (this) {
    CaraActivity.LISTENING -> "Listening"
    CaraActivity.SPEAKING -> "Speaking"
    CaraActivity.THINKING -> "Thinking"
    CaraActivity.CHECKING -> "Checking your medicines while we talk"
}

private fun TranscriptFlag.annotation(): String? = when (this) {
    TranscriptFlag.INTERACTION_CHECK -> "Cara started checking this against your medicines"
    TranscriptFlag.OBSERVATION -> "Cara noticed some uncertainty here"
    TranscriptFlag.SAFETY_CONCERN -> "Cara raised a safety concern"
}

private fun formatDuration(seconds: Int): String =
    "%d:%02d".format(seconds / 60, seconds % 60)

@Preview(showBackground = true, widthDp = 400, heightDp = 880)
@Composable
private fun LiveCallPreview() {
    CareLoopTheme(darkTheme = true) {
        LiveCallScreen(onEndCall = {})
    }
}
