package com.careloop.app.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careloop.app.data.mock.MockData
import com.careloop.app.data.model.CheckIn
import com.careloop.app.data.model.CheckInStatus
import com.careloop.app.di.AppContainer
import com.careloop.app.ui.components.CareEmptyState
import com.careloop.app.ui.components.LoopMark
import com.careloop.app.ui.components.StatusPill
import com.careloop.app.ui.components.initialSeed
import com.careloop.app.ui.theme.CareColors
import com.careloop.app.ui.theme.CareDimens
import com.careloop.app.ui.theme.CareLoopTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Margaret's own record of Cara's calls.
 *
 * This screen exists so a check-in is never something that only happens *to* Margaret
 * somewhere out of sight — she can always look back and see exactly what was said and
 * done, in the same words Cara used with her. It is the elder-side half of the same
 * transparency principle that [com.careloop.app.ui.screens.sharing.WhatISharedScreen]
 * builds on for the caretaker-facing side.
 *
 * The stateful entry point below reads live data; the rendering itself lives in
 * [HistoryScreenContent], which takes plain data as parameters. That split keeps the
 * `@Preview` below cheap and reliable — previews render outside an Activity, and
 * [collectAsStateWithLifecycle] needs a real `LifecycleOwner` that a preview may not
 * supply.
 */
@Composable
fun HistoryScreen(
    onCheckInClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val checkIns by AppContainer.repository.observeCheckIns()
        .collectAsStateWithLifecycle(
            initialValue = initialSeed(empty = emptyList(), demo = MockData.checkIns),
        )

    val today = remember { LocalDate.now() }

    HistoryScreenContent(
        checkIns = checkIns,
        today = today,
        onCheckInClick = onCheckInClick,
        modifier = modifier,
    )
}

@Composable
private fun HistoryScreenContent(
    checkIns: List<CheckIn>,
    today: LocalDate,
    onCheckInClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(checkIns, today) { groupCheckInsByDate(checkIns, today) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = CareDimens.ScreenPadding,
            vertical = CareDimens.SpaceLg,
        ),
        verticalArrangement = Arrangement.spacedBy(CareDimens.SpaceMd),
    ) {
        item(key = "header") {
            Column {
                Text("Your check-ins", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(CareDimens.SpaceXs))
                Text(
                    "Every call Cara's made with you, in her own words. Nothing hidden.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (checkIns.isEmpty()) {
            item(key = "empty") {
                CareEmptyState(
                    title = "No check-ins yet",
                    whatHappensNext = "Cara's first call will show up here once she's spoken with you.",
                )
            }
        }

        groups.forEach { group ->
            item(key = "label-${group.label}") {
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = CareDimens.SpaceSm),
                )
            }
            items(group.checkIns, key = { it.id }) { checkIn ->
                CheckInRow(
                    checkIn = checkIn,
                    onClick = { onCheckInClick(checkIn.id) },
                )
            }
        }

        item(key = "bottom-space") {
            Spacer(Modifier.height(CareDimens.SpaceLg))
        }
    }
}

/**
 * One call, one row. The [LoopMark] stands in for Cara — she has no face, by design (see
 * [com.careloop.app.ui.components.LoopMark]'s doc comment) — and is held still here
 * ([LoopMark]'s `animated` flag doesn't matter for the default `LISTENING` activity, since
 * that state never draws the travelling arc, but passing it explicitly documents intent).
 *
 * The escalated call ([CheckInStatus.ESCALATED]) gets a softly tinted card and a calmer
 * status word than its own enum label ("Family alerted" reads like a verdict). Framing is
 * everything here: Margaret should read this row and think "Cara kept Sarah in the loop",
 * never "I got reported."
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckInRow(
    checkIn: CheckIn,
    onClick: () -> Unit,
) {
    val isEscalated = checkIn.status == CheckInStatus.ESCALATED

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CareDimens.CardRadius),
        colors = CardDefaults.cardColors(
            containerColor = if (isEscalated) CareColors.ConcernSurface else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = CareDimens.CardElevation),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CareDimens.SpaceLg),
        ) {
            LoopMark(size = CareDimens.LoopSmall, animated = false)
            Spacer(Modifier.width(CareDimens.SpaceMd))

            Column(modifier = Modifier.fillMaxWidth()) {
                // FlowRow, not Row: "9:02 AM" and "1 min 18 sec" both grow at 200% font
                // scale, and a plain Row would let the duration run past the card edge
                // rather than drop to its own line.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CareDimens.SpaceSm),
                ) {
                    Text(
                        text = formatCallTime(checkIn.startedAt),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "· ${formatCallDuration(checkIn.durationSeconds)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(CareDimens.SpaceSm))

                StatusPill(
                    text = checkIn.status.warmPillText(),
                    icon = checkIn.status.icon(),
                    contentColor = checkIn.status.pillContentColor(),
                    containerColor = checkIn.status.pillContainerColor(),
                )

                Spacer(Modifier.height(CareDimens.SpaceMd))

                if (checkIn.medicationsConfirmed.isNotEmpty()) {
                    MedicationLine(
                        icon = Icons.Filled.Check,
                        tint = CareColors.Good,
                        label = "Took: ${checkIn.medicationsConfirmed.joinToString(", ")}",
                    )
                }
                if (checkIn.medicationsMissed.isNotEmpty()) {
                    Spacer(Modifier.height(CareDimens.SpaceXs))
                    MedicationLine(
                        icon = Icons.Filled.Close,
                        tint = CareColors.Concern,
                        label = "Missed: ${checkIn.medicationsMissed.joinToString(", ")}",
                    )
                }

                if (checkIn.caraSummary.isNotBlank()) {
                    Spacer(Modifier.height(CareDimens.SpaceMd))
                    Text(
                        text = checkIn.caraSummary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun MedicationLine(icon: ImageVector, tint: Color, label: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(CareDimens.SpaceSm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Status framing
// ---------------------------------------------------------------------------

/**
 * Deliberately not [CheckInStatus.label] verbatim. "Family alerted" is accurate but reads
 * like an accusation; "Cara let Sarah know" describes the same fact as something Cara did
 * *for* Margaret, not something that happened *to* her.
 */
