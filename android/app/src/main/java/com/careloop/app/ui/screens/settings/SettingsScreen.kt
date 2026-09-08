package com.careloop.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careloop.app.data.mock.MockData
import com.careloop.app.data.model.Caretaker
import com.careloop.app.ui.components.CareCard
import com.careloop.app.ui.components.CarePrimaryButton
import com.careloop.app.ui.components.LoopMark
import com.careloop.app.ui.theme.CareColors
import com.careloop.app.ui.theme.CareDimens
import com.careloop.app.ui.theme.CareLoopTheme
import com.careloop.app.di.AppContainer
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Settings — the screen where Margaret is in charge.
 *
 * ## What this screen is trying to do
 *
 * Everything on this screen is something Margaret owns, not something Sarah configures for
 * her. That framing shapes every section below: the call time is explicitly *hers* to move,
 * the sharing feed is a hero row rather than a buried toggle, and even the section that
 * exists purely for a technical reason (full-screen calls) is written to her, in her
 * language, not as a system dialog borrowed wholesale.
 *
 * ## Sections, and why they're in this order
 * 1. **When Cara calls** — the thing she is most likely to want to change, so it leads.
 * 2. **Let Cara ring like a real phone call** — see the long comment on
 *    [RingPermissionCard]. This is the single most consequential setting on the screen,
 *    even though it looks the most "technical".
 * 3. **What I share with Sarah** — the dignity feature. Deliberately styled as a hero row,
 *    not a list item, because burying it would undercut the whole point of it existing.
 * 4. **Who Cara calls if she's worried** — read-only, warm, and framed as Margaret's
 *    choice rather than an assigned contact.
 * 5. **About Cara** — plain honesty that Cara is an AI. Hiding this would be the easy
 *    choice and the wrong one; transparency about the agent's nature is what earns trust
 *    here, not what risks it.
 * 6. **Help** — the low-effort escape hatch, last because it's needed least often.
 */
@Composable
fun SettingsScreen(
    onOpenWhatIShared: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // MockData.elder as the seed value means the screen never flashes empty while the
    // (instant, in-memory) flow catches up — same pattern as MedicationsScreen.
    val elder by AppContainer.repository.observeElder()
        .collectAsStateWithLifecycle(initialValue = MockData.elder)

    val scope = rememberCoroutineScope()

    // Demo stand-in for the real Android 14 special-access permission. See the long
    // comment on RingPermissionCard for why this exists and what it is standing in for.
    // Starts granted so the happy path is what judges see first; the switch inside
    // RingPermissionCard flips it either way so both states can be shown live on stage.
    var fullScreenCallsGranted by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CareDimens.ScreenPadding, vertical = CareDimens.SpaceLg),
        verticalArrangement = Arrangement.spacedBy(CareDimens.SpaceXl),
    ) {
        Column {
            Text("Settings", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(CareDimens.SpaceSm))
            Text(
                "Everything here is yours to change, Margaret.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        CallTimeCard(
            checkInTime = elder.dailyCheckInTime,
            onTimeChange = { newTime ->
                scope.launch { AppContainer.repository.updateCheckInTime(newTime) }
            },
        )

        RingPermissionCard(
            granted = fullScreenCallsGranted,
            onGrantedChange = { fullScreenCallsGranted = it },
        )

        WhatISharedCard(onClick = onOpenWhatIShared)

        WhoCaraCallsCard(caretaker = elder.caretaker)

        AboutCaraCard()

        HelpCard(caretaker = elder.caretaker)

        // Trailing breathing room so the last card never sits flush with the bottom edge.
        Spacer(Modifier.height(CareDimens.SpaceMd))
    }
}

// ---------------------------------------------------------------------------
// 1. When Cara calls
// ---------------------------------------------------------------------------

/**
 * Big +/- steppers rather than a dial, for the same reason the onboarding call-time step
 * uses them: dragging a small arc precisely is exactly the fine-motor task that hand tremor
 * defeats, while a stepper turns "pick a time" into a sequence of large, unambiguous taps.
 *
 * The hour stepper walks the underlying 24-hour value directly (rather than a 12-hour value
 * plus a separate AM/PM toggle) so crossing noon or midnight is just another tap, not a
 * second control to manage. The formatted headline above it is what actually reads as
 * 12-hour time.
 *
 * Every change calls [onTimeChange] immediately — there is no separate "Save" step. The
 * value shown is always [checkInTime] as it comes back from the repository, so the display
 * can never drift from what is actually scheduled.
 */
