package com.careloop.app.ui.screens.medications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
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
import com.careloop.app.data.model.Criticality
import com.careloop.app.data.model.Medication
import com.careloop.app.di.AppContainer
import com.careloop.app.ui.components.CareCard
import com.careloop.app.ui.components.CareEmptyState
import com.careloop.app.ui.components.CarePrimaryButton
import com.careloop.app.ui.components.CareTipCallout
import com.careloop.app.ui.components.StatusPill
import com.careloop.app.ui.components.initialSeed
import com.careloop.app.ui.theme.CareColors
import com.careloop.app.ui.theme.CareDimens
import com.careloop.app.ui.theme.CareLoopTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Margaret's medication list.
 *
 * ## What this screen is trying to do
 *
 * Three things, in order of how often they matter:
 * 1. Tell her what she takes and why, in plain language, so the list itself is reassuring
 *    rather than clinical.
 * 2. Surface the proactive refill card *above* the list when something needs starting —
 *    this is the single best demonstration of Cara noticing something before it becomes a
 *    problem, so it earns the top slot rather than being buried in a detail screen.
 * 3. Make warfarin's criticality legible without it reading as a red alert. The whole
 *    product thesis is that Cara reasons about *seriousness*, not that she panics — the
 *    copy and colour here have to carry that distinction on their own, because a screen
 *    that treats every medication the same defeats the point, and one that treats the
 *    critical one as an emergency terrifies someone for no reason.
 *
 * [onMedicationClick] opens [com.careloop.app.ui.screens.medications.MedicationDetailScreen] —
 * wired in [com.careloop.app.ui.navigation.CareLoopNavigation].
 */
@Composable
fun MedicationsScreen(
    onMedicationClick: (String) -> Unit = {},
    onAddMedication: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // MockData.medications as the demo-mode seed means the list is never empty on first
    // frame in a demo build — there is no flash of an empty state while the (instant,
    // in-memory) flow catches up. A real signed-in account seeds empty instead: see
    // initialSeed's doc for why that split matters.
    val medications by AppContainer.repository.observeMedications()
        .collectAsStateWithLifecycle(
            initialValue = initialSeed(empty = emptyList(), demo = MockData.medications),
        )

    val medicationsNeedingRefill = remember(medications) {
        medications.filter { it.needsRefillSoon }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            horizontal = CareDimens.ScreenPadding,
            vertical = CareDimens.SpaceLg,
        ),
        verticalArrangement = Arrangement.spacedBy(CareDimens.SpaceLg),
    ) {
        item(key = "header") {
            Column {
                Text("Medications", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(CareDimens.SpaceSm))
                Text(
                    "What you take, and why Cara has you on each one.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (medicationsNeedingRefill.isNotEmpty()) {
            item(key = "refill-card") {
                RefillPlanningCard(medications = medicationsNeedingRefill)
            }
        }

        if (medications.isEmpty()) {
            item(key = "empty") {
                CareEmptyState(
                    title = "No medications added yet",
                    whatHappensNext = "Add your first one below, and Cara will start " +
                        "asking about it on your calls.",
                )
            }
        }

        items(medications, key = { it.id }) { medication ->
            MedicationCard(
                medication = medication,
                onClick = { onMedicationClick(medication.id) },
            )
        }

        item(key = "add-medication") {
            Column {
                Spacer(Modifier.height(CareDimens.SpaceSm))
                CarePrimaryButton(
                    text = "Add a medication",
                    icon = Icons.Rounded.Add,
                    onClick = onAddMedication,
                )
            }
        }
    }
}

/**
 * The proactive refill card.
 *
 * Deliberately styled in Cara's own gold rather than [CareColors.Concern] or
 * [CareColors.Urgent] — those semantic colours exist for genuine attention-needed states,
 * and warfarin having nine days of supply left is not one. This is Cara being organised on
 * Margaret's behalf, not a warning. The copy leads with *why now* ("repeat prescriptions
 * take a few days") rather than a bare countdown, because a number alone reads as a
 * deadline and a reason reads as help.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RefillPlanningCard(
    medications: List<Medication>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CareDimens.CardRadius),
        // A soft wash of Cara's gold, not a full-strength fill — this is a nudge, not a banner.
        colors = CardDefaults.cardColors(containerColor = CareColors.Yellow.copy(alpha = 0.28f)),
        elevation = CardDefaults.cardElevation(defaultElevation = CareDimens.CardElevation),
    ) {
        Column(Modifier.padding(CareDimens.SpaceLg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.LocalPharmacy,
                    contentDescription = null,
                    tint = CareColors.GoldDeep,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(CareDimens.SpaceSm))
                Text(
                    "Cara is planning ahead",
                    style = MaterialTheme.typography.titleMedium,
                    color = CareColors.GoldDeep,
                )
            }
            Spacer(Modifier.height(CareDimens.SpaceMd))
            Text(
                "Repeat prescriptions usually take a few days to arrive, so Cara likes to " +
                    "start the request early rather than wait until you're nearly out.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(CareDimens.SpaceMd))
            medications.forEach { medication ->
                // FlowRow, not Row: "Ferrous sulfate" plus "9 days left" side by side can
                // outgrow the card width at 200% font scale, and Row does not wrap onto a
                // second line on its own — it lets the second Text run off the edge instead.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CareDimens.SpaceSm),
                ) {
                    Text(
                        medication.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "· ${medication.daysOfSupplyRemaining} days left",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CareColors.GoldDeep,
                    )
                }
            }
        }
    }
}

/**
 * One medication. The whole card is tappable — [CareDimens.TouchTarget] is a floor, and a
 * card with a name, purpose, schedule, and a status pill is comfortably taller than that on
 * its own, so no extra sizing work is needed to clear it.
 */
@Composable
private fun MedicationCard(
    medication: Medication,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val criticalityInfo = criticalityPresentation(medication.criticality)

    CareCard(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(medication.name, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(CareDimens.SpaceXs))
                Text(
                    medication.dose,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Decorative disclosure affordance only — the whole card is the tap target and
            // its content (name, dose, purpose) is what a screen reader should announce, so
            // this icon must not duplicate that as its own contentDescription.
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.height(CareDimens.SpaceMd))
        Text(medication.purpose, style = MaterialTheme.typography.bodyLarge)

        Spacer(Modifier.height(CareDimens.SpaceMd))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(CareDimens.SpaceSm))
            Text(
                scheduleText(medication.schedule),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(CareDimens.SpaceSm))
        Text(
            "${medication.daysOfSupplyRemaining} days of supply left",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(CareDimens.SpaceMd))
        StatusPill(
            text = criticalityInfo.text,
            icon = criticalityInfo.icon,
            contentColor = criticalityInfo.contentColor,
            containerColor = criticalityInfo.containerColor,
        )

        // Food guidance — surfaced as a tip from Cara, not a warning. The copy in MockData
        // already explains the mechanism ("calcium and iron compete...") rather than just
        // saying "avoid", so this block only needs to present it clearly, not dramatise it.
        // CareTipCallout, not a bespoke block: MedicationDetailScreen shows the exact same
        // guidance and must render it identically, or it reads as two different facts.
        val foodGuidance = medication.foodGuidance
        if (foodGuidance != null) {
            Spacer(Modifier.height(CareDimens.SpaceMd))
            CareTipCallout(
                icon = Icons.Outlined.Restaurant,
                label = "A tip from Cara",
                text = foodGuidance,
            )
        }
    }
}

