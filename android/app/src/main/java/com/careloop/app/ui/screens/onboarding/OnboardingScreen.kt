package com.careloop.app.ui.screens.onboarding

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.careloop.app.data.model.CaraActivity
import com.careloop.app.ui.components.CarePrimaryButton
import com.careloop.app.ui.components.CareSecondaryButton
import com.careloop.app.ui.components.LoopMark
import com.careloop.app.ui.components.SectionHeader
import com.careloop.app.ui.theme.CareColors
import com.careloop.app.ui.theme.CareDimens
import com.careloop.app.ui.theme.CareLoopTheme

/**
 * Onboarding — five screens, no more.
 *
 * ## Why five, and why this shape
 *
 * Onboarding is the single highest-risk drop-off point in the product: the user hasn't
 * felt any value yet, so every extra step is pure cost. Research on onboarding for older
 * adults is consistent on a small set of levers, and this screen leans on all of them:
 *
 * 1. **Step count matters more than content density.** Drop-off rises sharply past ~5
 *    steps, so we fit the whole flow (introduce Cara, who this is for, call time, the
 *    ring permission, confirmation) into exactly 5 rather than splitting further.
 * 2. **"Assisted setup" is the common real path, not an edge case.** Margaret is 78; her
 *    daughter may well be the one holding the phone. Step 2 asks who is setting up rather
 *    than assuming, so neither person is designing for the wrong reader.
 * 3. **Minimise text entry.** There is not a single text field in this entire flow —
 *    every choice is a tap: a card, a stepper, a button. Typing is a documented
 *    onboarding blocker for this cohort, so we simply don't ask for any.
 * 4. **Never trap the user.** Every step past the first has a large "Back" affordance,
 *    and the one step that asks for a real system permission (step 4) still has a "Not
 *    now" that moves forward — declining a permission must never be a dead end.
 * 5. **Show progress plainly.** "Step 2 of 5" plus a filled bar reduces the anxiety of
 *    not knowing how much is left, which is measurably tied to completion.
 *
 * State is intentionally simple: five mutable fields in `remember`, no ViewModel. This
 * screen has no persistence and no async work — a ViewModel here would be ceremony, not
 * architecture.
 */

private const val TOTAL_STEPS = 5

