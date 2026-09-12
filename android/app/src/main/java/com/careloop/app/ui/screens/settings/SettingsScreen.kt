package com.careloop.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Visibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careloop.app.data.model.Caretaker
import com.careloop.app.data.repository.EmptyElderProfile
import com.careloop.app.ui.components.CareCard
import com.careloop.app.ui.components.CarePrimaryButton
import com.careloop.app.ui.components.CareSecondaryButton
import com.careloop.app.ui.components.LoopMark
import com.careloop.app.ui.theme.CareColors
import com.careloop.app.ui.theme.CareDimens
import com.careloop.app.ui.theme.CareLoopTheme
import com.careloop.app.ui.theme.TextScaleStore
import com.careloop.app.ui.theme.TextSize
import com.careloop.app.ui.theme.rememberTextSize
import com.careloop.app.di.AppContainer
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * 3. **Text size** — see [TextSizeCard]. Placed right after the two settings most likely to
 *    need changing, not buried at the bottom, because illegible text is what makes every
 *    other setting on this screen hard to use in the first place.
 * 4. **What I share with Sarah** — the dignity feature. Deliberately styled as a hero row,
 *    not a list item, because burying it would undercut the whole point of it existing.
 * 5. **Who Cara calls if she's worried** — read-only, warm, and framed as Margaret's
 *    choice rather than an assigned contact.
 * 6. **About Cara** — plain honesty that Cara is an AI. Hiding this would be the easy
 *    choice and the wrong one; transparency about the agent's nature is what earns trust
 *    here, not what risks it.
 * 7. **Help** — the low-effort escape hatch, last because it's needed least often.
 */