/**
 * How each criticality level should read to Margaret — care and attention, not risk.
 *
 * Internal, not private: [com.careloop.app.ui.screens.medications.MedicationDetailScreen]
 * needs the exact same mapping so a medication's criticality reads identically on the list
 * and the detail screen.
 */
internal data class CriticalityPresentation(
    val text: String,
    val icon: ImageVector,
    val contentColor: Color,
    val containerColor: Color,
)

/**
 * [Criticality.CRITICAL] is the one case this screen was built around: warfarin must read
 * as "Cara is paying close attention" rather than "danger". Reusing [CareColors.Urgent] or
 * [CareColors.Concern] here would say the opposite of what we mean, so critical medications
 * get Cara's own gold instead of a semantic warning colour.
 */
internal fun criticalityPresentation(criticality: Criticality): CriticalityPresentation = when (criticality) {
    Criticality.CRITICAL -> CriticalityPresentation(
        text = "Cara watches this one closely",
        icon = Icons.Outlined.Info,
        contentColor = CareColors.GoldDeep,
        containerColor = CareColors.Yellow.copy(alpha = 0.30f),
    )
    Criticality.HIGH -> CriticalityPresentation(
        text = "Cara keeps this on her list",
        icon = Icons.Outlined.Info,
        contentColor = CareColors.Navy,
        containerColor = CareColors.Cloud,
    )
    Criticality.MEDIUM -> CriticalityPresentation(
        text = "Part of your daily routine",
        icon = Icons.Rounded.Check,
        contentColor = CareColors.Slate,
        containerColor = CareColors.Cloud,
    )
    Criticality.LOW -> CriticalityPresentation(
        text = "Routine, nothing to watch for",
        icon = Icons.Rounded.Check,
        contentColor = CareColors.Slate,
        containerColor = CareColors.Cloud,
    )
}

internal val scheduleTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

/**
 * "Taken at 9:00 AM" or "Taken at 9:00 AM and 7:00 PM" — never a bare list of times.
 * Internal so [com.careloop.app.ui.screens.medications.MedicationDetailScreen] reads a
 * medication's schedule in exactly the same words as this list does.
 */
internal fun scheduleText(times: List<LocalTime>): String {
    val formatted = times.sorted().map { it.format(scheduleTimeFormatter) }
    val joined = when (formatted.size) {
        0 -> return "No scheduled time set"
        1 -> formatted[0]
        else -> formatted.dropLast(1).joinToString(", ") + " and " + formatted.last()
    }
    return "Taken at $joined"
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, name = "Medications, light")
@Composable
private fun MedicationsScreenPreview() {
    CareLoopTheme(darkTheme = false) {
        MedicationsScreen()
    }
}

@Preview(showBackground = true, name = "Medications, dark")
@Composable
private fun MedicationsScreenDarkPreview() {
    CareLoopTheme(darkTheme = true) {
        MedicationsScreen()
    }
}

@Preview(showBackground = true, name = "Single medication card")
@Composable
private fun MedicationCardPreview() {
    CareLoopTheme(darkTheme = false) {
        Column(modifier = Modifier.padding(CareDimens.SpaceLg)) {
            MedicationCard(medication = MockData.warfarin, onClick = {})
        }
    }
}

/** Confirms the refill card's FlowRow and every card's text actually survive 200% scale. */
@PreviewFontScale
@Composable
private fun MedicationsScreenFontScalePreview() {
    CareLoopTheme(darkTheme = false) {
        MedicationsScreen()
    }
}