@Composable
private fun CallTimeCard(
    checkInTime: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()) }

    CareCard(modifier = modifier) {
        CardHeader(icon = Icons.Rounded.Schedule, title = "When Cara calls")
        Spacer(Modifier.height(CareDimens.SpaceXs))
        Text(
            "This is your call time, Margaret. Sarah can see it, but only you change it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(CareDimens.SpaceLg))

        Text(
            text = checkInTime.format(formatter),
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(CareDimens.SpaceLg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TimeStepper(
                label = "Hour",
                value = display12Hour(checkInTime.hour).toString(),
                decrementDescription = "One hour earlier",
                incrementDescription = "One hour later",
                onDecrement = {
                    onTimeChange(LocalTime.of(decHour24(checkInTime.hour), checkInTime.minute))
                },
                onIncrement = {
                    onTimeChange(LocalTime.of(incHour24(checkInTime.hour), checkInTime.minute))
                },
            )
            TimeStepper(
                label = "Minutes",
                value = checkInTime.minute.toString().padStart(2, '0'),
                decrementDescription = "Five minutes earlier",
                incrementDescription = "Five minutes later",
                onDecrement = {
                    onTimeChange(LocalTime.of(checkInTime.hour, decMinute5(checkInTime.minute)))
                },
                onIncrement = {
                    onTimeChange(LocalTime.of(checkInTime.hour, incMinute5(checkInTime.minute)))
                },
            )
        }
    }
}

@Composable
private fun TimeStepper(
    label: String,
    value: String,
    decrementDescription: String,
    incrementDescription: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CareDimens.SpaceSm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton(
                symbol = "−",
                contentDescription = decrementDescription,
                onClick = onDecrement,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 84.dp),
            )
            StepperButton(
                symbol = "+",
                contentDescription = incrementDescription,
                onClick = onIncrement,
            )
        }
    }
}

/**
 * A plain text glyph rather than an [Icon] — deliberately. Every icon on this screen must
 * carry a visible text label, and a small circular +/- control has no room for one without
 * crowding it. A large text symbol reads just as clearly and sidesteps the question, while
 * [contentDescription] still gives it a real accessible name.
 */