/** Who is holding the phone during setup. See [WhoIsThisForStep]. */
private enum class SetupAudience { SELF, HELPING_PARENT }

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableIntStateOf(0) }

    // Step 2: who is setting this up. Captured but not yet acted on — see the comment
    // on WhoIsThisForStep for what the real branching would look like.
    var audience by remember { mutableStateOf<SetupAudience?>(null) }

    // Step 3: the elder's chosen daily call time. Kept as plain 12-hour ints rather than
    // a LocalTime so the stepper math (wrap-around at 12, wrap-around at 60 minutes) stays
    // trivial to read. Convert to LocalTime only at the point this is actually persisted.
    // Defaults to 9:00am, matching MockData.elder.dailyCheckInTime.
    var callHour12 by remember { mutableIntStateOf(9) }
    var callMinute by remember { mutableIntStateOf(0) }
    var callIsAm by remember { mutableStateOf(true) }

    // Step 4: whether the elder tapped "Allow" or "Not now". Recorded for the confirmation
    // step's own reasoning, but — per this screen's UI-only scope — never wired to a real
    // permission request. See the TODO(backend) on RingPermissionStep.
    var fullScreenIntentAllowed by remember { mutableStateOf<Boolean?>(null) }

    fun goBack() {
        if (step > 0) step -= 1
    }

    fun goNext() {
        if (step < TOTAL_STEPS - 1) step += 1
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = CareDimens.ScreenPadding, vertical = CareDimens.SpaceLg),
        ) {
            OnboardingProgress(step = step, total = TOTAL_STEPS)

            Spacer(Modifier.height(CareDimens.SpaceMd))

            // Reserve the same vertical rhythm whether or not Back is shown, so the step
            // content below doesn't visibly jump between the welcome screen and the rest.
            if (step > 0) {
                BackAffordance(onClick = ::goBack)
            } else {
                Spacer(Modifier.height(CareDimens.TouchTarget))
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (step) {
                    0 -> WelcomeStep(onGetStarted = ::goNext)
                    1 -> WhoIsThisForStep(
                        onSelect = { selected ->
                            audience = selected
                            // TODO(product): a real build would branch here — e.g. adjusting
                            // pronouns/voice for the remaining steps ("your mother" vs "you")
                            // when HELPING_PARENT is chosen. For this flow every subsequent
                            // step is identical regardless of the answer.
                            goNext()
                        },
                    )
                    2 -> CallTimeStep(
                        hour12 = callHour12,
                        minute = callMinute,
                        isAm = callIsAm,
                        onHourChange = { callHour12 = it },
                        onMinuteChange = { callMinute = it },
                        onPeriodChange = { callIsAm = it },
                        onConfirm = ::goNext,
                    )
                    3 -> RingPermissionStep(
                        onAllow = {
                            fullScreenIntentAllowed = true
                            goNext()
                        },
                        onNotNow = {
                            fullScreenIntentAllowed = false
                            goNext()
                        },
                    )
                    else -> AllSetStep(
                        hour12 = callHour12,
                        minute = callMinute,
                        isAm = callIsAm,
                        onDone = onComplete,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 1 — Welcome
// ---------------------------------------------------------------------------

@Composable
private fun WelcomeStep(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Not a face, deliberately — see LoopMark's own doc comment. Cara introduces
        // herself through this same mark she'll use on every call, so it's already
        // familiar by the time the phone actually rings.
        LoopMark(size = CareDimens.LoopLarge, activity = CaraActivity.LISTENING)

        Spacer(Modifier.height(CareDimens.SpaceXl))

        Text(
            text = "Meet Cara",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(CareDimens.SpaceMd))

        Text(
            text = "Cara will call you once a day, at a time you choose, just to check " +
                "in on your medications. It's a real phone call, like one from a friend " +
                "nothing to open, nothing to type.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(CareDimens.SpaceXxl))

        CarePrimaryButton(text = "Get started", onClick = onGetStarted)
    }
}

// ---------------------------------------------------------------------------
// Step 2 — Who's setting this up
// ---------------------------------------------------------------------------

/**
 * "Assisted setup" — an adult child configuring the app for a parent — is a recognised,
 * evidence-backed pattern, not a fallback for when the elder "can't manage it". Asking
 * plainly, up front, means the rest of onboarding can be written for whoever is actually
 * reading it, without guessing.
 */
@Composable
private fun WhoIsThisForStep(
    onSelect: (SetupAudience) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionHeader(
            title = "Who's setting this up?",
            subtitle = "There's no wrong answer here, it just helps us get the next " +
                "few steps right for you.",
        )

        Spacer(Modifier.height(CareDimens.SpaceLg))

        AudienceCard(
            title = "I'm setting this up for myself",
            icon = Icons.Rounded.Person,
            onClick = { onSelect(SetupAudience.SELF) },
        )

        Spacer(Modifier.height(CareDimens.SpaceMd))

        AudienceCard(
            title = "I'm helping my parent",
            icon = Icons.Rounded.Favorite,
            onClick = { onSelect(SetupAudience.HELPING_PARENT) },
        )
    }
}

@Composable
private fun AudienceCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CareDimens.LargeTouchTarget),
        shape = RoundedCornerShape(CareDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = CareDimens.CardElevation),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CareDimens.SpaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(CareDimens.SpaceMd))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Step 3 — Call time
// ---------------------------------------------------------------------------

/**
 * A dial would be the "obvious" time picker — and the wrong one here. Dragging a small
 * arc precisely is exactly the kind of fine-motor task that hand tremor defeats. Big
 * stepper buttons turn "pick a time" into a sequence of large, unambiguous taps instead.
 */
@Composable
private fun CallTimeStep(
    hour12: Int,
    minute: Int,
    isAm: Boolean,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onPeriodChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionHeader(
            title = "What time should Cara call?",
            subtitle = "This is your call time to set, not anyone else's, and you can " +
                "change it again anytime from Settings.",
        )

        Spacer(Modifier.height(CareDimens.SpaceLg))

        Text(
            text = formatTime(hour12, minute, isAm),
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(CareDimens.SpaceXl))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TimeStepper(
                label = "Hour",
                value = hour12.toString(),
                decrementDescription = "One hour earlier",
                incrementDescription = "One hour later",
                onDecrement = { onHourChange(decHour(hour12)) },
                onIncrement = { onHourChange(incHour(hour12)) },
            )
            TimeStepper(
                label = "Minutes",
                value = minute.toString().padStart(2, '0'),
                decrementDescription = "Five minutes earlier",
                incrementDescription = "Five minutes later",
                onDecrement = { onMinuteChange(decMinute(minute)) },
                onIncrement = { onMinuteChange(incMinute(minute)) },
            )
        }

        Spacer(Modifier.height(CareDimens.SpaceLg))

        PeriodToggle(isAm = isAm, onChange = onPeriodChange)

        Spacer(Modifier.height(CareDimens.SpaceXxl))

        CarePrimaryButton(text = "This time works", onClick = onConfirm)
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
            StepperButton(symbol = "−", contentDescription = decrementDescription, onClick = onDecrement)
            Text(
                text = value,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 84.dp),
            )
            StepperButton(symbol = "+", contentDescription = incrementDescription, onClick = onIncrement)
        }
    }
}