private fun CheckInStatus.warmPillText(): String = when (this) {
    CheckInStatus.COMPLETED -> "All medications taken"
    CheckInStatus.MISSED_DOSE -> "A dose was missed"
    CheckInStatus.NO_ANSWER -> "No answer"
    CheckInStatus.ESCALATED -> "Cara let Sarah know"
}

private fun CheckInStatus.icon(): ImageVector = when (this) {
    CheckInStatus.COMPLETED -> Icons.Filled.Check
    CheckInStatus.MISSED_DOSE -> Icons.Filled.ErrorOutline
    CheckInStatus.NO_ANSWER -> Icons.Filled.Phone
    // A person icon, not a warning triangle — this is "Sarah was told", not "something failed".
    CheckInStatus.ESCALATED -> Icons.Filled.Person
}

private fun CheckInStatus.pillContentColor(): Color = when (this) {
    CheckInStatus.COMPLETED -> CareColors.Good
    // Grouped with "concern", deliberately never "urgent" red — a kept-in-the-loop family
    // member is not an emergency, and the colour shouldn't say otherwise.
    else -> CareColors.Concern
}

private fun CheckInStatus.pillContainerColor(): Color = when (this) {
    CheckInStatus.COMPLETED -> CareColors.GoodSurface
    else -> CareColors.ConcernSurface
}

// ---------------------------------------------------------------------------
// Grouping and formatting
// ---------------------------------------------------------------------------

private data class CheckInGroup(val label: String, val checkIns: List<CheckIn>)

private fun groupCheckInsByDate(checkIns: List<CheckIn>, today: LocalDate): List<CheckInGroup> {
    val yesterday = today.minusDays(1)
    val weekAgo = today.minusDays(6)

    return checkIns
        .sortedByDescending { it.startedAt }
        .groupBy { it.date }
        .toSortedMap(compareByDescending<LocalDate> { it })
        .map { (date, itemsForDate) ->
            val label = when {
                date == today -> "Today"
                date == yesterday -> "Yesterday"
                date >= weekAgo -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))
            }
            CheckInGroup(label, itemsForDate.sortedByDescending { it.startedAt })
        }
}

private val callTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun formatCallTime(dateTime: LocalDateTime): String = dateTime.format(callTimeFormatter)

private fun formatCallDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        minutes <= 0 -> "$seconds sec"
        seconds == 0 -> "$minutes min"
        else -> "$minutes min $seconds sec"
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, widthDp = 400, heightDp = 900)
@Composable
private fun HistoryScreenPreview() {
    CareLoopTheme {
        HistoryScreenContent(
            checkIns = MockData.checkIns,
            today = LocalDate.now(),
            onCheckInClick = {},
        )
    }
}

/** Confirms the call-time/duration FlowRow survives 200% scale. */
@PreviewFontScale
@Composable
private fun HistoryScreenFontScalePreview() {
    CareLoopTheme {
        HistoryScreenContent(
            checkIns = MockData.checkIns,
            today = LocalDate.now(),
            onCheckInClick = {},
        )
    }
}
