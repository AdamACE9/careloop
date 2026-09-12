package com.careloop.app.ui.screens.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.careloop.app.di.AppContainer
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
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.VolumeUp
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
 * All of the state on this screen now comes from [LiveCallViewModel], which drives either a
 * real Gemini Live session or the scripted fallback. This screen does not know or care which,
 * with one deliberate exception: when the call is not real it says so, plainly, at the top.
 * A demo that quietly presents itself as live is the one version of this screen worth
 * refusing to build.
 */
@Composable
fun LiveCallScreen(
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveCallViewModel = viewModel(
        factory = LiveCallViewModel.factory(AppContainer.repository),
    ),
) {
    val context = LocalContext.current

    val visibleLines by viewModel.transcript.collectAsStateWithLifecycle()
    val activity by viewModel.activity.collectAsStateWithLifecycle()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val interaction by viewModel.interaction.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val statusNote by viewModel.statusNote.collectAsStateWithLifecycle()
    val muted by viewModel.muted.collectAsStateWithLifecycle()
    val microphoneWorking by viewModel.microphoneWorking.collectAsStateWithLifecycle()

    // Asked here, at the moment it is needed, rather than at launch. Requesting
    // the microphone during onboarding for a call that happens tomorrow is the
    // pattern research consistently finds gets denied.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.start(granted) }

    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            viewModel.start(true)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

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

            // --- Is this a real call? ---
            if (mode != LiveCallViewModel.Mode.LIVE) {
                CallModeNotice(mode = mode, note = statusNote)
                Spacer(Modifier.height(CareDimens.SpaceMd))
            }

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

            // --- The microphone is not working ---
            // Said out loud rather than left to be inferred. On an emulator with
            // no host audio input every buffer comes back silent, so Cara hears
            // nothing however loudly somebody talks. Without this the app looks
            // broken rather than limited, and the person keeps repeating
            // themselves to a machine that cannot hear them.
            if (microphoneWorking == false && !muted) {
                MicrophoneWarning()
                Spacer(Modifier.height(CareDimens.SpaceMd))
            }

            // --- Transcript ---
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (visibleLines.isEmpty()) {
                    Text(
                        text = "What you both say will appear here.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CareColors.White.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = CareDimens.SpaceLg),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(CareDimens.SpaceMd),
                        contentPadding = PaddingValues(vertical = CareDimens.SpaceMd),
                    ) {
                        items(visibleLines) { line -> TranscriptBubble(line) }
                    }
                }
            }

            // --- Controls ---
            // Three controls, each with a word under it. A bare icon row is the
            // usual pattern on a call screen and it is the wrong one here: an
            // unlabelled icon measurably slows this age group down and raises
            // their error rate, and the control that ends a call is not one to
            // be uncertain about.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                CallControl(
                    icon = if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    label = if (muted) "Unmute" else "Mute",
                    background = if (muted) CareColors.Gold else CareColors.White.copy(alpha = 0.14f),
                    tint = if (muted) CareColors.Brown else CareColors.White,
                    onClick = { viewModel.toggleMute() },
                )

                CallControl(
                    icon = Icons.Rounded.CallEnd,
                    label = "End call",
                    background = CareColors.Urgent,
                    tint = CareColors.White,
                    onClick = { viewModel.endCall(onEndCall) },
                )

                CallControl(
                    icon = Icons.Rounded.VolumeUp,
                    label = "Speaker",
                    background = CareColors.White.copy(alpha = 0.14f),
                    tint = CareColors.White,
                    // Deliberately inert and deliberately shown. The call already
                    // plays through the speaker: this is where someone reaches
                    // for it, so removing it would be more confusing than a
                    // control that confirms what is already true.
                    onClick = {},
                )
            }

            Spacer(Modifier.height(CareDimens.SpaceLg))
        }
    }
}

/**
 * Says out loud when the call is not real.
 *
 * Deliberately readable rather than a subtle grey chip. The whole product rests
 * on the person believing what it tells them, and a scripted call presented as a
 * live one would be the single most damaging thing this screen could do.
 */
@Composable
private fun CallModeNotice(mode: LiveCallViewModel.Mode, note: String?) {
    val (label, tint) = when (mode) {
        LiveCallViewModel.Mode.CONNECTING -> "Connecting to Cara" to CareColors.White
        LiveCallViewModel.Mode.DEMO -> "Example call, not a real conversation" to CareColors.Yellow
        LiveCallViewModel.Mode.FAILED -> "Could not reach Cara" to CareColors.Yellow
        LiveCallViewModel.Mode.LIVE -> return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CareColors.White.copy(alpha = 0.09f))
            .padding(CareDimens.SpaceMd),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(CareDimens.SpaceSm))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = tint,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (!note.isNullOrBlank()) {
            Spacer(Modifier.height(CareDimens.SpaceXs))
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = CareColors.White.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun CallControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    background: Color,
    tint: Color,
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
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(CareDimens.SpaceSm))
        Text(label, style = MaterialTheme.typography.labelLarge, color = CareColors.White)
    }
}

@Composable
private fun MicrophoneWarning() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CareColors.Concern.copy(alpha = 0.22f))
            .padding(CareDimens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.MicOff,
            contentDescription = null,
            tint = CareColors.Yellow,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(CareDimens.SpaceSm))
        Text(
            text = "Cara cannot hear anything from this microphone, so she will not answer you.",
            style = MaterialTheme.typography.bodyMedium,
            color = CareColors.White,
        )
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
            // "You" rather than their own name. On their own screen, being
            // addressed in the third person reads like a case file.
            text = if (isCara) "Cara" else "You",
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