/**
 * A plain text glyph rather than an [Icon] — deliberately. Every icon on this screen must
 * carry a visible text label, and a tiny circular +/- control has no room for one without
 * looking cluttered. A large text symbol reads just as clearly and sidesteps the question.
 * [contentDescription] still gives it a real accessible name for screen readers.
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

@Composable
private fun PeriodToggle(
    isAm: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        PeriodOption(label = "AM", selected = isAm, onClick = { onChange(true) })
        Spacer(Modifier.width(CareDimens.SpaceMd))
        PeriodOption(label = "PM", selected = !isAm, onClick = { onChange(false) })
    }
}

@Composable
private fun PeriodOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .widthIn(min = 100.dp)
            .heightIn(min = CareDimens.TouchTarget),
        shape = RoundedCornerShape(CareDimens.ButtonRadius),
        color = if (selected) CareColors.Navy else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) CareColors.White else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            // AM/PM is state communicated by fill colour AND the label text itself is
            // never ambiguous on its own — satisfies "never colour alone" without needing
            // an extra icon.
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ---------------------------------------------------------------------------
// Step 4 — Ring permission (the critical one)
// ---------------------------------------------------------------------------

/**
 * On Android 14+, `USE_FULL_SCREEN_INTENT` is no longer auto-granted to apps like this
 * one. Without it, Cara's incoming call quietly degrades to a banner notification —
 * exactly the kind of thing an elderly user is likely to miss entirely, which defeats the
 * entire premise of a daily check-in call. This screen exists to earn that permission
 * honestly: explain what it does, in plain language, before asking for it.
 *
 * "Not now" still advances. A permission screen that traps the user into granting it is
 * hostile, and we would rather ship a slightly weaker notification experience than ever
 * make Margaret feel cornered by her own phone.
 */