@Composable
private fun StepperButton(
    symbol: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(CareDimens.TouchTarget)
            .semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(text = symbol, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Let Cara ring like a real phone call
// ---------------------------------------------------------------------------

/**
 * ## Why this section exists
 *
 * CareLoop's entire value proposition depends on Cara's call actually being noticed. From
 * Android 14 (API 34) onward, `USE_FULL_SCREEN_INTENT` is no longer freely granted —
 * it became a special-access permission that a health/medication app like this one does
 * not automatically qualify for (see the long comment on `CallNotifier.canRingFullScreen`).
 * Declaring the permission in the manifest is not enough on a modern device; the user (or,
 * realistically, whoever set the phone up for them) has to explicitly flip it on in system
 * settings.
 *
 * ## What breaks without it
 *
 * Without the permission, `NotificationManager.canUseFullScreenIntent()` returns `false`
 * and Cara's incoming call quietly degrades from a real, screen-filling call to a heads-up
 * notification banner — something Margaret can very easily miss if her phone is face-down,
 * across the room, or she's mid-conversation with someone else. For a product whose entire
 * premise is "Cara calls you like family would", a missed banner is not a minor visual
 * regression — it is the daily check-in silently not happening.
 *
 * ## Why this is in Settings at all, not just onboarding
 *
 * The system can revoke or reset this permission (a phone update, a "clear all
 * notifications" cleanup, a well-meaning relative "tidying up" permissions) without
 * Margaret ever asking for it to change. If the only place this is explained is a step she
 * clicked past once during setup, she has no way to notice or fix it later. This screen is
 * that way back in.
 *
 * ## What is and isn't real here
 *
 * [granted] is a hardcoded, in-memory demo flag — this screen never calls a real Android
 * permission API. In the shipped app:
 *  - the actual state should come from `NotificationManager.canUseFullScreenIntent()`,
 *    re-checked on resume (permissions granted in system settings don't push a callback
 *    back into the app);
 *  - the "Turn on" button should launch
 *    `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` with this app's package, landing
 *    the user directly on the right system toggle rather than a generic settings page.
 *
 * The inline switch is the same idea in miniature: flipping it does not touch the OS, it
 * only flips the local demo flag, so both states can be shown live without leaving the app.
 *
 * ## Language
 *
 * Never "full-screen intent" to Margaret — that is implementation detail, not something a
 * person needs to reason about. She needs to know two things: what she'll experience
 * ("Cara's call rings and fills the screen, like a call from family") and what happens if
 * it's off ("her call might just be a small banner you could miss").
 */
@Composable
private fun RingPermissionCard(
    granted: Boolean,
    onGrantedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    CareCard(modifier = modifier) {
        // The status row. Colour alone never carries the meaning here: the icon changes
        // (a phone versus a small bell), the headline text changes, and the caption text
        // changes — three independent, non-colour signals for the same state.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (granted) Icons.Rounded.Phone else Icons.Rounded.Notifications,
                contentDescription = null,
                tint = if (granted) CareColors.Good else CareColors.Concern,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(CareDimens.SpaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Let Cara ring like a real phone call",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(CareDimens.SpaceXs))
                Text(
                    text = if (granted) {
                        "On — her calls ring and fill the screen"
                    } else {
                        "Off — her calls show as a small banner"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (granted) CareColors.Good else CareColors.Concern,
                )
            }
            Spacer(Modifier.width(CareDimens.SpaceSm))
            Switch(
                checked = granted,
                // TODO(backend): a real toggle here can't grant the permission directly —
                // Android requires the user to do that in system settings. This should
                // instead launch Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT and let
                // the resumed state (via NotificationManager.canUseFullScreenIntent()) be
                // the source of truth. Left as a direct local flip for the demo only.
                onCheckedChange = onGrantedChange,
            )
        }

        Spacer(Modifier.height(CareDimens.SpaceLg))

        if (granted) {
            ExplainerRow(
                icon = Icons.Rounded.Phone,
                text = "When Cara calls, your phone will ring and fill the screen — the " +
                    "same as a call from family. It's hard to miss by accident.",
            )
        } else {
            ExplainerRow(
                icon = Icons.Rounded.Notifications,
                text = "Right now, when Cara calls, it may only show as a small banner at " +
                    "the top of the screen. That's easy to miss — especially if the phone " +
                    "is face-down or in another room.",
            )

            Spacer(Modifier.height(CareDimens.SpaceLg))

            CarePrimaryButton(
                text = "Turn on full-screen calls",
                icon = Icons.Rounded.Phone,
                onClick = {
                    // TODO(backend): launch Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
                    // (with a package: URI extra for this app) instead of flipping local
                    // state. Re-check NotificationManager.canUseFullScreenIntent() when the
                    // user returns to the app rather than assuming the button press worked.
                    onGrantedChange(true)
                },
            )
        }
    }
}

@Composable
private fun ExplainerRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(CareDimens.SpaceSm))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// 3. What I share with Sarah — the dignity feature
// ---------------------------------------------------------------------------

/**
 * Deliberately a hero row, not a list item. This is the feature that makes monitoring feel
 * like partnership rather than surveillance — Margaret can see exactly what Cara told Sarah
 * and add her own context — so it gets real visual weight (a filled navy surface, its own
 * icon, a chevron) rather than living as one more line among plain settings rows where it
 * would read as an afterthought.
 */
@Composable
private fun WhatISharedCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CareDimens.LargeTouchTarget),
        shape = RoundedCornerShape(CareDimens.CardRadius),
        color = CareColors.Navy,
        contentColor = CareColors.White,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CareDimens.SpaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = CareColors.White,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(CareDimens.SpaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "What I share with Sarah",
                    style = MaterialTheme.typography.titleLarge,
                    color = CareColors.White,
                )
                Spacer(Modifier.height(CareDimens.SpaceXs))
                Text(
                    "See exactly what Cara's told her, and add your own note if she's " +
                        "got it wrong.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CareColors.White.copy(alpha = 0.82f),
                )
            }
            Spacer(Modifier.width(CareDimens.SpaceSm))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = CareColors.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 4. Who Cara calls if she's worried
// ---------------------------------------------------------------------------

/**
 * Read-only by design — changing who Cara escalates to is a bigger decision than this
 * screen should invite lightly, and it isn't something the mock repository exposes a
 * mutation for. Framed warmly, as the person Margaret picked, not an assigned supervisor.
 */