@Composable
fun SettingsScreen(
    onOpenWhatIShared: () -> Unit = {},
    onOpenAgentThreads: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Seeded EMPTY, not with the example household. Seeding with MockData.elder
    // meant a real account was greeted as Margaret for as long as the read took,
    // and every line below that names a person had "Margaret" and "Sarah"
    // written into it regardless of who was signed in.
    val elder by AppContainer.repository.observeElder()
        .collectAsStateWithLifecycle(initialValue = EmptyElderProfile)

    // The names this screen speaks with. Blank until the read lands, and blank
    // forever for someone with nobody linked, so every sentence below has to
    // read correctly without them.
    val you = elder.preferredName.ifBlank { elder.firstName }
    val carer = elder.caretaker.name.substringBefore(' ').trim()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val textSize = rememberTextSize()

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
                if (you.isBlank()) {
                    "Everything here is yours to change."
                } else {
                    "Everything here is yours to change, $you."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        CallTimeCard(
            caretakerName = carer,
            checkInTime = elder.dailyCheckInTime,
            onTimeChange = { newTime ->
                scope.launch { AppContainer.repository.updateCheckInTime(newTime) }
            },
        )

        RingPermissionCard(
            granted = fullScreenCallsGranted,
            onGrantedChange = { fullScreenCallsGranted = it },
        )

        TextSizeCard(
            currentSize = textSize,
            onSizeChange = { newSize ->
                scope.launch { TextScaleStore.set(context, newSize) }
            },
        )

        WhatISharedCard(caretakerName = carer, onClick = onOpenWhatIShared)

        WhatCaraWatchesCard(onClick = onOpenAgentThreads)

        WhoCaraCallsCard(caretaker = elder.caretaker)

        AboutCaraCard(caretakerName = carer)

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
    caretakerName: String,
    checkInTime: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()) }

    CareCard(modifier = modifier) {
        CardHeader(icon = Icons.Rounded.Schedule, title = "When Cara calls")
        Spacer(Modifier.height(CareDimens.SpaceXs))
        Text(
            // The second clause is only true once somebody is linked. Telling a
            // person with no caretaker that "Sarah can see it" is both wrong and
            // slightly alarming.
            if (caretakerName.isBlank()) {
                "This is your call time, and only you can change it."
            } else {
                "This is your call time. $caretakerName can see it, " +
                    "but only you change it."
            },
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

        // Stacked, not side by side.
        //
        // Two steppers in one row do not fit at this screen's type sizes, let
        // alone at the enlarged sizes the settings below offer: the minutes
        // value wrapped onto two lines and pushed its "+" button off the card.
        // One per row also gives each control the full width, which is the
        // right answer for the reader anyway.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CareDimens.SpaceLg),
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

            MorningEveningPicker(
                isMorning = checkInTime.hour < 12,
                onChange = { morning ->
                    onTimeChange(
                        LocalTime.of(toHour24(checkInTime.hour, morning), checkInTime.minute),
                    )
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
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CareDimens.SpaceSm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StepperButton(
                symbol = "−",
                contentDescription = decrementDescription,
                onClick = onDecrement,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                // weight, not a fixed width: the buttons keep their touch
                // targets and the number takes whatever is left, so nothing is
                // pushed off the card at any font scale.
                modifier = Modifier.weight(1f),
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
 * Morning or evening, as one tap.
 *
 * The hour stepper alone could not express this. It ran 1 to 12 with the
 * meaning hidden in the 24-hour value behind it, so the only way to move 9am to
 * 9pm was to press "+" twelve times, watching the number wrap around twice. On
 * a screen built for someone with a tremor that is not a control, it is an
 * obstacle course, and it is how the call time landed on 9:00 PM by accident
 * while testing.
 *
 * Two labelled buttons rather than a switch: a switch needs a label saying what
 * "on" means, and "on" for a time of day means nothing. These say which one is
 * chosen in words, and the big display above says it again.
 */
@Composable
private fun MorningEveningPicker(
    isMorning: Boolean,
    onChange: (morning: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Morning or evening",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CareDimens.SpaceSm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CareDimens.SpaceMd),
        ) {
            HalfDayButton(
                text = "Morning",
                selected = isMorning,
                onClick = { onChange(true) },
                modifier = Modifier.weight(1f),
            )
            HalfDayButton(
                text = "Evening",
                selected = !isMorning,
                onClick = { onChange(false) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HalfDayButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = CareDimens.LargeTouchTarget),
        shape = RoundedCornerShape(CareDimens.CardRadius),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        // The selected state is carried by the label as well as the fill, so it
        // never depends on telling two similar colours apart.
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (selected) "$text \u2713" else text,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(CareDimens.SpaceMd),
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
                        "On, her calls ring and fill the screen"
                    } else {
                        "Off, her calls show as a small banner"
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
                text = "When Cara calls, your phone will ring and fill the screen, the " +
                    "same as a call from family. It's hard to miss by accident.",
            )
        } else {
            ExplainerRow(
                icon = Icons.Rounded.Notifications,
                text = "Right now, when Cara calls, it may only show as a small banner at " +
                    "the top of the screen. That's easy to miss, especially if the phone " +
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
// 3. Text size
// ---------------------------------------------------------------------------

/**
 * Four steps, not a slider or a percentage. A slider asks for the fine-motor precision this
 * whole app is built to avoid (see [CareDimens]'s doc comment on tremor and failed touches
 * being misread as gestures), and "125%" is a number to interpret, not a size to recognise.
 * Four plain-language, large-target rows do both jobs a slider does — see roughly where you
 * are, move it — without either problem.
 *
 * The live preview text is genuinely live, not a second copy of the logic: it's rendered
 * with [MaterialTheme.typography] like everything else on this screen, and this whole screen
 * sits under `CareLoopTheme(textSize = rememberTextSize())` at the app's root (the default
 * parameter documented on [CareLoopTheme]), so the instant [onSizeChange] persists a new
 * value, the theme recomposes at the new scale and this text visibly changes size along
 * with the rest of the screen. There is nothing here to keep in sync by hand.
 *
 * Each option row's own "Aa" sample, by contrast, is deliberately sized by *that row's*
 * [TextSize.scale] rather than the currently-applied one — Margaret should be able to see
 * what "Largest" looks like without having to select it first just to preview it.
 */
@Composable
private fun TextSizeCard(
    currentSize: TextSize,
    onSizeChange: (TextSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    CareCard(modifier = modifier) {
        CardHeader(icon = Icons.Rounded.TextFields, title = "Text size")
        Spacer(Modifier.height(CareDimens.SpaceXs))
        Text(
            "Choose how big everything looks. It changes right away, everywhere in the app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(CareDimens.SpaceLg))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(CareDimens.ButtonRadius))
                .padding(CareDimens.SpaceMd),
        ) {
            Text(
                "Cara calls at nine o'clock every morning.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Spacer(Modifier.height(CareDimens.SpaceLg))

        TextSize.entries.forEachIndexed { index, size ->
            TextSizeOptionRow(
                size = size,
                selected = size == currentSize,
                onClick = { onSizeChange(size) },
            )
            if (index != TextSize.entries.lastIndex) {
                Spacer(Modifier.height(CareDimens.SpaceSm))
            }
        }
    }
}

@Composable
private fun TextSizeOptionRow(
    size: TextSize,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CareCard(onClick = onClick, selected = selected, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Aa",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp * size.scale),
                modifier = Modifier.widthIn(min = 56.dp),
            )
            Spacer(Modifier.width(CareDimens.SpaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(size.label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(CareDimens.SpaceXs))
                Text(
                    textSizeDescription(size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Plain language, not a percentage — see the class doc on [TextSizeCard]. */
private fun textSizeDescription(size: TextSize): String = when (size) {
    TextSize.NORMAL -> "The size everything starts at."
    TextSize.LARGE -> "A little bigger, still comfortable on the screen."
    TextSize.LARGER -> "Bigger again, easier to read at a glance."
    TextSize.LARGEST -> "As big as the app goes."
}

// ---------------------------------------------------------------------------
// 4. What I share with Sarah — the dignity feature
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
    caretakerName: String,
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
                    if (caretakerName.isBlank()) {
                        "What I share with your family"
                    } else {
                        "What I share with $caretakerName"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = CareColors.White,
                )
                Spacer(Modifier.height(CareDimens.SpaceXs))
                Text(
                    // "them", not "her". The elder is whoever signed up on
                    // this phone; the example household being a woman is not a
                    // fact about the person holding it.
                    "See exactly what Cara's told them, and add your own note " +
                        "if she's got it wrong.",
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
// 5. Who Cara calls if she's worried
// ---------------------------------------------------------------------------

/**
 * Read-only by design — changing who Cara escalates to is a bigger decision than this
 * screen should invite lightly, and it isn't something the mock repository exposes a
 * mutation for. Framed warmly, as the person Margaret picked, not an assigned supervisor.
 */
/**
 * The second half of the transparency pair.
 *
 * "What I share" answers what was said about you. This answers what is being
 * remembered about you, which is the quieter and arguably more unsettling
 * question, and until now the app had no answer to it at all: Cara kept notes
 * on a person that only that person's family could see.
 *
 * Deliberately styled plainly rather than as a second hero row. Two competing
 * hero cards next to each other cancel out, and this one is the calmer of the
 * two: nothing here needs a response, it is simply available.
 */
@Composable
private fun WhatCaraWatchesCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CareCard(onClick = onClick, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(CareDimens.SpaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "What Cara is keeping an eye on",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(CareDimens.SpaceXs))
                Text(
                    "The few things she remembers between calls, in her words.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(CareDimens.SpaceSm))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun WhoCaraCallsCard(
    caretaker: Caretaker,
    modifier: Modifier = Modifier,
) {
    CareCard(modifier = modifier) {
        CardHeader(icon = Icons.Rounded.Person, title = "Who Cara calls if she's worried")
        Spacer(Modifier.height(CareDimens.SpaceXs))
        if (caretaker.name.isNotBlank()) {
            Text(
                "The person you've chosen, not someone assigned to you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(CareDimens.SpaceLg))

        if (caretaker.name.isBlank()) {
            ConnectSomeoneSection()
        } else {
            Text(caretaker.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(CareDimens.SpaceXs))
            Text(
                caretaker.relationship,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (caretaker.phone.isNotBlank()) {
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

            Spacer(Modifier.height(CareDimens.SpaceLg))
            DisconnectSection(caretaker = caretaker)
        }
    }
}

/**
 * Taking someone's access away.
 *
 * Deliberately quiet. This is not a button anybody should be nudged toward, and
 * it is the only genuinely destructive control on a screen used by someone who
 * may have a tremor. So it is a plain text action rather than a button, and it
 * opens a confirmation in place rather than acting on the first tap.
 *
 * Confirmation is inline rather than a dialog on purpose. A dialog has to be
 * dismissed, and dismissing one is exactly the interaction that fails for
 * someone with unsteady hands: a stray tap outside it reads as a cancel, or
 * worse, lands on whatever is underneath. This just expands, and the way out of
 * it is a large button that says what it does.
 *
 * The wording names the consequence rather than the mechanism. "They will no
 * longer see your check-ins" is the thing a person is deciding about. "Remove
 * caretaker link" is not.
 */
@Composable
private fun DisconnectSection(caretaker: Caretaker) {
    var confirming by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val firstName = caretaker.name.substringBefore(' ').trim().ifBlank { "They" }

    if (!confirming) {
        Text(
            text = "Disconnect $firstName",
            style = MaterialTheme.typography.bodyLarge,
            color = CareColors.Slate,
            modifier = Modifier
                .heightIn(min = CareDimens.LargeTouchTarget)
                .fillMaxWidth()
                .clickable { confirming = true }
                .wrapContentHeight(Alignment.CenterVertically),
        )
        return
    }

    Column {
        Text(
            "$firstName will no longer see your check-ins, and Cara will not " +
                "contact them if she is worried. You can connect them again later " +
                "with a new code.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (failed) {
            Spacer(Modifier.height(CareDimens.SpaceSm))
            Text(
                "That did not go through. Nothing has changed. Please try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(CareDimens.SpaceMd))

        // The safe choice first and styled as the primary action, because the
        // most likely reason to be looking at this panel is a mis-tap.
        CarePrimaryButton(
            text = "Keep $firstName connected",
            enabled = !working,
            onClick = { confirming = false; failed = false },
        )

        Spacer(Modifier.height(CareDimens.SpaceSm))

        CareSecondaryButton(
            text = if (working) "Disconnecting" else "Yes, disconnect $firstName",
            enabled = !working,
            onClick = {
                working = true
                failed = false

                // Application scope, not rememberCoroutineScope.
                //
                // The first real run of this failed with
                // ForgottenCoroutineScopeException: the composable left
                // composition between the tap and the call, and took the
                // request with it. This codebase has now been bitten by exactly
                // this three times, and the rule it keeps learning is that a
                // network write which must not be lost cannot live on a scope
                // owned by the thing on screen.
                //
                // Worse here than elsewhere, because the failure is silent and
                // asymmetric: the person believes they revoked somebody's
                // access to their health record, and they did not.
                AppContainer.applicationScope.launch {
                    val result = AppContainer.repository.unlinkCaretaker(caretaker.id)
                    withContext(Dispatchers.Main) {
                        working = false
                        result
                            .onSuccess { confirming = false }
                            .onFailure { failed = true }
                    }
                }
            },
        )
    }
}

/**
 * Getting a code to the person who will watch over you.
 *
 * This existed only inside onboarding, which is a one-time flow, so anyone whose
 * family joined later had no way to produce a code and no way to ever connect
 * them. Onboarding's own failure message said "You can do this later from
 * Settings", which was a promise this screen did not keep.
 *
 * The direction matters and is stated on screen, because it is the thing people
 * get wrong: the code is made HERE, on the elder's phone, and read out to the
 * family member. Only this device can mint one, which is what stops anyone
 * attaching themselves to a stranger's health record.
 *
 * The code is shown large and spaced out, because it is going to be read aloud
 * down a telephone by someone who may not see it well.
 */
@Composable
private fun ConnectSomeoneSection() {
    var code by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    Column {
        Text(
            "Nobody is connected yet. When you are ready, make a code and read " +
                "it out to them. They type it into CareLoop on their computer.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(CareDimens.SpaceLg))

        code?.let { value ->
            Text(
                // Spaced so it can be read aloud a character at a time.
                text = value.toCharArray().joinToString("  "),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(CareDimens.SpaceSm))
            Text(
                "Read this to them. It works once, and only for a little while.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(CareDimens.SpaceLg))
        }

        if (failed) {
            Text(
                "Could not make a code just now. Please try again in a moment.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(CareDimens.SpaceMd))
        }

        CarePrimaryButton(
            text = when {
                loading -> "Making a code"
                code != null -> "Make a new code"
                else -> "Make a code"
            },
            // enabled rather than an early return: a non-local return out of a
            // lambda is the construct that broke this codebase once already.
            enabled = !loading,
            onClick = {
                loading = true
                failed = false
                // Application scope for the same reason as DisconnectSection.
                // A code minted on the server but lost before it reaches the
                // screen is worse than no code: it is a real, valid code the
                // person never sees, and the one they eventually read out is a
                // different one.
                AppContainer.applicationScope.launch {
                    val result = AppContainer.repository.generateLinkingCode()
                    withContext(Dispatchers.Main) {
                        result
                            .onSuccess { code = it.code }
                            .onFailure { failed = true }
                        loading = false
                    }
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// 6. About Cara
// ---------------------------------------------------------------------------

/**
 * Plain honesty that Cara is an AI, not a person — stated once, clearly, near the Loop
 * mark itself. Trust research on companion technology for older adults is consistent that
 * disclosing the agent's nature helps adoption rather than harming it; obscuring it would
 * be the easy choice here and the wrong one. The line about only listening during a call is
 * included for the same reason: it answers the privacy question before it has to be asked.
 */
@Composable
private fun AboutCaraCard(caretakerName: String, modifier: Modifier = Modifier) {
    CareCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoopMark(size = CareDimens.LoopSmall)
            Spacer(Modifier.width(CareDimens.SpaceMd))
            Text("About Cara", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(CareDimens.SpaceLg))

        Text(
            "Cara is a computer program. She is an AI companion, not a person. She " +
                "calls you once a day to check in on your medications, listens, asks how " +
                "you're doing, and lets " +
                    caretakerName.ifBlank { "them" } +
                    " know if something seems worrying.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(Modifier.height(CareDimens.SpaceMd))

        ExplainerRow(
            icon = Icons.Rounded.VolumeUp,
            text = "She only listens while you're on a call with her, never in between " +
                "calls, and never when you haven't picked up.",
        )
    }
}

// ---------------------------------------------------------------------------
// 7. Help
// ---------------------------------------------------------------------------

@Composable
private fun HelpCard(
    caretaker: Caretaker,
    modifier: Modifier = Modifier,
) {
    CareCard(modifier = modifier) {
        CardHeader(icon = Icons.Rounded.Help, title = "Need a hand?")
        Spacer(Modifier.height(CareDimens.SpaceXs))

        // With nobody linked, every version of this card was broken. It read
        // "If anything on this screen is confusing,  is one tap away", with the
        // name simply missing, above a greyed-out button labelled "Call my ".
        // Say the true thing instead.
        val firstName = caretaker.name.substringBefore(' ').trim()
        val relationship = caretaker.relationship.lowercase().trim()

        Text(
            if (firstName.isBlank()) {
                "Once somebody is connected to your CareLoop, you can reach them " +
                    "from here in one tap."
            } else {
                "If anything on this screen is confusing, $firstName is one tap away."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // An `if`, not an early return. A non-local return out of a lambda is
        // the construct that broke this codebase once already.
        //
        // The button appears only when there is a number to dial. One that is
        // always there and sometimes does nothing is worse than one that shows
        // up when it can work, and this one is pressed by somebody who wants
        // their daughter right now.
        if (caretaker.phone.isNotBlank()) {
            Spacer(Modifier.height(CareDimens.SpaceLg))

            val context = LocalContext.current
            CarePrimaryButton(
                text = if (relationship.isBlank()) "Call $firstName" else "Call my $relationship",
                icon = Icons.Rounded.Phone,
                onClick = {
                    // ACTION_DIAL, not ACTION_CALL. It opens the dialler with
                    // the number filled in and lets the person press call
                    // themselves. ACTION_CALL would place the call instantly and
                    // needs the CALL_PHONE permission, which is a large thing to
                    // ask for and a bad idea on a screen where a mis-tap is
                    // likely.
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_DIAL,
                                Uri.fromParts("tel", caretaker.phone, null),
                            ),
                        )
                    }
                },
            )
        }
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

/**
 * The 24-hour value for the same clock face reading, in the chosen half of the
 * day. 9 with morning is 09:00; 9 with evening is 21:00.
 */
private fun toHour24(hour24: Int, morning: Boolean): Int {
    val onTheClock = hour24 % 12 // midnight and noon both read as 12
    return if (morning) onTheClock else onTheClock + 12
}

private fun incHour24(hour24: Int): Int = (hour24 + 1) % 24

private fun decHour24(hour24: Int): Int = (hour24 + 23) % 24

/** Five-minute steps — enough taps to feel precise, few enough to not be a chore. */
private fun incMinute5(minute: Int): Int = (minute + 5) % 60

private fun decMinute5(minute: Int): Int = (minute + 55) % 60

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, name = "Settings, light")
@Composable
private fun SettingsScreenPreview() {
    CareLoopTheme(darkTheme = false) {
        SettingsScreen()
    }
}

@Preview(showBackground = true, name = "Settings, dark")
@Composable
private fun SettingsScreenDarkPreview() {
    CareLoopTheme(darkTheme = true) {
        SettingsScreen()
    }
}

@Preview(showBackground = true, name = "Ring permission, granted")
@Composable
private fun RingPermissionCardOnPreview() {
    CareLoopTheme(darkTheme = false) {
        Column(modifier = Modifier.padding(CareDimens.SpaceLg)) {
            RingPermissionCard(granted = true, onGrantedChange = {})
        }
    }
}

@Preview(showBackground = true, name = "Ring permission, not granted")
@Composable
private fun RingPermissionCardOffPreview() {
    CareLoopTheme(darkTheme = false) {
        Column(modifier = Modifier.padding(CareDimens.SpaceLg)) {
            RingPermissionCard(granted = false, onGrantedChange = {})
        }
    }
}

@Preview(showBackground = true, name = "What I share, hero row")
@Composable
private fun WhatISharedCardPreview() {
    CareLoopTheme(darkTheme = false) {
        Column(modifier = Modifier.padding(CareDimens.SpaceLg)) {
            WhatISharedCard(caretakerName = "Sarah", onClick = {})
        }
    }
}

@Preview(showBackground = true, name = "Text size")
@Composable
private fun TextSizeCardPreview() {
    CareLoopTheme(darkTheme = false) {
        Column(modifier = Modifier.padding(CareDimens.SpaceLg)) {
            TextSizeCard(currentSize = TextSize.LARGE, onSizeChange = {})
        }
    }
}

/** Confirms the whole screen, including the text-size rows themselves, survives 200% scale. */
@PreviewFontScale
@Composable
private fun SettingsScreenFontScalePreview() {
    CareLoopTheme(darkTheme = false) {
        SettingsScreen()
    }
}