@Composable
private fun RingPermissionStep(
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Icon(
            imageVector = Icons.Rounded.Phone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )

        Spacer(Modifier.height(CareDimens.SpaceMd))

        Text(
            text = "Let Cara ring like a real call",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(Modifier.height(CareDimens.SpaceMd))

        Text(
            text = "Some phones treat CareLoop's call as just a quiet notification, " +
                "easy to miss if you're in another room. Turning this on makes it ring " +
                "and fill the screen instead, the same as a call from family.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(CareDimens.SpaceMd))

        Text(
            text = "You can still decline any call, it simply rings until you do. And " +
                "Cara never listens in unless you pick up.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(CareDimens.SpaceXxl))

        CarePrimaryButton(
            text = "Allow CareLoop to ring",
            onClick = onAllow,
            icon = Icons.Rounded.Phone,
        )

        Spacer(Modifier.height(CareDimens.SpaceMd))

        CareSecondaryButton(text = "Not now", onClick = onNotNow)

        // TODO(backend): wire "Allow CareLoop to ring" to the real permission flow —
        // launch Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT (with a package: URI
        // extra for this app) so the user lands directly on the right system toggle, and
        // check NotificationManager.canUseFullScreenIntent() on return / on resume to
        // reflect the actual granted state. This screen is deliberately UI-only for now
        // and never calls a permission API — both buttons simply advance onboarding.
    }
}

// ---------------------------------------------------------------------------
// Step 5 — Confirmation
// ---------------------------------------------------------------------------

@Composable
private fun AllSetStep(
    hour12: Int,
    minute: Int,
    isAm: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LoopMark(size = CareDimens.LoopMedium, activity = CaraActivity.LISTENING)

        Spacer(Modifier.height(CareDimens.SpaceXl))

        Text(
            text = "You're all set",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(CareDimens.SpaceMd))

        Text(
            text = "Cara will call tomorrow at ${formatTime(hour12, minute, isAm)}.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(CareDimens.SpaceSm))

        Text(
            text = "If that's ever not a good moment, that's alright, you can tell " +
                "her when she calls. Nothing here is locked in.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(CareDimens.SpaceXxl))

        CarePrimaryButton(text = "Done", onClick = onDone, icon = Icons.Rounded.Check)
    }
}

// ---------------------------------------------------------------------------
// Shared chrome — progress and back
// ---------------------------------------------------------------------------

@Composable
private fun OnboardingProgress(
    step: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Step ${step + 1} of $total",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CareDimens.SpaceSm))
        LinearProgressIndicator(
            // Lambda form, not the `progress: Float` overload. That overload was
            // deprecated in Material3 1.2 and is error-level in current versions.
            progress = { (step + 1f) / total },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(CareDimens.PillRadius)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun BackAffordance(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(CareDimens.ButtonRadius))
            .clickable(onClick = onClick)
            .heightIn(min = CareDimens.TouchTarget)
            .padding(horizontal = CareDimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(CareDimens.SpaceSm))
        Text(
            text = "Back",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Time helpers
// ---------------------------------------------------------------------------
//
// Deliberately plain Int math rather than java.time.LocalTime — the stepper only ever
// needs "next hour, wrapping at 12" and "next 5 minutes, wrapping at 60", and keeping that
// as small pure functions makes the wrap-around behaviour easy to check by eye. Convert to
// a real LocalTime only where this gets persisted.

private fun formatTime(hour12: Int, minute: Int, isAm: Boolean): String {
    val paddedMinute = minute.toString().padStart(2, '0')
    val period = if (isAm) "AM" else "PM"
    return "$hour12:$paddedMinute $period"
}

private fun incHour(hour12: Int): Int = if (hour12 >= 12) 1 else hour12 + 1

private fun decHour(hour12: Int): Int = if (hour12 <= 1) 12 else hour12 - 1

/** Five-minute steps — enough taps to feel precise, few enough to not be a chore. */
private fun incMinute(minute: Int): Int = (minute + 5) % 60

private fun decMinute(minute: Int): Int = (minute - 5 + 60) % 60

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, name = "Onboarding, Step 1 Welcome")
@Composable
private fun WelcomeStepPreview() {
    CareLoopTheme {
        WelcomeStep(onGetStarted = {})
    }
}

@Preview(showBackground = true, name = "Onboarding, Step 2 Who")
@Composable
private fun WhoIsThisForStepPreview() {
    CareLoopTheme {
        WhoIsThisForStep(onSelect = {})
    }
}

@Preview(showBackground = true, name = "Onboarding, Step 3 Call time")
@Composable
private fun CallTimeStepPreview() {
    CareLoopTheme {
        CallTimeStep(
            hour12 = 9,
            minute = 0,
            isAm = true,
            onHourChange = {},
            onMinuteChange = {},
            onPeriodChange = {},
            onConfirm = {},
        )
    }
}

@Preview(showBackground = true, name = "Onboarding, Step 4 Ring permission")
@Composable
private fun RingPermissionStepPreview() {
    CareLoopTheme {
        RingPermissionStep(onAllow = {}, onNotNow = {})
    }
}

@Preview(showBackground = true, name = "Onboarding, Step 5 All set")
@Composable
private fun AllSetStepPreview() {
    CareLoopTheme {
        AllSetStep(hour12 = 9, minute = 0, isAm = true, onDone = {})
    }
}

@Preview(showBackground = true, name = "Onboarding, Full screen (step 1)")
@Composable
private fun OnboardingScreenPreview() {
    CareLoopTheme {
        OnboardingScreen(onComplete = {})
    }
}