@Composable
private fun WhoCaraCallsCard(
    caretaker: Caretaker,
    modifier: Modifier = Modifier,
) {
    CareCard(modifier = modifier) {
        CardHeader(icon = Icons.Rounded.Person, title = "Who Cara calls if she's worried")
        Spacer(Modifier.height(CareDimens.SpaceXs))
        Text(
            "The person you've chosen — not someone assigned to you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(CareDimens.SpaceLg))

        Text(caretaker.name, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(CareDimens.SpaceXs))
        Text(
            caretaker.relationship,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(CareDimens.SpaceMd))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Phone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(CareDimens.SpaceSm))
            Text(
                caretaker.phone,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 5. About Cara
// ---------------------------------------------------------------------------

/**
 * Plain honesty that Cara is an AI, not a person — stated once, clearly, near the Loop
 * mark itself. Trust research on companion technology for older adults is consistent that
 * disclosing the agent's nature helps adoption rather than harming it; obscuring it would
 * be the easy choice here and the wrong one. The line about only listening during a call is
 * included for the same reason: it answers the privacy question before it has to be asked.
 */
@Composable
private fun AboutCaraCard(modifier: Modifier = Modifier) {
    CareCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoopMark(size = CareDimens.LoopSmall)
            Spacer(Modifier.width(CareDimens.SpaceMd))
            Text("About Cara", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(CareDimens.SpaceLg))

        Text(
            "Cara is a computer program — an AI companion, not a person — who calls you " +
                "once a day to check in on your medications. She listens, asks how you're " +
                "doing, and lets Sarah know if something seems worrying.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(Modifier.height(CareDimens.SpaceMd))

        ExplainerRow(
            icon = Icons.Rounded.VolumeUp,
            text = "She only listens while you're on a call with her — never in between " +
                "calls, and never when you haven't picked up.",
        )
    }
}

// ---------------------------------------------------------------------------
// 6. Help
// ---------------------------------------------------------------------------

@Composable
private fun HelpCard(
    caretaker: Caretaker,
    modifier: Modifier = Modifier,
) {
    CareCard(modifier = modifier) {
        CardHeader(icon = Icons.Rounded.Help, title = "Need a hand?")
        Spacer(Modifier.height(CareDimens.SpaceXs))
        Text(
            "If anything on this screen is confusing, ${caretaker.name.substringBefore(' ')} " +
                "is one tap away.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(CareDimens.SpaceLg))

        CarePrimaryButton(
            text = "Call my ${caretaker.relationship.lowercase()}",
            icon = Icons.Rounded.Phone,
            // TODO(backend): launch an ACTION_DIAL (or ACTION_CALL, if CALL_PHONE is ever
            // requested) intent with caretaker.phone. UI-only for now — no telephony calls
            // are placed from this screen.
            onClick = {},
        )
    }
}

// ---------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------

@Composable
private fun CardHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(CareDimens.SpaceSm))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

// ---------------------------------------------------------------------------
// Time helpers
// ---------------------------------------------------------------------------
//
// Plain Int math on the 24-hour value, mirroring the wrap-around style used by the
// onboarding call-time step — the difference is this stepper walks 0..23 directly rather
// than a separate 12-hour + AM/PM pair, so there is only ever one thing to tap through.

private fun display12Hour(hour24: Int): Int {
    val h = hour24 % 12
    return if (h == 0) 12 else h
}

private fun incHour24(hour24: Int): Int = (hour24 + 1) % 24

private fun decHour24(hour24: Int): Int = (hour24 + 23) % 24

/** Five-minute steps — enough taps to feel precise, few enough to not be a chore. */
private fun incMinute5(minute: Int): Int = (minute + 5) % 60

private fun decMinute5(minute: Int): Int = (minute + 55) % 60

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, name = "Settings — light")
@Composable
private fun SettingsScreenPreview() {
    CareLoopTheme(darkTheme = false) {
        SettingsScreen()
    }
}

@Preview(showBackground = true, name = "Settings — dark")
@Composable
private fun SettingsScreenDarkPreview() {
    CareLoopTheme(darkTheme = true) {
        SettingsScreen()
    }
}

@Preview(showBackground = true, name = "Ring permission — granted")
@Composable
private fun RingPermissionCardOnPreview() {
    CareLoopTheme(darkTheme = false) {
        Column(modifier = Modifier.padding(CareDimens.SpaceLg)) {
            RingPermissionCard(granted = true, onGrantedChange = {})
        }
    }
}

@Preview(showBackground = true, name = "Ring permission — not granted")
@Composable
private fun RingPermissionCardOffPreview() {
    CareLoopTheme(darkTheme = false) {
        Column(modifier = Modifier.padding(CareDimens.SpaceLg)) {
            RingPermissionCard(granted = false, onGrantedChange = {})
        }
    }
}

@Preview(showBackground = true, name = "What I share — hero row")
@Composable
private fun WhatISharedCardPreview() {
    CareLoopTheme(darkTheme = false) {
        Column(modifier = Modifier.padding(CareDimens.SpaceLg)) {
            WhatISharedCard(onClick = {})
        }
    }
}
